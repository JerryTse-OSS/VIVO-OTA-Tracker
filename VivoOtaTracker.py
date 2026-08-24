#!/usr/bin/env python3
"""
Vivo OTA Tracker - Query OTA update information for Vivo devices.

Pure Python implementation using AES-128-CBC encryption
extracted from libvivoseckey_n4.so via reverse engineering.

Author: VIVO-OTA-Tracker Contributors
"""

import argparse
import base64
import binascii
import gzip
import json
import os
import random
import re
import sys
import time
import zlib
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Tuple
from urllib.parse import urlencode, quote_plus

import ssl
import requests
import urllib3
from requests.adapters import HTTPAdapter
from urllib3.util.ssl_ import create_urllib3_context
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad, unpad

# Suppress SSL warnings
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


# Custom SSL adapter for compatibility
class SSLAdapter(HTTPAdapter):
    """Custom HTTP adapter with flexible SSL configuration."""

    def init_poolmanager(self, *args, **kwargs):
        ctx = create_urllib3_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        ctx.set_ciphers('DEFAULT:@SECLEVEL=1')
        kwargs['ssl_context'] = ctx
        return super().init_poolmanager(*args, **kwargs)


# ============================================================
# Constants
# ============================================================

TOKEN_NATIVE = "jnisgmain_v2@com.bbk.updater"

# AES-128-CBC encryption parameters (extracted from SO)
AES_IV = bytes.fromhex("047cd76d65d3b28b4ccc2c0246681aa6")
AES_KEY_KV1 = bytes.fromhex("5da590863052089893199b2a901b6470")
AES_KEY_KV2 = bytes.fromhex("836e75afddae728551ad22b2bae6ca57")
PUB_KEY_HASH = "077c62d0246d6572d2760e3247a1ebcaf68eb4161aa13353583c445ce71fc49c"

# API endpoints
BASE_URL = "https://sysupgrade.vivo.com.cn"
UPDATE_ENDPOINT = "/vgc/v2/getVgcAndPatch.do?"
REDIR_ENDPOINT = "/pk/redirPost.do"


# ============================================================
# Dataclasses
# ============================================================

@dataclass
class DeviceConfig:
    """Device configuration for OTA query."""
    device_type: str = "phone"
    model_sw_ver: str = "PD2408"
    device_model: str = "V2408A"
    sw_version: str = "16.1.16.5.W10"
    android_ver: int = 16
    snp: str = "A0000000000000A"
    is_full: bool = True
    verbose: bool = False


@dataclass
class UpdateInfo:
    """Parsed OTA update information."""
    version: Optional[str] = None
    filename: Optional[str] = None
    size: Optional[str] = None
    size_mb: Optional[int] = None
    download_url: Optional[str] = None
    changelog_url: Optional[str] = None
    raw_response: Optional[str] = None


# ============================================================
# Crypto Functions
# ============================================================

def aes_encrypt(plaintext: bytes, key_version: int = 2) -> bytes:
    """
    Encrypt data using AES-128-CBC with PKCS7 padding.

    Args:
        plaintext: Data to encrypt
        key_version: Key version (1 or 2)

    Returns:
        Encrypted data
    """
    key = AES_KEY_KV1 if key_version == 1 else AES_KEY_KV2
    cipher = AES.new(key, AES.MODE_CBC, AES_IV)
    padded = pad(plaintext, AES.block_size)
    return cipher.encrypt(padded)


def aes_decrypt(ciphertext: bytes, key_version: int = 2) -> bytes:
    """
    Decrypt data using AES-128-CBC with PKCS7 padding.

    Args:
        ciphertext: Data to decrypt
        key_version: Key version (1 or 2)

    Returns:
        Decrypted data
    """
    key = AES_KEY_KV1 if key_version == 1 else AES_KEY_KV2
    cipher = AES.new(key, AES.MODE_CBC, AES_IV)
    decrypted = cipher.decrypt(ciphertext)
    return unpad(decrypted, AES.block_size)


# ============================================================
# Protocol Functions
# ============================================================

