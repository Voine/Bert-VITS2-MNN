# Bert-VITS2-MNN

> ✨ [Bert-VITS2](https://github.com/fishaudio/Bert-VITS2) Android 版, 推理框架基于 [alibaba-MNN](https://github.com/alibaba/MNN).

🌐 **Language**: [中文](README.md) · [English](README_EN.md)

---

## 🧠 简介

本工程提供了一个示例，实现了离线推理版本的 Bert-VITS2 （2.3版本），**目前已支持中 / 日 / 英三语 TTS 推理**，其中部分中文角色还支持**中英文混合输入**：

- 🧠 **蒸馏版多语 BERT 模型** ：中文 Bert 使用了一个自制的蒸馏版本，基于 [Wikipedia 中文](https://huggingface.co/datasets/pleisto/wikipedia-cn-20230720-filtered)以及 [SkyPile 中文数据集](https://huggingface.co/datasets/Skywork/SkyPile-150B)，共计约 1000W 条文本进行模型蒸馏，将体积缩减至 30M。（也不知道蒸的咋样反正最后看曲线是收敛了 -.-)；日 / 英 BERT 也按类似思路压缩，统一在端上推理。
- 🏗 **MNN** ：基于 MNN 推理框架实现 BV2 的整个推理流程，推理参考自其 onnx 推理代码。(pth 直接转不成功，你没资格啊，你没资格.jpg)
- 🧹 **cppjieba** / **cpptokenizer** / **openjtalk** ：用来平替 Python 端的 jieba 分词、huggingface tokenizer 以及日文的 open-jtalk。一些 BV2 独有的文本预处理步骤使用 Kotlin 进行平替实现。(此过程 GPT/Claude 老祖帮了许多)
- 📦 **AAR 发布** ：推理相关代码已下沉到 `bertvits2-infer-wrapper` 模块，可一键打成 AAR 给其它 App 接入，详见 [PUBLISHING.md](PUBLISHING.md)。

整个过程在 Android 端全程 **离线推理** 无需任何联网服务.

---

## 🔬 大体流程

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

## 🎵 示例音频

下表展示了 demo App 中内置的角色样例，完整角色清单可参考 [`VoiceViewModel.kt`](app/src/main/java/com/example/bertvits2mnn/VoiceViewModel.kt)。中文角色基于部分明日方舟语音集以及[原神语音集](https://www.bilibili.com/opus/804258696892776484)等公开数据训练，日 / 英角色基于各自游戏对应公开语音集训练。

| Character | Language | Sample Rate | Text                                                          | Audio |
|-----------|----------|-------------|---------------------------------------------------------------|-------|
| 陈        | 中文     | 44100 Hz    | 博士，当初在龙门，我不该放你走的。                              | 🔊 [Play](https://github.com/user-attachments/assets/a6fc4022-e473-41e3-89da-0f5c9741a4c4) |
| 珐露珊    | 中文     | 44100 Hz    | 旅行者，好久不见。                                              | 🔊 [Play](https://github.com/user-attachments/assets/60a96546-1e18-43b8-9a6a-3c9bfd5eca42) |
| 甘雨      | 中文     | 44100 Hz    | 工作还没有做完，又要开始搬砖了。                                | 🔊 [Play](https://github.com/user-attachments/assets/7482e892-630f-47ee-829f-336ceb9525c4) |
| 22娘      | 中英混合 | 22050 Hz    | RTX 5090 将于明年发布，敬请期待！                               | 🔊 [Play](https://github.com/user-attachments/assets/fd6cc25c-79ca-42fc-a60a-352d8f99e437) |
| APPLe     | 英文     | 44100 Hz    | Greetings, madam. I am here. Clouds help predict the weather. | 🔊 [Play](https://github.com/user-attachments/assets/7d513650-e4ab-4449-b2be-d8a49cb3c7fc) |
| Sonetto   | 英文     | 44100 Hz    | Timekeeper, at your service. The stars shine bright tonight.  | 🔊 [Play](https://github.com/user-attachments/assets/fe55763b-5b84-4e19-9960-4e7d2047cb54) |
| Vertin    | 英文     | 44100 Hz    | The storm is coming. We must prepare ourselves.               | 🔊 [Play](https://github.com/user-attachments/assets/b0238546-80b8-422e-ab9e-ed4940cba2e1) |
| 八重神子  | 日文     | 44100 Hz    | たびびと、きょうはどんなおもしろいほんをもってきてくれたの？もしないようがつまらなかったら、わたし、へんしゅうぶに『しげき』にいこうかな～？               | 🔊 [Play](https://github.com/user-attachments/assets/5032b809-f963-415b-8a93-d97644f725cc) |
| 宵宫      | 日文     | 44100 Hz    | こんにちは、皆さん。今日は素晴らしい一日ですね。                | 🔊 [Play](https://github.com/user-attachments/assets/f2fb34e2-fb2d-4e8a-8c95-4555f0adf9aa) |
| 椿        | 日文     | 44100 Hz    | あなたといると、なぜか落ち着くの。              | 🔊 [Play](https://github.com/user-attachments/assets/2e2a6951-a435-4a0c-a9a3-23241c42049c) |
| 野兽先辈  | 日文     | 44100 Hz    | にじゅうよんさいはがくせいです                                  | 🔊 [Play](https://github.com/user-attachments/assets/ea56d3e1-4de4-43c9-ba18-ec48d9e3504d) |


---

## 🎯 22k 采样率底模

新增的 22k 采样率底模放在 [`base_model_22k/`](base_model_22k) 目录下：

> 经过多次测试和取舍，22k 采样率能在性能和效果之间达到最佳的平衡，底模基于原神、鸣潮、绝区零、崩铁四款游戏的角色语音训练，仅供学习交流使用。且对应调整了 decoder 的相关参数，原始训练代码里有一些硬编码逻辑可能需要稍作调整，这里不做赘述。

如需基于该底模做二次训练，请配合 [BertVITS2](https://github.com/fishaudio/Bert-VITS2) 2.3 版本使用。

---

## 📦 作为 AAR 引入到你的 App

推理入口模块 `bertvits2-infer-wrapper` 以及其依赖的 native 模块均已支持 maven-publish，可以一键产出 AAR 给其它 APK 接入。

```bash
./gradlew publishAars
```

完整用法（含坐标自定义、本地仓库目录、下游接入示例等）参见 [PUBLISHING.md](PUBLISHING.md)。

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
| [MNN](https://github.com/alibaba/MNN)                       | `third_party/MNN`            |
| [cppjieba](https://github.com/yanyiwu/cppjieba)             | `third_party/cppjieba`       |
| [tokenizer-cpp](https://github.com/mlc-ai/tokenizers-cpp)   | `third_party/tokenizers-cpp` |


---

## 💡 关于 - 模型蒸馏 -

中文模型基于 [chinese-roberta-wwm-ext-large](https://huggingface.co/hfl/chinese-roberta-wwm-ext-large) 进行蒸馏，为适配移动端，大幅缩减了体积。原版直接转换能有 1.2G ...

日 / 英 BERT 也走相同的蒸馏 + 量化思路。

蒸馏代码详见 [`distill/README.md`](distill/README.md).

---


## 💡 关于 - 自制模型替换 -

1. 如果你需要替换自己的模型尝试验证，首先需要参考 [BertVITS2](https://github.com/fishaudio/Bert-VITS2) 内的说明进行训练得到桌面端模型，目前仅支持 2.3 版本，本工程基于的 BV2 代码 commit 为 13424595，如需自制模型，建议 BV2 代码版本保持一致；若使用 22k 采样率，请参考 [`base_model_22k/`](base_model_22k) 内的 `config.json` 与 `G_0.pth`。
2. 将你的 pth 模型转换成 onnx, onnx 导出脚本在  [这里](https://github.com/fishaudio/Bert-VITS2/blob/master/export_onnx.py)
3. 使用 [MNN Convert](https://mnn-docs.readthedocs.io/en/latest/tools/convert.html) 将所有模块的 onnx 模型转成 mnn, 转换命令参考：

```bash
./MNNConvert --modelFile your_path_to_onnx.onnx --MNNModel your_path_to_mnn.mnn --framework ONNX --bizCode MNN --weightQuantBits 8 --weightQuantAsymmetric
```

4. 放到 bertvits2-jni/src/main/assets 内，加载代码需参考 BertVITS2SimpleInferImpl.kt

---

## 💡 关于 - third_party -

目前在 third_party 内的 cppjieba、tokenizer-cpp 以及 MNN 仅是为了提供头文件，若需要自行编译 tokenizer-cpp 并替换产物 [libtokenizers_c.a](cpptokenizer/src/main/jniStaticLibs/arm64-v8a/libtokenizers_c.a) [libtokenizers_cpp.a](cpptokenizer/src/main/jniStaticLibs/arm64-v8a/libtokenizers_cpp.a)，需修改 [huggingface_tokenizer.cc](third_party/tokenizers-cpp/src/huggingface_tokenizer.cc) 内的 add_special_tokens 默认为 true

---

## 📋 工程大体结构

```
├── app/                              # Demo App
│   └── src/main/
│       ├── assets                    # mnn bert model, cppjieba dic, mnn bv2 model
│       └── java/                     # UI / ViewModel
├── bertvits2-infer-wrapper/          # 对外推理入口（可打 AAR 给其它 App 接入）
├── bertvits2-jni/                    # Bert-VITS2 推理 JNI
├── text-preprocess/                  # 中 / 日 / 英 / 混合 文本预处理
├── cppjieba/                         # cppjieba interface
├── cpptokenizer/                     # cpptokenizer interface
├── openjtalk/                        # open-jtalk interface (日文 G2P)
├── base_model_22k/                   # 22k 采样率底模
├── distill/                          # BERT 蒸馏脚本
└── third_party/                      # 三方头文件
```

---

## 🙌 鸣谢

本工程基于以下前辈们的贡献做了一些微不足道的搬砖工作，也希望能为后续在端智能推理捣鼓的小伙伴提供一些参考。

- [VITS](https://github.com/jaywalnut310/vits)
- [BertVITS2](https://github.com/fishaudio/Bert-VITS2)
- [MNN](https://github.com/alibaba/MNN)
- [cppjieba](https://github.com/yanyiwu/cppjieba)
- [tokenizer-cpp](https://github.com/mlc-ai/tokenizers-cpp)
- [open-jtalk](http://open-jtalk.sourceforge.net/)

---

## 🛠️ 后续工作

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