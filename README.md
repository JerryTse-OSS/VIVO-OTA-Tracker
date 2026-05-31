# Vivo OTA Tracker

## Vivo OTA Tracker is an open-source tool built on top of the [unidbg](https://github.com/zhkl0228/unidbg) framework. It is designed to fetch official OTA firmware download links(Currently China Only) for various Vivo devices directly from your computer.

By loading specific dynamic libraries locally, this tool processes the necessary request parameters for system updates, allowing you to obtain full or incremental firmware download links without needing a physical phone environment.

**Core Features:**
* 📦 **Firmware Fetching**: Construct device parameters on your PC to request and extract firmware download direct links for various models from official servers.
* ⚙️ **Automated Processing**: Automatically handles parameter conversions and data encapsulation required for fetching firmware.
* 📱 **Multi-Device Support**: Supports custom device models, system versions, and other parameters to flexibly fetch firmware for different devices.

---

### 🛠️ Environment Prerequisites

To run this tool via the command line without relying on any IDEs like IntelliJ IDEA or Eclipse, you need to configure the following basic environment:

1. **Java Environment (JDK)**: 
   * **JDK 8 (Java 1.8) is highly recommended**.
   * If you use JDK 11 or JDK 17 and above, you may encounter specific Java package name conflicts (see the FAQ below for solutions).
   * Ensure the `java` and `javac` commands are correctly added to your system's `PATH` environment variable.
2. **Maven Build Tool**: 
   * You can use the globally installed Maven on your computer (using the `mvn` command).
   * Alternatively, you can use the Maven Wrapper included in the root directory of unidbg project (`mvnw.cmd` for Windows or `./mvnw` for Linux/Mac).

---

### 📁 File Placement Guide

Due to the multi-module structure of `unidbg`, you must **strictly place the files according to the following directory structure**, otherwise, it will result in class or library file not found errors when running via the command line:

1. **Place Native Libraries and APK**
   Create a folder named `libs` in the **root directory** of the unidbg project and place the relevant files obtained from the phone into it:
   ~~~text
   unidbg-master/
   ├── libs/
   │   ├── libvivoseckey_n4.so    # Library
   │   └── Updater.apk            # Official system update APK (used to extract official certificate info)
   ~~~

2. **Place Java Source Code**
   Place the `VivoOtaTracker.java` file of this tool into the **main source (main)** directory of the `unidbg-android` module:
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

### ⚙️ Modify Device Configuration

Before running, open the `VivoOtaTracker.java` file with any text editor (like Notepad, VS Code), scroll to the `main` method at the bottom of the file, and modify the target device parameters you want to query:

~~~java
// ==================== User Configuration Area ====================
final String DEVICE_TYPE = "phone";     // Device type: "tablet" or "phone"
final String MODEL_SW_VER = "PD2502";   // Internal model code (e.g., PD2314, DPD2429)
final String DEVICE_MODEL = "V2502A";   // Public marketing model (e.g., V2314A, PA2573)
final String SW_VERSION = "16.0.14.7.W10"; // Your base system version (server provides packages >= this version)
final int ANDROID_VER = 16;             // FuntouchOS / OriginOS major version (13=OS3, 14=OS4, 15=OS5)
// =================================================================
~~~