def build_protocol_package(
    msg_type: int,
    key_version: int,
    token: str,
    data: bytes
) -> bytes:
    """
    Build a protocol package with header and encrypted payload.

    Args:
        msg_type: Message type (5 for encrypt)
        key_version: Key version (1 or 2)
        token: Protocol token string
        data: Encrypted payload

    Returns:
        Complete protocol package bytes
    """
    token_bytes = token.encode("utf-8")
    header_total_len = 16 + len(token_bytes)

    # Build header fields
    header_fields = bytearray()
    header_fields += (1).to_bytes(2, "big")  # version
    header_fields += len(token_bytes).to_bytes(1, "big")
    header_fields += token_bytes
    header_fields += key_version.to_bytes(2, "big")
    header_fields += msg_type.to_bytes(1, "big")

    # CRC32 of header fields
    crc = zlib.crc32(header_fields) & 0xFFFFFFFF

    # Build full package
    package = bytearray()
    package += header_total_len.to_bytes(2, "big")
    package += crc.to_bytes(8, "big")
    package += header_fields
    package += data

    return bytes(package)


def extract_encrypted_body(full_package: bytes) -> bytes:
    """
    Extract encrypted payload from protocol package.

    Args:
        full_package: Complete protocol package

    Returns:
        Encrypted payload bytes
    """
    header_len = int.from_bytes(full_package[:2], "big")
    return full_package[header_len:]


def base64_url_encode(data: bytes) -> str:
    """Encode bytes to URL-safe base64 string."""
    return base64.urlsafe_b64encode(data).decode("ascii")


def base64_url_decode(data: str) -> bytes:
    """Decode URL-safe base64 string to bytes."""
    from urllib.parse import unquote
    # First URL-decode the string (like Java's URLDecoder.decode)
    decoded = unquote(data)
    # Then replace URL-safe base64 characters
    decoded = decoded.replace("-", "+").replace("_", "/")
    # Add padding if needed
    pad_len = 4 - len(decoded) % 4
    if pad_len != 4:
        decoded += "=" * pad_len
    return base64.b64decode(decoded)


def encrypt_to_jvq(plaintext: str, key_version: int = 2) -> str:
    """
    Encrypt plaintext to jvq_param format.

    Args:
        plaintext: Text to encrypt
        key_version: Key version (1 or 2)

    Returns:
        Base64-url-encoded protocol package
    """
    plaintext_bytes = plaintext.encode("utf-8")
    encrypted = aes_encrypt(plaintext_bytes, key_version)
    package = build_protocol_package(5, key_version, TOKEN_NATIVE, encrypted)
    return base64_url_encode(package)


def decrypt_response(response_b64: str, key_version: int = 2) -> str:
    """
    Decrypt a base64-encoded response.

    Args:
        response_b64: Base64-url-encoded response
        key_version: Key version (1 or 2)

    Returns:
        Decrypted plaintext string
    """
    full_package = base64_url_decode(response_b64)
    encrypted_body = extract_encrypted_body(full_package)
    decrypted = aes_decrypt(encrypted_body, key_version)
    return decrypted.decode("utf-8")


# ============================================================
# HTTP Functions
# ============================================================

def http_post(url: str, body: str, timeout: int = 60, max_retries: int = 3) -> str:
    """
    Send HTTP POST request with retry logic.

    Args:
        url: Request URL
        body: Request body
        timeout: Request timeout in seconds
        max_retries: Maximum number of retries

    Returns:
        Response body string
    """
    # Match Java version's Content-Type exactly
    headers = {
        "Content-Type": "application/json; charset=utf-8",
        "User-Agent": "okhttp/4.3.23",
        "Host": "sysupgrade.vivo.com.cn",
        "Connection": "Keep-Alive",
        "Accept-Encoding": "gzip",
    }

    # Create session with custom SSL adapter
    session = requests.Session()
    session.mount("https://", SSLAdapter())

    last_error = None
    for attempt in range(max_retries):
        try:
            response = session.post(
                url,
                data=body.encode("utf-8"),
                headers=headers,
                timeout=(10, timeout),  # (connect timeout, read timeout)
                verify=False,
            )
            response.raise_for_status()

            # Get raw content
            content = response.content

            # Try to decompress if it looks like gzip
            if len(content) >= 2 and content[:2] == b'\x1f\x8b':
                try:
                    content = gzip.decompress(content)
                except Exception:
                    pass  # Not actually gzip, use as-is

            # Try multiple encodings
            for encoding in ['utf-8', 'gbk', 'gb2312', 'latin-1']:
                try:
                    return content.decode(encoding)
                except (UnicodeDecodeError, LookupError):
                    continue

            return content.decode('utf-8', errors='replace')

        except (requests.exceptions.SSLError, requests.exceptions.ConnectionError) as e:
            last_error = e
            if attempt < max_retries - 1:
                wait_time = 2 * (attempt + 1)
                print(f"  [!] Connection error, retrying in {wait_time}s... ({attempt + 1}/{max_retries})")
                time.sleep(wait_time)
            continue

        except requests.exceptions.Timeout as e:
            last_error = e
            if attempt < max_retries - 1:
                wait_time = 5 * (attempt + 1)
                print(f"  [!] Timeout, retrying in {wait_time}s... ({attempt + 1}/{max_retries})")
                time.sleep(wait_time)
            continue

        except requests.exceptions.RequestException as e:
            last_error = e
            break

    raise last_error


