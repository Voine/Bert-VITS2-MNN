# Bert-VITS2-MNN

> ✨ A high-performance on-device TTS system for Chinese, powered by distilled Bert + Bert-VITS2 + MNN.

![banner](assets/banner.png) <!-- 可以替成你的页面图 -->

---

## 🧠 Introduction

This project brings Bert-VITS2 to Android, re-implemented using:

- 🧠 **Distilled Chinese BERT** for lightweight semantic embedding
- 🏗 **MNN** for efficient on-device inference
- 🧹 **cppjieba** and **cpptokenizer** for fast native text preprocessing

All components run **entirely offline** on Android. No server or internet required.

---

## 🔬 Architecture

```
Input Text
   ↓
Tokenization + G2P (cppjieba + tokenizer + kotlin code)
   ↓
BERT embedding (distilled Chinese model)
   ↓
Encoder + DP/SDP + Flow + Decoder (MNN)
   ↓
Waveform output (.wav)
```

---

## 🎵 Demo Audios

Here are some sample results:

| Text               | Character | Audio                                      |
|--------------------|-----------|--------------------------------------------|
| 博士，当初在龙门，我不该放你走的。  | Chen      | 🔊 [Play](./wav_sample/output_chen.wav)    |
| 旅行者，好久不见。          | Faruzan   | 🔊 [Play](./wav_sample/output_faruzan.wav) |
| 工作还没有做完，又要开始搬砖了。   | Ganyu     | 🔊 [Play](./wav_sample/output_ganyu.wav)   |

---

## ⚡ Quick Start

### Clone with submodules

```bash
git clone --recurse-submodules https://github.com/yourname/Bert-VITS2-MNN.git
cd Bert-VITS2-MNN
```

If already cloned:

```bash
git submodule update --init --recursive
```

### Build for Android

> 📦 Requires NDK r25+, CMake 3.22+, Android Studio Arctic Fox+

```bash
# From project root
./gradlew assembleRelease
```

---

## 🛁 Git LFS (for large models)

This repo uses Git LFS for `.mnn` and `.wav` files.

```bash
git lfs install
git lfs pull
```

To track files (if contributing):

```bash
git lfs track "*.mnn"
git lfs track "*.wav"
```

---

## 🛠️ Dependencies (via Submodule)

| Library | Path |
|---------|------|
| MNN     | `third_party/MNN` |
| cppjieba| `third_party/cppjieba` |
| cpptokenizer | `third_party/cpptokenizer` |

To update or verify:

```bash
git submodule update --init --recursive
```

---

## 💡 Model Distillation

We distilled a compact BERT encoder from `chinese-roberta-wwm-ext-large` using hidden state alignment and simplified tokenizer vocab.

Details coming in [`distill/distill.md`](distill/distill.md).

---

## 🌍 Multilingual README

You can switch to the 中文版 [这里](README.zh.md) 。

---

## 📋 Project Layout

```
app/
├── src/main/                 
│           ├── assets               # mnn bert model, cppjieba dic, mnn bv2model
│           ├── java/preprocess      # Text preprocess code
├── bertvits2/                       # Bert-VITS2 infer code
├── cppjieba                         # cppjieba interface 
├── cpptokenizer                     # cpptokenizer interface

```

---

## 🙌 Acknowledgements

This project is built on the shoulders of:

- [VITS](https://github.com/jaywalnut310/vits)
- [BertVITS2](https://github.com/fishaudio/Bert-VITS2)
- [MNN](https://github.com/alibaba/MNN)
- [cppjieba](https://github.com/yanyiwu/cppjieba)

---

Made with ❤️ by [Voine] · License: MIT