You may check the SW_MODEL and DEVICE_MODEL from [here](https://khwang9883.github.io/MobileModels/brands/vivo_cn.html)

---

### 🚀 Command Line Run Guide

Open your terminal or command prompt (CMD / PowerShell), **you must use the `cd` command to enter the root directory of the unidbg project** (the directory containing `pom.xml` and `mvnw.cmd`), and execute the following two commands in order:

#### Step 1: Global Compile and Install Base Dependencies
Since unidbg is a multi-module project, you must first install the base libraries (such as the api module) to the local Maven cache, and **skip tests and code signature verification**.
* **Windows:**
  ~~~cmd
  mvnw.cmd clean install -DskipTests -Dgpg.skip=true
  ~~~
* **Linux / macOS:**
  ~~~bash
  ./mvnw clean install -DskipTests -Dgpg.skip=true
  ~~~
*(Note: This step only needs to be executed once the first time you use this tool, or when the unidbg framework source code changes. If you see `BUILD SUCCESS`, it means it's successful)*

#### Step 2: Run the Firmware Fetcher Tool
Use `-pl unidbg-android` to specify running the submodule, and execute our main program:
* **Windows:**
  ~~~cmd
  mvnw.cmd exec:java -pl unidbg-android -Dexec.mainClass="com.vivo.ota.VivoOtaTracker"
  ~~~
* **Linux / macOS:**
  ~~~bash
  ./mvnw exec:java -pl unidbg-android -Dexec.mainClass="com.vivo.ota.VivoOtaTracker"
  ~~~

If the parameters are set correctly and the network is clear, the console will output the device initialization information, the version and size information of the update package, and automatically print out the **final firmware `.zip` download direct link**.

---

### 🆘 FAQ & Troubleshooting

When compiling and running from the command line, limited by the differences in Java environments on various computers, you may encounter the following common errors. Detailed troubleshooting steps are provided here:

#### ❌ Error 1: `maven-gpg-plugin: sign failed / Exit code: 2`
* **Cause**: The framework is configured by default with GPG signature verification (used to publish code to the central repository). If your computer does not have a GPG key configured, packaging will fail.
* **Solution**: We do not need to publish code when running the tool locally. You must add the `-Dgpg.skip=true` parameter after the `install` command to skip the signature (see Step 1 above).

#### ❌ Error 2: `Could not resolve dependencies ... com.github.zhkl0228:unidbg-api:jar`
* **Cause**: You directly tried to run the `unidbg-android` submodule without globally compiling the root project. Maven cannot find its prerequisites.
* **Solution**: You must first execute a complete installation command in the project root directory: `mvnw.cmd clean install -DskipTests -Dgpg.skip=true`.

#### ❌ Error 3: `Ambiguous reference to Module`
* **Cause (Classic Java Version Conflict)**: The framework was originally developed based on Java 8, and internally defined a class called `com.github.unidbg.Module`. But starting from Java 9, Java officially added a built-in class with the same name, `java.lang.Module`. If your current environment uses JDK 11 or JDK 17 for compilation, the compiler will abort due to the "name conflict".
* **Solution (Choose one)**:
  1. **[Most Recommended] Switch JDK Version**: Change the system's `JAVA_HOME` environment variable to **JDK 8 (1.8)**, reopen the command line, and you can compile perfectly and smoothly without any errors.
  2. **[Manually Fix Code]**: If you insist on using JDK 17, open the corresponding error file (such as `AbstractARMDebugger.java`, `AndroidElfLoader.java`, etc.) according to the error prompt in the command line, and manually add an import line in the `import` area at the top of the file:
     `import com.github.unidbg.Module;` 
     Then use the command to redo the compilation.

#### ❌ Error 4: `ClassNotFoundException: com.vivo.ota.VivoOtaTracker`
* **Cause**: The file placement path is incorrect. You may have placed `VivoOtaTracker.java` in the `src/test/java` directory, while the command line runs programs in the `src/main/java` directory by default.
* **Solution**:
  Please be sure to move the file to the `unidbg-android/src/main/java/com/vivo/ota/` directory. Then execute `mvnw.cmd compile -pl unidbg-android` to recompile the code, and finally run the startup command again.

#### ❌ Error 5: Server returns `{"message":"No update","retcode":210}`
* **Cause**: The firmware information cannot be obtained due to server-side business verification interception. Common causes:
  1. The `SW_VERSION` (base version number) you entered is not in the official open upgrade roadmap.
  2. The push quota for this model/version is full, or the official has temporarily taken down the package.
  3. Requests are too frequent and temporarily restricted.
* **Solution**: Modify the configuration area code to test other models; or check forums/tieba to confirm the exact system version number that can currently receive updates for this model, and retry after filling it in the code.

---

### 📜 Disclaimer
This project is for technical learning and communication purposes only. Do not use it for any illegal or commercial purposes. The user bears all consequences for any problems caused by improper use.