def send_encrypted_request(plaintext: str, key_version: int = 2) -> str:
    """
    Send an encrypted request to the OTA server.

    Args:
        plaintext: Request plaintext
        key_version: Key version (1 or 2)

    Returns:
        Decrypted response string
    """
    jvq_param = encrypt_to_jvq(plaintext, key_version)
    url = BASE_URL + UPDATE_ENDPOINT
    response = http_post(url, "jvq_param=" + jvq_param)

    if not response.startswith(("ACw", "ACo")):
        return "[Error] " + response

    return decrypt_response(response, key_version)


def request_redir_post(plaintext: str, key_version: int = 2) -> str:
    """
    Send a redirect POST request.

    Args:
        plaintext: Request plaintext
        key_version: Key version (1 or 2)

    Returns:
        Decrypted response string
    """
    jvq_param = encrypt_to_jvq(plaintext, key_version)
    url = BASE_URL + REDIR_ENDPOINT
    response = http_post(url, "jvq_param=" + jvq_param)

    if not response.startswith(("ACw", "ACo")):
        return "[Error] " + response

    return decrypt_response(response, key_version)


# ============================================================
# Utility Functions
# ============================================================

def generate_imei() -> str:
    """Generate a random 15-digit IMEI string."""
    return "".join(str(random.randint(0, 9)) for _ in range(15))


def extract_json_str(json_str: str, key: str) -> str:
    """
    Extract a string value from JSON using simple pattern matching.

    Args:
        json_str: JSON string
        key: Key pattern to search for (e.g., 'pk":"')

    Returns:
        Extracted value or "(Not found)"
    """
    idx = json_str.find(key)
    if idx < 0:
        return "(Not found)"

    start = idx + len(key)
    end = json_str.find('"', start)

    if end < 0:
        end = json_str.find(",", start)
        if end < 0:
            end = json_str.find("}", start)
        return json_str[start:end].strip()

    return json_str[start:end]


def extract_pk_url(update_json: str) -> Optional[str]:
    """
    Extract download URL from update response.

    Args:
        update_json: Update response JSON string

    Returns:
        Download URL or None
    """
    pk_url = extract_json_str(update_json, 'pk":"')
    return None if pk_url == "(Not found)" else pk_url


def join_params(params: Dict[str, str]) -> str:
    """
    Join parameters into URL query string format.

    Args:
        params: Parameter dictionary

    Returns:
        Joined parameter string
    """
    return "&".join(f"{k}={v}" for k, v in params.items())


def normalize_json_url(url: Optional[str]) -> Optional[str]:
    """Normalize JSON-escaped URL."""
    if url is None:
        return None
    return url.replace("\\/", "/")


def parse_size(size_str: str) -> int:
    """Parse size string to integer."""
    try:
        return int(size_str)
    except (ValueError, TypeError):
        return 0


# ============================================================
# Request Building
# ============================================================

