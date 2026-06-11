# Vivo OTA Tracker

Vivo OTA Tracker 是一个基于 [unidbg](https://github.com/zhkl0228/unidbg) 的 vivo / iQOO OTA 链接获取工具。它通过本地加载 vivo Updater 使用的 native 加密库，构造官方 OTA 请求参数，并从 vivo 官方服务器获取固件包下载链接。

当前主要面向中国区 OTA 接口。

## 功能

- 构造 vivo / iQOO OTA 查询参数。
- 本地调用 `libvivoseckey` 完成 `jvq_param` 加密和响应解密。
- 支持通过 Java 系统属性传入机型、版本、设备类型等参数。
- 支持手机和部分平板参数模板。
- 支持通过 `pk/redirPost.do` 获取最终 `.zip` 下载直链。

## 文件放置

在 unidbg 项目根目录创建 `libs/`，放入从目标设备或 Updater APK 中提取的文件：

```text
unidbg-master/
├── libs/
│   └── libvivoseckey_n4.so
```

将 `VivoOtaTracker.java` 放到 unidbg-android 模块源码目录：

```text
unidbg-master/
└── unidbg-android/
    └── src/
        └── main/
            └── java/
                └── com/
                    └── vivo/
                        └── ota/
                            └── VivoOtaTracker.java
```

## 编译

推荐使用 JDK 8。若使用较新的 JDK，也建议用 Java 8 目标版本编译：

```bash
javac --release 8 -cp unidbg-android-0.9.10-SNAPSHOT.jar -d classes VivoOtaTracker.java
```

如果使用完整 unidbg 源码工程：

```bash
./mvnw clean install -DskipTests -Dgpg.skip=true
./mvnw exec:java -pl unidbg-android -Dexec.mainClass="com.vivo.ota.VivoOtaTracker"
```

## 参数说明

工具通过 Java `-D` 系统属性读取参数。

| 参数 | 说明 | 示例 |
| --- | --- | --- |
| `DEVICE_TYPE` | 设备类型，`phone` 或 `tablet` | `tablet` |
| `MODEL_SW_VER` | 内部项目代号 / 软件型号 | `DPD2106` |
| `DEVICE_MODEL` | 公开机型 / 入网型号 | `PA2170` |
| `SW_VERSION` | 当前基线版本 | `8.7.22` |
| `ANDROID_VER` | Android / OriginOS 大版本 | `14` |
| `IS_FULL` | 是否请求完整包标记 | `false` |
| `OTA_TYPE` | 可选 OTA 类型，通常留空 | `RECOVERY` |
| `ALWAYS_FAILED_VERSION` | 可选失败版本标记 | `DPD2106_N_DPD2106MA_8.7.22` |
| `SNP` | 平板序列号参数 | `400C5R007A00000` |
| `OEM_PROJECTS` | 设备 `ro.build.oem.projects` | `DPD2106 DPD2106B` |
| `VERBOSE` | 输出原始参数和响应 | `true` |

高级覆盖参数：

```text
HW_VER
FULL_VER
VERSION
SW_VER
APP_VER_NAME
APP_VER_CODE
```

## 示例：vivo Pad DPD2106

已验证可查询 `DPD2106_A_8.7.22 -> 8.7.30` 的 OTA 包。

注意：该版本链路需要 `IS_FULL=false`，并且不要强制设置 `OTA_TYPE=RECOVERY`。强制 `RECOVERY` 或 `IS_FULL=true` 会返回 `retcode=210`。

```bash
java \
  -DDEVICE_TYPE=tablet \
  -DMODEL_SW_VER=DPD2106 \
  -DDEVICE_MODEL=PA2170 \
  -DSW_VERSION=8.7.22 \
  -DANDROID_VER=14 \
  -DIS_FULL=false \
  -DSNP=400C5R007A00000 \
  -DVERBOSE=true \
  -cp classes:unidbg-android-0.9.10-SNAPSHOT.jar \
  com.vivo.ota.VivoOtaTracker
```

已验证结果：

```text
Version: 8.7.30
Filename: 21ef4221e306eaa6ef5ac7de790e759f.zip
Size: 391997868 bytes
```

## 示例：iQOO Neo9S Pro PD2339

已验证可查询 `PD2339 / V2339FA / 16.2.10.1.W10.V000L1`。

```bash
java \
  -DDEVICE_TYPE=phone \
  -DMODEL_SW_VER=PD2339 \
  -DDEVICE_MODEL=V2339FA \
  -DSW_VERSION=16.2.10.1.W10.V000L1 \
  -DANDROID_VER=16 \
  -DVERBOSE=true \
  -cp classes:unidbg-android-0.9.10-SNAPSHOT.jar \
  com.vivo.ota.VivoOtaTracker
```

已验证结果：

```text
Version: 16.2.10.1.W10.V000L1
Filename: 20260516001650459683edfa517acd4f2101280c98ce8b.zip
Size: 9248486792 bytes
OTA Type: AB
```

## 常见问题

### 返回 `{"message":"无更新","retcode":210}`

常见原因：

- `SW_VERSION` 不在当前官方开放升级路线中。
- 当前版本已经是最新版本。
- `IS_FULL` 与服务器实际推送包类型不匹配。
- 强制设置了不合适的 `OTA_TYPE`。
- 设备参数与真实设备差异过大，例如 `MODEL_SW_VER`、`DEVICE_MODEL`、`SNP`、`OEM_PROJECTS`。
- 推送配额已满、包被临时下架，或请求频率过高。

排查建议：

- 先用真实设备当前基线版本查询。
- 平板优先尝试 `IS_FULL=false` 且 `OTA_TYPE` 留空。
- 手机版本号通常保留 `.V000L1`。
- 如果当前版本已经安装到目标版本，查询当前版本通常会返回 `210`。

### Linux 下报 `libunicorn_java.so` 或 native backend 错误

部分 unidbg 版本在 Linux 上默认 backend 可能无法直接加载。可以切换到 Unicorn2 backend，或确保 `java.library.path` 指向包含 `libunicorn.so` 的目录。

### 是否一定需要真实设备

不一定。只要参数在官方服务器的升级路线中，工具可以直接在 PC 上请求 OTA。  
但某些新版本可能会进行更严格的业务校验，这时真实设备参数越完整，命中率越高。

## 免责声明

本项目仅用于技术学习、设备维护和安全研究。请勿用于非法或商业用途。使用本工具造成的一切后果由使用者自行承担。
