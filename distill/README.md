- [bert_distill_onnx_export.py](bert_distill_onnx_export.py)
导出 onnx 用

- [BertModelDistill.py](BertModelDistill.py)
蒸馏主流程

- [combine_bert_model.py](combine_bert_model.py)
由于一开始保存的时候没有把线性层保存到一起，所以额外有一个 Merge

- [preprocess_text.py](preprocess_text.py)
文本预处理

大体上基本都是 GPT 教的 -.-