def build_request_params(config: DeviceConfig) -> Dict[str, str]:
    """
    Build request parameters for OTA query.

    Args:
        config: Device configuration

    Returns:
        Parameter dictionary
    """
    is_phone = config.device_type == "phone"

    # Derived values
    hw_ver = config.model_sw_ver + "MA"

    if ".W" in config.sw_version:
        full_sw_version = config.sw_version + ".V000L1"
        full_ver = f"{config.model_sw_ver}_A_{config.sw_version}.V000L1"
        version_long = f"{config.model_sw_ver}_N_{config.model_sw_ver}MA_{config.sw_version}.V000L1"
    else:
        full_sw_version = config.sw_version
        full_ver = f"{config.model_sw_ver}_A_{config.sw_version}"
        version_long = f"{config.model_sw_ver}_N_{config.model_sw_ver}MA_{config.sw_version}"

    ts = time.strftime("%y_%m_%d-%H_%M_%S")
    elapsedtime = (
        140000 + random.randint(0, 80000)
        if is_phone
        else 2000000 + random.randint(0, 500000)
    )
    is_full = 1 if config.is_full else 0
    imei = generate_imei()

    # Common parameters
    params = {
        "vgcNewActiveVer": "",
        "nt": "WIFI",
        "vgcSwVer": "1.1.1",
        "fullVer": full_ver,
        "emmcid": "",
        "sm1": "null",
        "sm2": "null",
        "model": config.model_sw_ver,
        "hasVgc": 1,
        "vgcNewPassiveVer": "",
        "ch": "N",
        "gn": 0,
        "newActiveVer": "",
        "version": version_long,
        "st2": 0,
        "cu": "N",
        "srm2": 0,
        "srm1": 0,
        "cy": "CN-ZH",
        "sn2": "null",
        "ne": "null",
        "sn1": "null",
        "public_model": config.device_model,
        "newPassiveVer": "",
        "hwVer": hw_ver,
        "swVer": full_sw_version,
        "language": "zh_CN",
        "isMan": 1,
        "isFull": is_full,
        "protocalversion": "1.0",
        "checkTrige": "MANUL",
        "isstlifeover": "false",
        "hwFingerprint": "",
    }

    # Device-specific parameters
    if is_phone:
        params.update({
            "vgcCu": "V000",
            "sf": 1,
            "si": "null",
            "dType": "phone",
            "s_n": "null",
            "elapsedtime": elapsedtime,
            "st1": 100000 + random.randint(0, 60000),
            "imei": imei,
            "ms": 0,
            "mtype": "no",
            "radiotype": "L",
        })
    else:
        params.update({
            "romVersion": f"Funtouch {config.android_ver}.0",
            "occurTime": ts,
            "vgcCu": "NULL",
            "battery": 69,
            "sf": 0,
            "si": "",
            "oem": f"{config.model_sw_ver}_CN-ZH_FULL_SC_NULL",
            "dType": "tablet",
            "oemProjects": f"{config.model_sw_ver}+{config.model_sw_ver}B",
            "verName": "1.1.1.1",
            "elapsedtime": elapsedtime,
            "verCode": "000000001",
            "st1": 0,
            "snp": config.snp,
            "imei": "",
            "sdkVersion": 34,
            "isCharge": "false",
            "ms": -1,
            "mtype": "FULL_SC",
            "radiotype": "A",
        })

    return params


def parse_update_response(response: str) -> UpdateInfo:
    """
    Parse update response into UpdateInfo.

    Args:
        response: Decrypted response string

    Returns:
        Parsed UpdateInfo object
    """
    info = UpdateInfo(raw_response=response)

    info.version = extract_json_str(response, 'version":"')
    info.filename = extract_json_str(response, 'pkName":"')
    info.size = extract_json_str(response, 'pkLen":"')
    info.size_mb = parse_size(info.size) // 1048576 if info.size != "(Not found)" else 0
    info.changelog_url = extract_json_str(response, 'h5Url":"')

    # Extract download URL from pk field
    pk_url = extract_pk_url(response)
    if pk_url:
        try:
            query_start = pk_url.find("?")
            if query_start >= 0:
                redir_params = pk_url[query_start + 1:]
                redir_response = request_redir_post(redir_params)
                if '"data":"' in redir_response:
                    data_start = redir_response.find('"data":"') + 8
                    data_end = redir_response.find('"', data_start)
                    info.download_url = normalize_json_url(
                        redir_response[data_start:data_end]
                    )
        except Exception as e:
            print(f"  [!] Failed to get redirect URL: {e}")

    return info


# ============================================================
# Display Functions
# ============================================================

def print_update_info(info: UpdateInfo) -> None:
    """Print update information in formatted output."""
    print("\n=== Update Information ===")

    if info.version and info.version != "(Not found)":
        print(f"  Version: {info.version}")

    if info.filename and info.filename != "(Not found)":
        print(f"  Filename: {info.filename}")

    if info.size and info.size != "(Not found)":
        print(f"  Size: {info.size} bytes ({info.size_mb} MB)")

    if info.download_url:
        print(f"  Download URL: {info.download_url}")

    if info.changelog_url and info.changelog_url != "(Not found)":
        print(f"  ChangeLog URL: {info.changelog_url}")


