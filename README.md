# Vivo OTA Tracker

Vivo OTA Tracker is an open-source tool for fetching official OTA firmware download links (Currently China Only) for various Vivo devices directly from your computer.

---

**Core Features:**
* 📦 **Firmware Fetching**: Construct device parameters on your PC to request and extract firmware download direct links for various models from official servers.
* ⚙️ **Automated Processing**: Automatically handles parameter conversions and data encapsulation required for fetching firmware.
* 📱 **Multi-Device Support**: Supports custom device models, system versions, and other parameters to flexibly fetch firmware for different devices.

---

### 🛠️ Environment Prerequisites

#### Python Version
* **Python 3.8 or higher**
* Install dependencies:
  ```bash
  pip install requests pycryptodome
  ```

---

### 🚀 Quick Start

#### Python
```bash
# Show usage (no arguments)
python VivoOtaTracker.py

# Phone query (full package)
python VivoOtaTracker.py -t phone -m PD2408 -d V2408A -v 16.1.16.5.W10 -a 16 --isfull true

# Tablet query (delta package)
python VivoOtaTracker.py -t tablet -m DPD2106 -d PA2170 -v 8.7.22 -a 14 --isfull false --verbose
```

---

### ⚙️ Device Configuration

#### Python (CLI Arguments)

| Argument | Description | Example |
|----------|-------------|---------|
| `-t`, `--device-type` | Device type: `phone` or `tablet` | `phone` |
| `-m`, `--model` | Software model code | `PD2408` |
| `-d`, `--device` | Device model | `V2408A` |
| `-v`, `--version` | Current software version | `16.1.16.5.W10` |
| `-a`, `--android-ver` | Android/OriginOS major version | `16` |
| `--isfull` | Full package flag: `true` or `false` | `true` |
| `--snp` | Serial number (optional) | `A0000000000000A` |
| `--verbose` | Print raw responses (optional) | `--verbose` |

You may check the SW_MODEL and DEVICE_MODEL from [here](https://khwang9883.github.io/MobileModels/brands/vivo_cn.html)

---

### 🆘 FAQ & Troubleshooting

#### ❌ Error: Server returns `{"message":"No update","retcode":210}`
* **Cause**: The firmware information cannot be obtained due to server-side business verification interception. Common causes:
  1. The `SW_VERSION` (base version number) you entered is not in the official open upgrade roadmap.
  2. The push quota for this model/version is full, or the official has temporarily taken down the package.
  3. Requests are too frequent and temporarily restricted.
* **Solution**: Modify the configuration area code to test other models; or check forums/tieba to confirm the exact system version number that can currently receive updates for this model, and retry after filling it in the code.

---

### 📜 Disclaimer
This project is for technical learning and communication purposes only. Do not use it for any illegal or commercial purposes. The user bears all consequences for any problems caused by improper use.