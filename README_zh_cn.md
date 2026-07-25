# Vivo OTA 抓包工具（非官方）

Vivo OTA Tracker 是一个基于 [unidbg](https://github.com/zhkl0228/unidbg) 框架开发的开源工具，可以让你在电脑上直接获取 Vivo 手机（目前仅支持国行）的官方 OTA 固件下载链接。

它的原理是在本地加载特定的动态库，模拟系统更新时的参数拼装过程，从而拿到完整包或增量包的直链，不需要真的拿手机去请求。

---

**主要特点：**
* 📦 **捞固件链接**：在电脑上构建设备参数，向官方服务器请求，提取出对应机型的固件下载直链。
* ⚙️ **自动处理**：自动帮你完成参数转换、数据封装等步骤，不用手动算来算去。
* 📱 **支持多机型**：可以自由指定机型、系统版本等参数，灵活适配不同设备。

---

### 🛠️ 环境准备

如果你想在命令行下直接跑这个工具，不需要装 IDEA 或 Eclipse 之类的 IDE，那需要先配好以下基础环境：

1. **Java 环境（JDK）**：
   * **强烈推荐用 JDK 8（Java 1.8）**。
   * 如果你用 JDK 11 或 17 及以上，可能会碰到 Java 包名冲突的问题（后面 FAQ 会讲怎么解决）。
   * 确保 `java` 和 `javac` 命令能在命令行里正常使用（也就是配好了环境变量 `PATH`）。
2. **Maven 构建工具**：
   * 你可以用电脑上全局安装的 Maven（也就是敲 `mvn` 命令那种）。
   * 也可以用 unidbg 项目根目录里自带的 Maven Wrapper（Windows 下是 `mvnw.cmd`，Linux/Mac 下是 `./mvnw`）。

---

### 📁 文件放哪要严格按规矩来

因为 unidbg 是多模块项目，**文件必须放在指定位置**，否则命令行运行时会报找不到类或库的错误。

1. **放 Native 库文件**
   在 unidbg 项目的**根目录**下新建一个 `libs` 文件夹，然后把从手机里提取到的相关文件放进去：
   ~~~text
   unidbg-master/
   ├── libs/
   │   └── libvivoseckey_n4.so    # 动态库文件
   ~~~

2. **放 Java 源码**
   把本工具的 `VivoOtaTracker.java` 文件放到 `unidbg-android` 模块的 `main` 源码目录下：
   ~~~text
   unidbg-master/
   └── unidbg-android/
       └── src/
           └── main/
               └── java/
                   └── com/
                       └── vivo/
                           └── ota/
                               └── VivoOtaTracker.java
   ~~~

---

### ⚙️ 设备参数怎么配

工具会从 Java 系统属性里读取设备参数，所以你不用改源码，直接在命令行用 `-D` 传参就行。如果某个参数没传，就使用源码里的默认值。

| 属性名 | 说明 | 示例值 |
|--------|------|--------|
| `DEVICE_TYPE` | 设备类型，填 `phone` 或 `tablet` | `phone` |
| `MODEL_SW_VER` | 内部项目代号 / 软件型号 | `PD2408` |
| `DEVICE_MODEL` | 对外销售型号 / 入网型号 | `V2408A` |
| `SW_VERSION` | 当前基础系统版本号 | `16.1.16.5.W10` |
| `ANDROID_VER` | Android 或 OriginOS 大版本（平板请求里会用到） | `16` |
| `IS_FULL` | 是否强制请求完整包。手机默认 `true`，平板默认 `false` | `true` |
| `SNP` | 请求参数里用的序列号 | `A0000000000000A` |
| `VERBOSE` | 是否打印原始响应内容（方便调试） | `true` |

另外，如果某个机型不按默认的 `MODEL_A_VERSION` / `MODEL_N_HW_VERSION` 套路走，你还可以单独覆盖这些高级参数：`HW_VER`、`FULL_VER`、`VERSION`、`SW_VER`、`APP_VER_NAME`、`APP_VER_CODE`。

至于 `MODEL_SW_VER` 和 `DEVICE_MODEL` 到底填啥，可以参考[这个网站](https://khwang9883.github.io/MobileModels/brands/vivo_cn.html)查。

---

### 🚀 命令行运行步骤

打开终端或 CMD / PowerShell，**先用 `cd` 命令切到 unidbg 项目的根目录**（就是放着 `pom.xml` 和 `mvnw.cmd` 的那个文件夹），然后按顺序执行下面两条命令：

#### 第一步：全局编译并安装基础依赖
因为 unidbg 是多模块项目，得先把基础模块（比如 api 模块）装到本地 Maven 仓库里，记得**跳过测试和 GPG 签名验证**。
* **Windows：**
  ~~~cmd
  mvnw.cmd clean install -DskipTests -Dgpg.skip=true
  ~~~
* **Linux / macOS：**
  ~~~bash
  ./mvnw clean install -DskipTests -Dgpg.skip=true
  ~~~
*（注意：这一步只在第一次用这个工具，或者 unidbg 源码有更新时才需要跑。如果看到 `BUILD SUCCESS` 就说明成功了）*

#### 第二步：运行固件抓取工具
用 `-pl unidbg-android` 指定只跑这个子模块，然后执行主程序（这里用的是默认配置）：
* **Windows：**
  ~~~cmd
  mvnw.cmd exec:java -pl unidbg-android -Dexec.mainClass="com.vivo.ota.VivoOtaTracker"
  ~~~
* **Linux / macOS：**
  ~~~bash
  ./mvnw exec:java -pl unidbg-android -Dexec.mainClass="com.vivo.ota.VivoOtaTracker"
  ~~~

你也可以像下面这样自定义参数（示例是查平板）：
* **Windows（换行用 ^）：**
  ~~~cmd
  mvnw.cmd exec:java -pl unidbg-android -Dexec.mainClass="com.vivo.ota.VivoOtaTracker" ^
    -DDEVICE_TYPE=tablet ^
    -DMODEL_SW_VER=DPD2106 ^
    -DDEVICE_MODEL=PA2170 ^
    -DSW_VERSION=8.7.22 ^
    -DANDROID_VER=14 ^
    -DIS_FULL=false ^
    -DVERBOSE=true
  ~~~
* **Linux / macOS（换行用 \）：**
  ~~~bash
  ./mvnw exec:java -pl unidbg-android -Dexec.mainClass="com.vivo.ota.VivoOtaTracker" \
    -DDEVICE_TYPE=tablet \
    -DMODEL_SW_VER=DPD2106 \
    -DDEVICE_MODEL=PA2170 \
    -DSW_VERSION=8.7.22 \
    -DANDROID_VER=14 \
    -DIS_FULL=false \
    -DVERBOSE=true
  ~~~

只要参数填对了，网络也通畅，控制台就会打印出设备初始化信息、升级包版本和大小，最后直接输出 **固件 `.zip` 的下载直链**。

---

### 🆘 常见问题 & 解决办法

命令行编译运行时，因为各人 Java 环境不一样，可能会遇到下面这些报错，这里都给你列好了：

#### ❌ 报错 `maven-gpg-plugin: sign failed / Exit code: 2`
* **原因**：框架默认开启了 GPG 签名（为了发布到中央仓库用的），你电脑上没有配 GPG 密钥，打包就会失败。
* **解决办法**：我们本地跑工具不需要签名，所以要在 `install` 命令后面加上 `-Dgpg.skip=true` 来跳过（参照上面第一步）。

#### ❌ 报错 `Could not resolve dependencies ... com.github.zhkl0228:unidbg-api:jar`
* **原因**：你没在根目录先全局编译，就直接跑去跑子模块了，Maven 找不到依赖。
* **解决办法**：必须先回到项目根目录，执行完整的安装命令：`mvnw.cmd clean install -DskipTests -Dgpg.skip=true`。

#### ❌ 报错 `Ambiguous reference to Module`
* **原因（经典的 Java 版本冲突）**：这个框架最早是基于 Java 8 开发的，里面自己定义了一个类叫 `com.github.unidbg.Module`。但从 Java 9 开始，官方也内置了一个同名类 `java.lang.Module`，如果你用的是 JDK 11 或 17，编译器就懵了，不知道你指的是哪个。
* **解决办法（二选一）**：
  1. **【最推荐】换 JDK 版本**：把系统环境变量 `JAVA_HOME` 改成 **JDK 8（1.8）**，然后重新打开命令行，编译就能顺顺利利通过。
  2. **【手动改代码】如果你非要死磕 JDK 17**：根据报错提示找到对应的文件（比如 `AbstractARMDebugger.java`、`AndroidElfLoader.java` 等），在文件顶部的 `import` 区域手动加一行：
     `import com.github.unidbg.Module;`
     然后重新编译。

#### ❌ 报错 `ClassNotFoundException: com.vivo.ota.VivoOtaTracker`
* **原因**：文件放错地方了。你可能把 `VivoOtaTracker.java` 放到了 `src/test/java` 目录下，但命令行默认只跑 `src/main/java` 里的程序。
* **解决办法**：
  务必把文件挪到 `unidbg-android/src/main/java/com/vivo/ota/` 目录下。然后执行 `mvnw.cmd compile -pl unidbg-android` 重新编译，再跑启动命令。

#### ❌ 服务器返回 `{"message":"No update","retcode":210}`
* **原因**：因为服务器有业务校验，拿不到固件信息。常见情况有：
  1. 你填的 `SW_VERSION`（当前版本号）不在官方升级白名单里。
  2. 这个机型 / 版本的推送名额已满，或者官方暂时下架了包。
  3. 请求太频繁，被临时限制了。
* **解决办法**：换其他型号或版本号试试；或者去论坛、贴吧确认一下当前能收到更新的确切版本号，填进去再跑。

---

### 📜 免责声明
本项目仅供技术学习和交流使用，请勿用于任何非法或商业用途。因使用不当造成的任何问题，由使用者自行承担。
