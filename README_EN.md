# Bert-VITS2-MNN

> ✨ Android port of [Bert-VITS2](https://github.com/fishaudio/Bert-VITS2), powered by [alibaba-MNN](https://github.com/alibaba/MNN) for on-device inference.

🌐 **Language**: [中文](README.md) · [English](README_EN.md)

---

## 🧠 Overview

This project provides a sample implementation of Bert-VITS2 (v2.3) running **fully offline on Android**. It now supports **TTS in Chinese / Japanese / English**, and some Chinese speakers also accept **mixed Chinese + English** input:

- 🧠 **Distilled multilingual BERT** — The Chinese BERT is a self-distilled variant trained on [Wikipedia-zh](https://huggingface.co/datasets/pleisto/wikipedia-cn-20230720-filtered) and [SkyPile](https://huggingface.co/datasets/Skywork/SkyPile-150B) (~10M sentences), shrunk down to ~30MB. The Japanese / English BERTs are compressed in the same fashion so they all fit on-device.
- 🏗 **MNN** — The whole BV2 pipeline is rebuilt on top of MNN, referencing the official ONNX inference code. (Direct `.pth` → MNN doesn't work, so ONNX → MNN it is.)
- 🧹 **cppjieba / cpptokenizer / openjtalk** — Replace the Python-side jieba, HuggingFace tokenizers and open-jtalk respectively. BV2-specific text preprocessing is reimplemented in Kotlin.
- 📦 **AAR distribution** — All inference code has been extracted into `bertvits2-infer-wrapper`, which can be published as an AAR (with its native dependencies) so other apps can integrate with one coordinate. See [PUBLISHING.md](PUBLISHING.md).

The entire pipeline runs **fully offline** on the device — no network required.

---

## 🔬 Pipeline

```
Input Text (ZH / JP / EN / ZH+EN Mix)
   ↓
Tokenization + G2P (cppjieba + cpptokenizer + openjtalk + kotlin code)
   ↓
BERT embedding (distilled ZH / JP / EN model)
   ↓
Encoder + Emb + DP/SDP + Flow + Decoder (BV2 infer by MNN)
   ↓
Waveform output (.wav)
```

---

## 🎵 Sample Audio

The speakers bundled in the demo app are listed below. The full list lives in [`VoiceViewModel.kt`](app/src/main/java/com/example/bertvits2mnn/VoiceViewModel.kt). Chinese speakers are trained on public voice sets from Arknights, Wuthering Waves, Bilibili New Year Gala videos and [Genshin Impact](https://www.bilibili.com/opus/804258696892776484); Japanese / English speakers come from the public voice sets of their respective games. Some English samples have minor distortion, and the Japanese samples sound a bit flat — most likely because the fine-tuning data mixed in some messy stuff and the model learned the wrong things (fog). Feel free to train your own characters and you should get noticeably better results~

| Character | Language | Sample Rate | Text                                                          | Audio |
|-----|----------|-------------|---------------------------------------------------------------|-------|
| Ch'en | Chinese     | 44100 Hz    | 博士，当初在龙门，我不该放你走的。                              | 🔊 [Play](https://github.com/user-attachments/assets/a6fc4022-e473-41e3-89da-0f5c9741a4c4) |
| Faruzan | Chinese     | 44100 Hz    | 旅行者，好久不见。                                              | 🔊 [Play](https://github.com/user-attachments/assets/60a96546-1e18-43b8-9a6a-3c9bfd5eca42) |
| Ganyu | Chinese     | 44100 Hz    | 工作还没有做完，又要开始搬砖了。                                | 🔊 [Play](https://github.com/user-attachments/assets/7482e892-630f-47ee-829f-336ceb9525c4) |
| 22  | ZH + EN mix  | 22050 Hz    | RTX 5090 将于明年发布，敬请期待！                               | 🔊 [Play](https://github.com/user-attachments/assets/fd6cc25c-79ca-42fc-a60a-352d8f99e437) |
| APPLe | English     | 44100 Hz    | Greetings, madam. I am here. Clouds help predict the weather. | 🔊 [Play](https://github.com/user-attachments/assets/7d513650-e4ab-4449-b2be-d8a49cb3c7fc) |
| Sonetto | English     | 44100 Hz    | Timekeeper, at your service. The stars shine bright tonight.  | 🔊 [Play](https://github.com/user-attachments/assets/fe55763b-5b84-4e19-9960-4e7d2047cb54) |
| Vertin | English     | 44100 Hz    | The storm is coming. We must prepare ourselves.               | 🔊 [Play](https://github.com/user-attachments/assets/b0238546-80b8-422e-ab9e-ed4940cba2e1) |
| Yae Miko | Japanese     | 44100 Hz    | たびびと、きょうはどんなおもしろいほんをもってきてくれたの？もしないようがつまらなかったら、わたし、へんしゅうぶに『しげき』にいこうかな～？               | 🔊 [Play](https://github.com/user-attachments/assets/5032b809-f963-415b-8a93-d97644f725cc) |
| Yoimiya  | Japanese     | 44100 Hz    | こんにちは、皆さん。今日は素晴らしい一日ですね。                | 🔊 [Play](https://github.com/user-attachments/assets/f2fb34e2-fb2d-4e8a-8c95-4555f0adf9aa) |
| Tsubaki   | Japanese     | 44100 Hz    | あなたといると、なぜか落ち着くの。              | 🔊 [Play](https://github.com/user-attachments/assets/2e2a6951-a435-4a0c-a9a3-23241c42049c) |
| Yajuu Senpai | Japanese     | 44100 Hz    | にじゅうよんさいはがくせいです                                  | 🔊 [Play](https://github.com/user-attachments/assets/ea56d3e1-4de4-43c9-ba18-ec48d9e3504d) |

---

## 🎯 22 kHz Base Model

A new 22 kHz base model lives under [`base_model_22k/`](base_model_22k).

> After plenty of testing and trade-offs, 22 kHz turned out to strike the best balance between performance and quality. The base model is trained on character voices from four games — Genshin Impact, Wuthering Waves, Zenless Zone Zero and Honkai: Star Rail — and is provided **for study / non-commercial use only**. The decoder hyper-parameters have been adjusted accordingly; the upstream training code contains some hard-coded values that may need small tweaks, which are not covered here.

If you want to fine-tune from this base model, please pair it with [BertVITS2](https://github.com/fishaudio/Bert-VITS2) v2.3.

---

## 📦 Integrating as AAR

The `bertvits2-infer-wrapper` module (along with all its native dependencies) is `maven-publish` ready. Producing AARs for downstream apps is a single command:

```bash
./gradlew publishAars
```

See [PUBLISHING.md](PUBLISHING.md) for the full guide — custom coordinates, local repository layout, downstream Gradle wiring, etc.

---

## ⚡ Build Guide

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

> 📦 Android Studio is recommended — just open the root directory.

```bash
# From project root
./gradlew assembleRelease
```

---

## 🛁 Git LFS

Some assets (e.g. `.mnn`) are stored via Git LFS:

```bash
git lfs install
git lfs pull
```

To track files (if contributing):

```bash
git lfs track "*.mnn"
```

---

## 🛠️ Submodules

| Library      | Path                         |
|--------------|------------------------------|
| [MNN](https://github.com/alibaba/MNN)                       | `third_party/MNN`            |
| [cppjieba](https://github.com/yanyiwu/cppjieba)             | `third_party/cppjieba`       |
| [tokenizer-cpp](https://github.com/mlc-ai/tokenizers-cpp)   | `third_party/tokenizers-cpp` |

---

## 💡 Notes — BERT Distillation

The Chinese BERT is distilled from [chinese-roberta-wwm-ext-large](https://huggingface.co/hfl/chinese-roberta-wwm-ext-large) and aggressively compressed for mobile (the naïve port is ~1.2 GB...). The JP / EN BERTs go through the same distill + quantize flow.

See [`distill/README.md`](distill/README.md) for the distillation scripts.

---

## 💡 Notes — Replacing With Your Own Model

1. Train a model following the [BertVITS2](https://github.com/fishaudio/Bert-VITS2) instructions. Only v2.3 is supported; this project is built against commit `13424595`, so please stick to that version. If you target the 22 kHz sample rate, use the `config.json` / `G_0.pth` shipped in [`base_model_22k/`](base_model_22k).
2. Convert your `.pth` to ONNX with the official [`export_onnx.py`](https://github.com/fishaudio/Bert-VITS2/blob/master/export_onnx.py).
3. Convert each ONNX module to MNN via [MNN Convert](https://mnn-docs.readthedocs.io/en/latest/tools/convert.html):

```bash
./MNNConvert --modelFile your_path_to_onnx.onnx --MNNModel your_path_to_mnn.mnn --framework ONNX --bizCode MNN --weightQuantBits 8 --weightQuantAsymmetric
```

4. Drop the `.mnn` files under `bertvits2-jni/src/main/assets/bv2_model`, then update the loading code in `BertVITS2SimpleInferImpl.kt` accordingly.

---

## 💡 Notes — third_party

`third_party/{cppjieba, tokenizers-cpp, MNN}` is only there to provide headers. If you want to rebuild tokenizer-cpp and replace the prebuilt [libtokenizers_c.a](cpptokenizer/src/main/jniStaticLibs/arm64-v8a/libtokenizers_c.a) / [libtokenizers_cpp.a](cpptokenizer/src/main/jniStaticLibs/arm64-v8a/libtokenizers_cpp.a), remember to flip `add_special_tokens` to `true` in [huggingface_tokenizer.cc](third_party/tokenizers-cpp/src/huggingface_tokenizer.cc).

---

## 💡 Other Notes

- The BERT model mainly serves as an auxiliary input to the whole system — in many cases dropping it doesn't break inference, the voice just ends up a bit duller / flatter / slightly off. Even after distillation the BERT model can still be sizeable, so when integrating into a real product, feel free to trade it off based on your size budget.
- Since the demo app bundles inference for multiple languages, a lot of components are wired up as lazy-init. As a result the **first** inference is noticeably slower. In a real product you can either hide the lazy-init work behind some other flow (e.g. splash / preload), or optimize the lazy-init path itself.

---

## 📋 Project Layout

```
├── app/                              # Demo app
│   └── src/main/
│       ├── assets                    # mnn bert model, cppjieba dict, mnn bv2 model
│       └── java/                     # UI / ViewModel
├── bertvits2-infer-wrapper/          # Public inference entry (publishable as AAR)
├── bertvits2-jni/                    # Bert-VITS2 inference JNI
├── text-preprocess/                  # ZH / JP / EN / mixed text preprocessing
├── cppjieba/                         # cppjieba interface
├── cpptokenizer/                     # cpptokenizer interface
├── openjtalk/                        # open-jtalk interface (Japanese G2P)
├── base_model_22k/                   # 22 kHz base model
├── distill/                          # BERT distillation scripts
└── third_party/                      # Vendored headers
```

---

## 🙌 Credits

Standing on the shoulders of these giants:

- [VITS](https://github.com/jaywalnut310/vits)
- [BertVITS2](https://github.com/fishaudio/Bert-VITS2)
- [MNN](https://github.com/alibaba/MNN)
- [cppjieba](https://github.com/yanyiwu/cppjieba)
- [tokenizer-cpp](https://github.com/mlc-ai/tokenizers-cpp)
- [open-jtalk](http://open-jtalk.sourceforge.net/)

---

## 🛠️ Roadmap

- Integrate into [ChatWaifu_Mobile](https://github.com/Voine/ChatWaifu_Mobile)

---

## ✨ Intro Video

- [Video](https://www.bilibili.com/video/BV1f5Ldz5Enz)

---

## Disclaimer

### This project is for study and personal experimentation only. Commercial use is **not** permitted.

### It must **not** be used for any purpose that violates the laws or regulations of your jurisdiction, nor for any politically sensitive purpose.

---
