# Bert-VITS2-MNN

> ✨ [Bert-VITS2](https://github.com/fishaudio/Bert-VITS2) Android 版, 推理框架基于 [alibaba-MNN](https://github.com/alibaba/MNN).

---

## 🧠 简介

本工程提供了一个示例，实现了离线推理版本的 Bert-VITS2 （2.3版本），目前仅适配了中文：

- 🧠 **蒸馏版中文 BERT 模型** ：中文 Bert 模型使用了一个自制的蒸馏版本，基于 [Wikipedia 中文](https://huggingface.co/datasets/pleisto/wikipedia-cn-20230720-filtered)以及 [SkyPile 中文数据集](https://huggingface.co/datasets/Skywork/SkyPile-150B)，共计约 1000W 条文本进行模型蒸馏，将体积缩减至 30M。（也不知道蒸的咋样反正最后看曲线是收敛了 -.-)
- 🏗 **MNN** ：基于 MNN 推理框架实现 BV2 的整个推理流程，推理参考自其 onnx 推理代码。(pth 直接转不成功，你没资格啊，你没资格.jpg)
- 🧹 **cppjieba** and **cpptokenizer** ：用来平替 Python 端的 jieba 分词以及 huggingface 的 tokenizer。一些 BV2 独有的文本预处理步骤使用 Kotlin 进行平替实现。(此过程 GPT 老祖帮了许多)

整个过程在 Android 端全程 **离线推理** 无需任何联网服务.

---

## 🔬 大体流程

```
Input Text
   ↓
Tokenization + G2P (cppjieba + tokenizer + kotlin code)
   ↓
BERT embedding (distilled Chinese model)
   ↓
Encoder + Emb + DP/SDP + Flow + Decoder (BV2 infer by MNN)
   ↓
Waveform output (.wav)
```

---

## 🎵 示例音频

此处提供一些中文音频示例，基于部分明日方舟语音集以及[原神语音集](https://www.bilibili.com/opus/804258696892776484)进行训练:

| Text               | Character | Audio                                                                                      |
|--------------------|-----------|--------------------------------------------------------------------------------------------|
| 博士，当初在龙门，我不该放你走的。  | 陈         | 🔊 [Play](https://github.com/user-attachments/assets/a6fc4022-e473-41e3-89da-0f5c9741a4c4) |
| 旅行者，好久不见。          | 珐露珊       | 🔊 [Play](https://github.com/user-attachments/assets/60a96546-1e18-43b8-9a6a-3c9bfd5eca42) |
| 工作还没有做完，又要开始搬砖了。   | 甘雨        | 🔊 [Play](https://github.com/user-attachments/assets/7482e892-630f-47ee-829f-336ceb9525c4)                                                   |



---

## ⚡ 本地编译指南

### Clone with submodules

```bash
GIT_LFS_SKIP_SMUDGE=1 git clone --recurse-submodules git@github.com:Voine/Bert-VITS2-MNN.git

# for windows powershell
$env:GIT_LFS_SKIP_SMUDGE=1; git clone --recurse-submodules git@github.com:Voine/Bert-VITS2-MNN.git

cd Bert-VITS2-MNN
```

If already cloned:

```bash
git submodule update --init --recursive
```

### Build for Android

> 📦 建议使用 Android Studio 进行工程编译，用 IDE 打开根目录即可

```bash
# From project root
./gradlew assembleRelease
```

---

## 🛁 Git LFS 

本工程的一些文件如 `.mnn` ，使用 lfs 进行存储，需要按照如下方式拉代码：

```bash
git lfs install
git lfs pull
```

To track files (if contributing):

```bash
git lfs track "*.mnn"
```

---

## 🛠️ Submodule 依赖

| Library      | Path                         |
|--------------|------------------------------|
| [MNN](https://github.com/alibaba/MNN)        | `third_party/MNN`            |
| [cppjieba](https://github.com/yanyiwu/cppjieba)     | `third_party/cppjieba`       |
| [tokenizer-cpp](https://github.com/mlc-ai/tokenizers-cpp) | `third_party/tokenizers-cpp` |


---

## 💡 关于 - 模型蒸馏 -

中文模型基于 [chinese-roberta-wwm-ext-large](https://huggingface.co/hfl/chinese-roberta-wwm-ext-large) 进行蒸馏，为适配移动端，大幅缩减了体积。原版直接转换能有 1.2G ...

蒸馏代码详见 [`distill/README.md`](distill/README.md).

---


## 💡 关于 - 自制模型替换 -

1. 如果你需要替换自己的模型尝试验证，首先需要参考 [BertVITS2](https://github.com/fishaudio/Bert-VITS2) 内的说明进行训练得到桌面端模型，目前仅支持 2.3 版本，本工程基于的 BV2 代码 commit 为 13424595，如需自制模型，建议 BV2 代码版本保持一致。
2. 将你的 pth 模型转换成 onnx, onnx 导出脚本在  [这里](https://github.com/fishaudio/Bert-VITS2/blob/master/export_onnx.py)
3. 使用 [MNN Convert](https://mnn-docs.readthedocs.io/en/latest/tools/convert.html) 将所有模块的 onnx 模型转成 mnn, 转换命令参考：

```bash
./MNNConvert --modelFile your_path_to_onnx.onnx --MNNModel your_path_to_mnn.mnn --framework ONNX --bizCode MNN --weightQuantBits 8 --weightQuantAsymmetric
```

4. 放到 assets/bv2_model 内，如果你的模型名字有变化，则需要修改 VoiceViewModel.kt 内关于模型路径加载的部分。（硬编码字符串一时爽，一直硬编码一直爽）

---

## 💡 关于 - third_party -

目前在 third_party 内的 cppjieba、tokenizer-cpp 以及 MNN 仅是为了提供头文件，若需要自行编译 tokenizer-cpp 并替换产物 [libtokenizers_c.a](cpptokenizer/src/main/jniStaticLibs/arm64-v8a/libtokenizers_c.a) [libtokenizers_cpp.a](cpptokenizer/src/main/jniStaticLibs/arm64-v8a/libtokenizers_cpp.a)，需修改 [huggingface_tokenizer.cc](third_party/tokenizers-cpp/src/huggingface_tokenizer.cc) 内的 add_special_tokens 默认为 true

---

## 📋 工程大体结构

```
├── app/
├──── src/main/                 
│           ├── assets               # mnn bert model, cppjieba dic, mnn bv2model
│           ├── java/preprocess      # Text preprocess code
├── bertvits2                        # Bert-VITS2 infer code
├── cppjieba                         # cppjieba interface 
├── cpptokenizer                     # cpptokenizer interface
├── third_party                      # provide hpp

```

---

## 🙌 鸣谢

本工程基于以下前辈们的贡献做了一些微不足道的搬砖工作，也希望能为后续在端智能推理捣鼓的小伙伴提供一些参考。

- [VITS](https://github.com/jaywalnut310/vits)
- [BertVITS2](https://github.com/fishaudio/Bert-VITS2)
- [MNN](https://github.com/alibaba/MNN)
- [cppjieba](https://github.com/yanyiwu/cppjieba)
- [tokenizer-cpp](https://github.com/mlc-ai/tokenizers-cpp)

---

## 🛠️ 后续工作

- 看一下日文版和英文版怎么搞
- 迁移到[移动版老婆聊天器](https://github.com/Voine/ChatWaifu_Mobile)中

---

## ✨  简介视频

- [Video](https://www.bilibili.com/video/BV1f5Ldz5Enz)

---


## 免责声明
### 本项目仅供学习交流使用，禁止用于商业用途，作者纯为爱发电搞着玩的。

### 严禁将此项目用于一切违反《中华人民共和国宪法》，《中华人民共和国刑法》，《中华人民共和国治安管理处罚法》和《中华人民共和国民法典》之用途。
### 严禁用于任何政治相关用途。

---