# ============================================================
# Main Query Function
# ============================================================

def query_ota_update(config: DeviceConfig) -> Optional[UpdateInfo]:
    """
    Query OTA update for a device.

    Args:
        config: Device configuration

    Returns:
        UpdateInfo if successful, None otherwise
    """
    # Build request parameters
    params = build_request_params(config)
    raw_params = join_params(params)

    print(f"  Device: {config.device_type} | {config.device_model} / {config.model_sw_ver}")
    print(f"  Base Version: {config.sw_version}")

    if config.verbose:
        print(f"  Raw Request Params: {raw_params}")

    # Send encrypted request
    try:
        response = send_encrypted_request(raw_params)
    except Exception as e:
        print(f"  [!] Request failed: {e}")
        return None

    if config.verbose:
        print(f"\n  Raw Update Response: {response}")

    # Check for errors
    if response.startswith("[Error]"):
        print(f"  {response}")
        return None

    # Parse response
    info = parse_update_response(response)
    print_update_info(info)

    return info


# ============================================================
# CLI Entry Point
# ============================================================

def print_usage():
    """Print usage examples when no arguments provided."""
    print("""
Usage: python vivo_ota_tracker.py [options]

Required arguments:
  -t, --device-type   Device type: phone or tablet
  -m, --model         Software model code (e.g., PD2408, DPD2106)
  -d, --device        Device model (e.g., V2408A, PA2170)
  -v, --version       Current software version (e.g., 16.1.16.5.W10, 8.7.22)
  -a, --android-ver   Android/OriginOS major version (e.g., 16, 14)
  --isfull            Full package flag: true or false

Optional arguments:
  --snp               Serial number (default: A0000000000000A)
  --verbose           Print raw request/response for debugging

Examples:
  # Phone query (full package)
  python vivo_ota_tracker.py -t phone -m PD2408 -d V2408A -v 16.1.16.5.W10 -a 16 --isfull true

  # Tablet query (delta package)
  python vivo_ota_tracker.py -t tablet -m DPD2106 -d PA2170 -v 8.7.22 -a 14 --isfull false --verbose
""")


def parse_args() -> argparse.Namespace:
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(
        description="Vivo OTA Tracker - Query OTA update information for Vivo devices",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Phone query (full package)
  %(prog)s -t phone -m PD2408 -d V2408A -v 16.1.16.5.W10 -a 16 --isfull true

  # Tablet query (delta package)
  %(prog)s -t tablet -m DPD2106 -d PA2170 -v 8.7.22 -a 14 --isfull false --verbose
        """,
    )

    parser.add_argument(
        "-t", "--device-type",
        required=True,
        choices=["phone", "tablet"],
        help="Device type",
    )
    parser.add_argument(
        "-m", "--model",
        required=True,
        help="Software model code (e.g., PD2408, DPD2106)",
    )
    parser.add_argument(
        "-d", "--device",
        required=True,
        help="Device model (e.g., V2408A, PA2170)",
    )
    parser.add_argument(
        "-v", "--version",
        required=True,
        help="Current software version (e.g., 16.1.16.5.W10, 8.7.22)",
    )
    parser.add_argument(
        "-a", "--android-ver",
        required=True,
        type=int,
        help="Android/OriginOS major version (e.g., 16, 14)",
    )
    parser.add_argument(
        "--isfull",
        required=True,
        choices=["true", "false"],
        help="Full package flag: true or false",
    )
    parser.add_argument(
        "--snp",
        default="A0000000000000A",
        help="Serial number (default: A0000000000000A)",
    )
    parser.add_argument(
        "--verbose",
        action="store_true",
        help="Print raw request/response for debugging",
    )

    return parser.parse_args()


def main() -> int:
    """Main entry point."""
    # Show usage if no arguments provided
    if len(sys.argv) == 1:
        print_usage()
        return 0

    args = parse_args()

    # Build configuration
    config = DeviceConfig(
        device_type=args.device_type,
        model_sw_ver=args.model,
        device_model=args.device,
        sw_version=args.version,
        android_ver=args.android_ver,
        snp=args.snp,
        is_full=args.isfull == "true",
        verbose=args.verbose,
    )

    # Query OTA update
    info = query_ota_update(config)

    if info is None:
        return 1

    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("\n  [!] Interrupted by user.")
        sys.exit(130)
    except Exception as e:
        print(f"\n  [!] Fatal error: {e}")
        sys.exit(1)
