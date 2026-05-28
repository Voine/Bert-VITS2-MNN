# 发布 AAR 给下游 APK 接入

工程里 `bertvits2-infer-wrapper` 是对外推理入口，它依赖以下兄弟模块（含 JNI / native so）：

- `bertvits2-infer-wrapper`（API 入口）
- `text-preprocess`（中/英/日文本预处理）
- `bertvits2-jni`（推理 JNI）
- `cpptokenizer`、`cppjieba`、`openjtalk`（native 三件套）

由于这些模块各自带有 native 代码（`.so`），一个"胖 AAR"会带来不少坑（AGP 8+ 的 fat-aar 兼容性差）。所以采用 **多 AAR + 本地 Maven 仓库** 的方式发布，下游只需要引一个坐标即可把整个依赖图拉进来。

---

## 1. 一键发布到本地 Maven 仓库

```bash
./gradlew publishAars
```

产物位置：`<repo>/build/repo/com/example/bertvits2mnn/...`

结构示例：

```
build/repo/
└── com/example/bertvits2mnn/
    ├── bertvits2-infer-wrapper/1.0.5/
    │   ├── bertvits2-infer-wrapper-1.0.5.aar
    │   ├── bertvits2-infer-wrapper-1.0.5.pom
    │   └── ...
    ├── text-preprocess/1.0.5/...
    ├── bertvits2-jni/1.0.5/...
    ├── cpptokenizer/1.0.5/...
    ├── cppjieba/1.0.5/...
    └── openjtalk/1.0.5/...
```

可通过命令行覆盖坐标：

```bash
./gradlew publishAars \
    -PpublishGroupId=com.foo.bar \
    -PpublishVersion=2026.05.28
```

---

## 2. 下游 APK 接入

把 `build/repo/` 整个拷到下游工程（或上传到内网 Maven），然后：

**`settings.gradle`：**

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("path/to/repo") }   // 本地仓库
    }
}
```

**`app/build.gradle`：**

```groovy
dependencies {
    implementation "com.example.bertvits2mnn:bertvits2-infer-wrapper:1.0.5"
}
```

`text-preprocess` / `bertvits2-jni` / `cpptokenizer` / `cppjieba` / `openjtalk` 都会被 POM 里的传递依赖自动拉进来，包括它们的 `.so`。

> ⚠️ 注意：当前所有 native 模块 `abiFilters` 只打 `arm64-v8a`，下游 APK 也需要相应配置或不再额外限制 ABI。

---

## 3. 只想拿到一堆 AAR 手工分发

```bash
./gradlew collectAars
```

所有 release AAR 会被复制到 `build/aars/` 并按 `<artifactId>-<version>.aar` 命名。下游需要 6 个全部 `implementation files(...)` 或者放进 `libs/`，并自行处理 `text-preprocess` 用到的第三方依赖（`pinyin`、`icu4j`、`androidx.core`、`androidx.appcompat`、`gson`）。

> 推荐用第 1 种 maven-publish 方式，依赖与传递关系都会被正确写进 POM，下游 APK 接入零心智负担。

---

## 4. 只装配不发布

```bash
./gradlew assembleReleaseAll
```

每个模块的 `<module>/build/outputs/aar/<module>-release.aar` 会被生成出来。

---

## 5. 涉及的 Gradle 脚本

- `publish-aar.gradle`（根目录）：通用 maven-publish 配置，apply 到每个 library 模块末尾。
- `build.gradle`（根）：聚合任务 `publishAars` / `collectAars` / `assembleReleaseAll`。
- `app_version.gradle`：默认版本号取自 `versionNameExt`。
