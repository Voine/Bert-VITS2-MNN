# run_distill.py

import os
import random

import torch
from datasets import load_dataset, Dataset
from transformers import AutoTokenizer, AutoModel, AutoConfig
from torch.utils.data import DataLoader
from torch import nn
from tqdm import tqdm
from torch.utils.tensorboard import SummaryWriter
import glob
from preprocess_text import load_and_preprocess_dataset

bert_model = "E:\\PythonProject\\PythonProject\\chinese-roberta-wwm-ext-large"
device = torch.device("cuda")

log_dir = "./runs/tinybert-distill"
os.makedirs(log_dir, exist_ok=True)
writer = SummaryWriter(log_dir=log_dir)

full_dataset = load_and_preprocess_dataset()
full_dataset.set_format(type='torch', columns=['input_ids', 'attention_mask'])

# ==== Step 5: Load Teacher & Student ====
print("Loading models...")

teacher = AutoModel.from_pretrained(bert_model)
teacher.eval()
tokenizer = AutoTokenizer.from_pretrained(bert_model)

# Define a lightweight student config
student_config = AutoConfig.from_pretrained(bert_model)
student_config.num_hidden_layers = 4
student_config.hidden_size = 384
student_config.intermediate_size = 1536
student_config.num_attention_heads = 6

from transformers import BertModel

student = BertModel(student_config).to(device)

# Optional: projection layer to match dimensions
project = nn.Linear(student_config.hidden_size, teacher.config.hidden_size)

# ==== Step 6: Resume Checkpoint if exists ====
ckpt_dir = "./checkpoints"
os.makedirs(ckpt_dir, exist_ok=True)
latest_ckpt = sorted(glob.glob(os.path.join(ckpt_dir, "ckpt_step_*.pt")))
start_step = 0
optimizer = torch.optim.AdamW(list(student.parameters()) + list(project.parameters()), lr=5e-5)
from torch.optim.lr_scheduler import StepLR
scheduler = StepLR(optimizer, step_size=1000, gamma=0.9)

if latest_ckpt:
    print(f"Loading checkpoint: {latest_ckpt[-1]}")
    checkpoint = torch.load(latest_ckpt[-1])
    student.load_state_dict(checkpoint['student'])
    project.load_state_dict(checkpoint['project'])
    if 'optimizer' in checkpoint:
        optimizer.load_state_dict(checkpoint['optimizer'])
    if 'scheduler' in checkpoint:
        scheduler.load_state_dict(checkpoint['scheduler'])
    start_step = checkpoint['step'] + 1

# ==== Step 7: Training ====
teacher.to(device)
student.to(device)
project.to(device)
loss_fn = nn.MSELoss()


# TensorBoard setup
log_dir = "./runs/tinybert-distill"
os.makedirs(log_dir, exist_ok=True)
writer = SummaryWriter(log_dir=log_dir)

print("Starting distillation training...")
save_every = 1000  # steps
step_count = start_step
MAX_CKPT = 5
MAX_TRAIN_SAMPLES_PER_EPOCH = 3_000_000

