from transformers import BertModel, BertConfig, PreTrainedModel, AutoTokenizer, AutoConfig
import torch
import torch.nn as nn
import os
from safetensors.torch import load_file


class DistilledBertWithProjection(PreTrainedModel):
    config_class = BertConfig

    def __init__(self, config, output_dim=1024):
        super().__init__(config)
        self.bert = BertModel(config)
        self.project = nn.Linear(config.hidden_size, output_dim)

    def forward(self, input_ids=None, attention_mask=None, **kwargs):
        outputs = self.bert(input_ids=input_ids, attention_mask=attention_mask)
        return self.project(outputs.last_hidden_state)


def merge_student_and_project(student_safetensor_dir, project_ckpt_path, save_dir, output_dim=1024):
    config = AutoConfig.from_pretrained(student_safetensor_dir)
    model = DistilledBertWithProjection(config, output_dim=output_dim)

    # 加载 safetensors 权重
    safetensor_path = os.path.join(student_safetensor_dir, "model.safetensors")
    state_dict = load_file(safetensor_path)
    model.bert.load_state_dict(state_dict, strict=False)

    # 加载 project 层参数
    project_ckpt = torch.load(project_ckpt_path, map_location="cpu")
    model.project.load_state_dict(project_ckpt["project"])
    print("✅ Loaded student + projection weights")

    # 保存为 HuggingFace 格式
    model.save_pretrained(save_dir, safe_serialization=True)
    tokenizer = AutoTokenizer.from_pretrained(student_safetensor_dir)
    tokenizer.save_pretrained(save_dir)
    print(f"✅ Merged model saved to: {save_dir}")


def test_merged_bert():
    model = DistilledBertWithProjection.from_pretrained("./merged_model")
    tokenizer = AutoTokenizer.from_pretrained("./merged_model")

    inputs = tokenizer("你好", return_tensors="pt")
    with torch.no_grad():
        out = model(**inputs)
    print(out)

if __name__ == "__main__":
    student_safetensor_dir = "./tinybert-distilled/step_126000_loss_0.2762"
    project_ckpt_path = "./checkpoints/ckpt_step_126000_loss_0.2762.pt"
    save_dir = "./merged_model"

    test_merged_bert()
    # merge_student_and_project(student_safetensor_dir, project_ckpt_path, save_dir)
