# Bert-VITS2-MNN

> ✨ A high-performance on-device TTS system for Chinese, powered by distilled BERT + VITS2 + MNN.

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
Tokenization + G2P (cppjieba + tokenizer)
   ↓
BERT embedding (distilled Chinese model)
   ↓
Flow + Decoder (MNN)
   ↓
Waveform output (.wav)
```

---

## 🎵 Demo Audios

Here are some sample results:

| Text | Audio |
|------|-------|
| 你好，欢迎使用BertVITS2。| 🔊 [Play](./samples/nihao.wav) |
| 我们的模型在手机上也能运行得非常流畅。| 🔊 [Play](./samples/smooth.wav) |

```html
<!-- Optional inline audio player -->
<audio controls>
  <source src="samples/nihao.wav" type="audio/wav">
</audio>
```

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

| Library | Path | Commit |
|---------|------|--------|
| MNN     | `third_party/MNN` | pinned @ abc1234 |
| cppjieba| `third_party/cppjieba` | pinned @ def5678 |
| cpptokenizer | `third_party/cpptokenizer` | pinned @ ghi9012 |

To update or verify:

```bash
git submodule update --init --recursive
```

---

## 💡 Model Distillation

We distilled a compact BERT encoder from `chinese-roberta-wwm-ext-large` using hidden state alignment and simplified tokenizer vocab.

Details coming in [`model/distill.md`](model/distill.md).

---

## 🌍 Multilingual README

You can switch to the 中文版 [这里](README.zh.md) 。

---

## 📋 Project Layout

```
app/
├── cpp/                 # Native code
│   ├── tokenizer/       # cppjieba + cpptokenizer
│   ├── tts_engine/      # MNN inference, flow, decoder
├── assets/              # Pre-trained .mnn model files
├── samples/             # Demo audio
```

---

## 🙌 Acknowledgements

This project is built on the shoulders of:

- [VITS](https://github.com/jaywalnut310/vits)
- [BertVITS2](https://github.com/fishaudio/Bert-VITS2)
- [MNN](https://github.com/alibaba/MNN)
- [cppjieba](https://github.com/yanyiwu/cppjieba)

---

Made with ❤️ by [yourname] · License: MIT