for epoch in range(1000):
    student.train()
    total_loss = 0

    # 每轮随机采样
    if MAX_TRAIN_SAMPLES_PER_EPOCH < len(full_dataset):
        indices = list(range(len(full_dataset)))
        random.shuffle(indices)
        sampled_dataset = full_dataset.select(indices[:MAX_TRAIN_SAMPLES_PER_EPOCH])
    else:
        sampled_dataset = full_dataset

    dataloader = DataLoader(sampled_dataset, batch_size=64, shuffle=True)

    for batch in tqdm(dataloader):
        input_ids = batch['input_ids'].to(device)
        attention_mask = batch['attention_mask'].to(device)

        with torch.no_grad():
            teacher_outputs = teacher(input_ids=input_ids, attention_mask=attention_mask, output_hidden_states=True)
            target = teacher_outputs.hidden_states[-3]

        student_outputs = student(input_ids=input_ids, attention_mask=attention_mask, output_hidden_states=True)
        pred = project(student_outputs.last_hidden_state)

        loss = loss_fn(pred, target)

        loss.backward()
        optimizer.step()
        scheduler.step()
        optimizer.zero_grad()

        total_loss += loss.item()
        global_step = step_count
        writer.add_scalar("train/loss", loss.item(), global_step)
        writer.add_scalar("train/lr", scheduler.get_last_lr()[0], global_step)

        # Save checkpoint every N steps
        # 初始化记录最优 ckpt
        if epoch == 0 and step_count == start_step:
            best_ckpts = []
            max_ckpt = MAX_CKPT

        if step_count % save_every == 0 and step_count > 0:
            ckpt_path = os.path.join(ckpt_dir, f"ckpt_step_{step_count}_loss_{loss.item():.4f}.pt")
            torch.save({
                'step': step_count,
                'loss': loss.item(),
                'student': student.state_dict(),
                'project': project.state_dict(),
                'optimizer': optimizer.state_dict(),
                'scheduler': scheduler.state_dict()
            }, ckpt_path)
            print(f"✅ Checkpoint saved: {ckpt_path}")

            # 保存 transformers 格式模型
            save_path = os.path.join("./tinybert-distilled", f"step_{step_count}_loss_{loss.item():.4f}")
            os.makedirs(save_path, exist_ok=True)
            student.save_pretrained(save_path)
            tokenizer.save_pretrained(save_path)

            # 更新 top-3 loss 最低的 checkpoint 列表
            best_ckpts.append((loss.item(), step_count, ckpt_path))
            best_ckpts = sorted(best_ckpts, key=lambda x: x[0])[:max_ckpt]

            # 清理其他 ckpt
            all_ckpts = glob.glob(os.path.join(ckpt_dir, "ckpt_step_*.pt"))
            preserved = set(ck[2] for ck in best_ckpts)
            for ckpt in all_ckpts:
                if ckpt not in preserved:
                    os.remove(ckpt)
                    print(f"🧹 Removed old ckpt: {ckpt}")

            # 清理多余的 save_pretrained 子目录
            all_model_dirs = glob.glob(os.path.join("./tinybert-distilled", "step_*"))
            preserved_model_dirs = set(
                os.path.join("./tinybert-distilled", f"step_{step}_loss_{loss:.4f}")
                for loss, step, _ in best_ckpts
            )

            for model_dir in all_model_dirs:
                if model_dir not in preserved_model_dirs:
                    import shutil
                    shutil.rmtree(model_dir, ignore_errors=True)
                    print(f"🧹 Removed save_pretrained model dir: {model_dir}")

            # 保存当前最佳模型副本
            if best_ckpts and step_count == best_ckpts[0][1]:
                best_model_dir = "./tinybert-distilled/best_model"
                os.makedirs(best_model_dir, exist_ok=True)
                student.save_pretrained(best_model_dir)
                tokenizer.save_pretrained(best_model_dir)
                print(f"🏆 Best model updated at step {step_count} with loss {loss.item():.4f}")

        step_count += 1

    epoch_loss = total_loss / len(dataloader)
    print(f"Epoch {epoch + 1}, Loss: {epoch_loss:.4f}")
    writer.add_scalar("train/epoch_loss", epoch_loss, epoch)

writer.close()


def remove_optimizer(step_count):
    # ==== Optional: remove optimizer and scheduler from latest checkpoint ====
    final_ckpt_path = os.path.join(ckpt_dir, f"ckpt_step_{step_count - 1}.pt")
    if os.path.exists(final_ckpt_path):
        ckpt = torch.load(final_ckpt_path)
        for k in ['optimizer', 'scheduler']:
            if k in ckpt:
                del ckpt[k]
        torch.save(ckpt, final_ckpt_path)
        print(f"Optimizer and scheduler removed from final checkpoint: {final_ckpt_path}")

