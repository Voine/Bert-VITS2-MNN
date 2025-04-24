from transformers import AutoTokenizer
from combine_bert_model import DistilledBertWithProjection
import torch

def export_onnx():
    model = DistilledBertWithProjection.from_pretrained("/Volumes/FanxiangS790E/merged_model")
    tokenizer = AutoTokenizer.from_pretrained("/Volumes/FanxiangS790E/merged_model")
    model.eval()

    dummy_text = "你好，这是一个测试句子"
    inputs = tokenizer(dummy_text, return_tensors="pt")

    dummy_input_ids = inputs["input_ids"]
    dummy_attention_mask = inputs["attention_mask"]
    dummy_token_type_ids = inputs["token_type_ids"]

    torch.onnx.export(
        model,
        (dummy_input_ids, dummy_attention_mask, dummy_token_type_ids),
        "chinese-roberta-wwm-ext-large-distilled.onnx",
        input_names=["input_ids", "attention_mask"],
        output_names=["hidden_state"],
        dynamic_axes={
            "input_ids": {0: "batch_size", 1: "sequence_length"},
            "attention_mask": {0: "batch_size", 1: "sequence_length"},
            "hidden_state": {0: "batch_size", 1: "sequence_length"}
        },
        do_constant_folding=True,
        opset_version=16
    )

def run_onnx():
    import onnxruntime
    import numpy as np
    tokenizer = AutoTokenizer.from_pretrained("/Volumes/FanxiangS790E/merged_model")

    ort_session = onnxruntime.InferenceSession("chinese-roberta-wwm-ext-large-distilled.onnx")
    batch_size = 1
    sequence_length = 10  # 根据您的模型和任务调整
    input_ids = np.random.randint(0, 100, size=(batch_size, sequence_length)).astype(np.int64)
    attention_mask = np.ones((batch_size, sequence_length), dtype=np.int64)
    token_type_ids = np.zeros((batch_size, sequence_length), dtype=np.int64)
    inputs = tokenizer("你好，世界", return_tensors="pt")
    onnx_input_ids = inputs["input_ids"]
    onnx_attention_mask = inputs["attention_mask"]
    onnx_token_type_ids = inputs["token_type_ids"].numpy()
    ort_inputs = {
        "input_ids": onnx_input_ids.numpy(),
        "attention_mask": onnx_attention_mask.numpy()
    }
    ort_outs = ort_session.run(None, ort_inputs)
    print("ONNX Output shape:", ort_outs[0].shape)

if __name__ == "__main__":
    export_onnx()