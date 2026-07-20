package com.vivo.ota;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.dvm.*;
import com.github.unidbg.linux.android.dvm.array.ArrayObject;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
import com.github.unidbg.memory.Memory;
import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkSigner;
import net.dongliu.apk.parser.bean.CertificateMeta;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;

/**
 * Vivo OTA Tracker
 * A tool for analyzing Vivo's OTA protocol.
 * 1. Decrypts HAR jvq_param requests and responses.
 * 2. Sends constructed requests to interact with the OTA server.
 */
public class VivoOtaTracker extends AbstractJni {

    private final AndroidEmulator emulator;
    private final VM vm;
    private final DvmClass sdkCipherNativeClass;
    private DvmObject<?> mockContextObj;
    private byte[] realPubKeyEncoded = null;
    private byte[] realCertData = null;
    private DalvikModule dm;

    static final String TOKEN_NATIVE = "jnisgmain_v2@com.bbk.updater";
    
    private static final String HARDCODED_CERT_BASE64 =
        "MIIEpTCCA42gAwIBAgIJALFTcw2KNSU5MA0GCSqGSIb3DQEBBQUAMIGTMQswCQYD" +
        "VQQGEwJDTjESMBAGA1UECBMJR3Vhbmdkb25nMRYwFAYDVQQHEw1Eb25nZ3VhbiBW" +
        "aWV3MQwwCgYDVQQKEwNCQksxDTALBgNVBAsTBElRT08xGTAXBgNVBAMTEGJia21v" +
        "YmlsZS5jb20uY24xIDAeBgkqhkiG9w0BCQEWEWJia3RlbEBiYmt0ZWwuY29tMB4X" +
        "DTEyMDkyNTE0MTk0M1oXDTQwMDIxMTE0MTk0M1owgZMxCzAJBgNVBAYTAkNOMRIw" +
        "EAYDVQQIEwlHdWFuZ2RvbmcxFjAUBgNVBAcTDURvbmdndWFuIFZpZXcxDDAKBgNV" +
        "BAoTA0JCSzENMAsGA1UECxMESVFPTzEZMBcGA1UEAxMQYmJrbW9iaWxlLmNvbS5j" +
        "bjEgMB4GCSqGSIb3DQEJARYRYmJrdGVsQGJia3RlbC5jb20wggEgMA0GCSqGSIb3" +
        "DQEBAQUAA4IBDQAwggEIAoIBAQCsfq1NAQO0ozLJgT4T+wFockr5RMA2WHvQcltj" +
        "KFDbI2MFSN4QwUqHbcUFMRZsiKu1c3fiGTNk+Py8fhQu3GMY6fv1rJtv9qaHEjtk" +
        "WleoemiH1z5rAr9Kipr6/jDwYbijDuPAK8XgNUCRWCZi1ci98Ve11wUQvcoYp1mz" +
        "gHuWi9eCtkZZyz4Ci47XmV5KfJGYajOMHSURjC1kzbdayUyUAskXiLqbmTT5NlPI" +
        "yB7xcGNGUfaXpyLR+Pg0xlKQoweqgVZ2I5Nems1E9aEckTuTVTmsUqSAKvgDQaFt" +
        "ksRXudAnwTRlEt5qjuiFxTD7Dzu3AY7PpsIIZFICcG/DaPOJAgEDo4H7MIH4MB0G" +
        "A1UdDgQWBBSw7I/j7ruoUyXz7Z+O03SdoYNzIzCByAYDVR0jBIHAMIG9gBSw7I/j" +
        "7ruoUyXz7Z+O03SdoYNzI6GBmaSBljCBkzELMAkGA1UEBhMCQ04xEjAQBgNVBAgT" +
        "CUd1YW5nZG9uZzEWMBQGA1UEBxMNRG9uZ2d1YW4gVmlldzEMMAoGA1UEChMDQkJL" +
        "MQ0wCwYDVQQLEwRJUU9PMRkwFwYDVQQDExBiYmttb2JpbGUuY29tLmNuMSAwHgYJ" +
        "KoZIhvcNAQkBFhFiYmt0ZWxAYmJrdGVsLmNvbYIJALFTcw2KNSU5MAwGA1UdEwQF" +
        "MAMBAf8wDQYJKoZIhvcNAQEFBQADggEBAH0tVFLHuJb1QT6CQrRFyuQRfZOCSlcl" +
        "O+lRokupKoMHISFRhLU1c/G4OFJrDEHqUZtJaFL8oy4Z9of+irsTx8BGGwXRarWF" +
        "qNVMVjdXXO0vHj9gKSZwSxbfoUkIEkFkCSmPA/U7+j/zls/y2vEu/HV4Y64C9kWj" +
        "mZlRqVe2A4Pn9ue276baHqF9pljaEqwQ+qLVzW7MVAND3vOCkzQtmc/EIN8fQ3pp" +
        "vyTpy9drhVBfNoPiA4qBn2L7RAYcEi7B+SBTPntpfPJ+iw8qra2MM/wjw9/7TAml" +
        "7UxMKbxf/V2+/ROj1mkTLWxKBnwTU+BY4k0W/i0YI7Zvqi1A0G9cABA=";

    // ============================================================
    // JNI Hooks
    // ============================================================

    @Override
    public DvmObject<?> callStaticObjectMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        if (signature.contains("SDKCipherNative->cCert"))
            return new ArrayObject(vm.resolveClass("android/content/pm/Signature").newObject("official_cert"));
        if (signature.contains("Base64->encode")) {
            byte[] src = (byte[]) vaList.getObjectArg(0).getValue();
            int flags = vaList.getIntArg(1);
            if (flags == 0) {
                String b64 = java.util.Base64.getEncoder().encodeToString(src);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < b64.length(); i += 76)
                    sb.append(b64, i, Math.min(i + 76, b64.length())).append('\n');
                return new ByteArray(vm, sb.toString().getBytes(StandardCharsets.US_ASCII));
            }
            return new ByteArray(vm, java.util.Base64.getEncoder().encode(src));
        }
        if (signature.contains("VMRuntime->getRuntime"))
            return vm.resolveClass("dalvik/system/VMRuntime").newObject(null);
        if (signature.contains("ActivityThread->currentActivityThread"))
            return vm.resolveClass("android/app/ActivityThread").newObject(null);
        return super.callStaticObjectMethodV(vm, dvmClass, signature, vaList);
    }

    @Override
    public DvmObject<?> callObjectMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        if (signature.contains("getApplication")) return mockContextObj;
        if (signature.contains("getPackageName")) return new StringObject(vm, "com.bbk.updater");
        if (signature.contains("getPackageCodePath"))
            return new StringObject(vm, "/data/app/com.bbk.updater-1/base.apk");
        if (signature.contains("getSystemService"))
            return vm.resolveClass("android/os/PowerManager").newObject(null);

        if (signature.contains("Signature->toByteArray"))
            return new ByteArray(vm, realCertData != null ? realCertData : realPubKeyEncoded);
        if (signature.contains("Signature->getPublicKey"))
            return vm.resolveClass("java/security/PublicKey").newObject(null);
        if (signature.contains("PublicKey->getEncoded"))
            return new ByteArray(vm, realPubKeyEncoded);

        if (signature.contains("NativeRequest->get")) {
            Map<String, Object> map = (Map<String, Object>) dvmObject.getValue();
            if (signature.contains("getData")) {
                byte[] data = (byte[]) map.get("data");
                return new ByteArray(vm, data != null ? data : new byte[0]);
            }
            if (signature.contains("getIV") || signature.contains("getGaloisMAC") || signature.contains("getNonce"))
                return new ByteArray(vm, new byte[16]);
            return new ByteArray(vm, new byte[0]);
        }
        return super.callObjectMethodV(vm, dvmObject, signature, vaList);
    }

    @Override
    public DvmObject<?> getObjectField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        if (signature.contains("PackageInfo->signatures"))
            return new ArrayObject(vm.resolveClass("android/content/pm/Signature").newObject("official_cert"));
        return super.getObjectField(vm, dvmObject, signature);
    }

    @Override
    public int callIntMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        if (signature.contains("NativeRequest->get")) {
            Map<String, Object> map = (Map<String, Object>) dvmObject.getValue();
            if (signature.contains("getOperateType")) return (int) map.getOrDefault("operateType", 0);
            if (signature.contains("getEncryptType")) return (int) map.getOrDefault("encryptType", 0);
            if (signature.contains("getKeyVersion")) return (int) map.getOrDefault("keyVersion", 1);
        }
        return super.callIntMethodV(vm, dvmObject, signature, vaList);
    }

    @Override
    public DvmObject<?> newObjectV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        if (signature.contains("NativeResponse-><init>")) {
            Map<String, Object> map = new HashMap<>();
            map.put("err", -9999);
            return dvmClass.newObject(map);
        }
        return super.newObjectV(vm, dvmClass, signature, vaList);
    }

    @Override
    public void callVoidMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) {
        if (signature.contains("NativeResponse->set")) {
            Map<String, Object> map = (Map<String, Object>) dvmObject.getValue();
            if (signature.contains("setErr")) map.put("err", vaList.getIntArg(0));
            else if (signature.contains("setOutput"))
                map.put("output", vaList.getObjectArg(0) != null ? vaList.getObjectArg(0).getValue() : null);
            else if (signature.contains("setKeyVersion")) map.put("keyVersion", vaList.getIntArg(0));
            else if (signature.contains("setPubicKeyHash"))
                map.put("pubicKeyHash", vaList.getObjectArg(0) != null ? vaList.getObjectArg(0).getValue() : null);
            return;
        }
        super.callVoidMethodV(vm, dvmObject, signature, vaList);
    }

    @Override
    public int getStaticIntField(BaseVM vm, DvmClass dvmClass, String signature) {
        if (signature.contains("currentProtectionMode")) return 0;
        if (signature.contains("soEncryptMaxLen")) return 102400;
        return super.getStaticIntField(vm, dvmClass, signature);
    }

    @Override
    public boolean getStaticBooleanField(BaseVM vm, DvmClass dvmClass, String signature) { return false; }
    @Override
    public boolean callBooleanMethodV(BaseVM vm, DvmObject<?> dvmObject, String signature, VaList vaList) { return true; }

    // ============================================================
    // Initialization
    // ============================================================

    public VivoOtaTracker() {
        emulator = AndroidEmulatorBuilder.for64Bit().setProcessName("com.bbk.updater").build();
        Memory memory = emulator.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23));

        vm = emulator.createDalvikVM();

        try {
            byte[] certData = java.util.Base64.getDecoder().decode(HARDCODED_CERT_BASE64.replace("\n", "").replace("\r", ""));
            this.realCertData = certData;
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certData));
            realPubKeyEncoded = cert.getPublicKey().getEncoded();
        } catch (Exception e) {
            System.err.println("[!] Certificate parsing failed: " + e.getMessage());
        }

        vm.setJni(this);
        vm.setVerbose(false);

        vm.resolveClass("android/content/ContextWrapper", vm.resolveClass("android/content/Context"));
        mockContextObj = vm.resolveClass("android/content/ContextWrapper").newObject(null);
        vm.addLocalObject(mockContextObj);

        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(originalOut) {
            @Override public void println(String s) {
                if (s != null && s.contains("_V_vivoseckey")) return;
                super.println(s);
            }
        });

        File soFile = new File("libs/libvivoseckey_n4.so");
        if (!soFile.exists()) soFile = new File("libs/libvivoseckey.so");
        this.dm = vm.loadLibrary(soFile, false);
        dm.callJNI_OnLoad(emulator);

        sdkCipherNativeClass = vm.resolveClass("com/vivo/seckeysdk/utils/SDKCipherNative");

        Boolean initResult = sdkCipherNativeClass.callStaticJniMethodBoolean(emulator,
                "init(Landroid/content/Context;)Z", mockContextObj);
        System.out.println("[*] Crypto engine initialization: " + (initResult ? "Success" : "Failed"));

        if (initResult) {
            Map<String, Object> uidReqMap = new HashMap<>();
            uidReqMap.put("operateType", 7);
            uidReqMap.put("encryptType", 0);
            DvmObject<?> uidRequestObj = vm.resolveClass("com/vivo/seckeysdk/utils/NativeRequest").newObject(uidReqMap);
            DvmObject<?> uidResultObj = sdkCipherNativeClass.callStaticJniMethodObject(emulator,
                    "execute(Lcom/vivo/seckeysdk/utils/NativeRequest;)Lcom/vivo/seckeysdk/utils/NativeResponse;",
                    uidRequestObj);
            Map<String, Object> uidResultMap = (Map<String, Object>) uidResultObj.getValue();
            int uidErr = (int) uidResultMap.getOrDefault("err", -1);
            System.out.println("[*] Key engine status: " + (uidErr == 0 ? "Ready" : "Error (err=" + uidErr + ")"));
        }

        System.setOut(originalOut);
    }

    public void destroy() {
        if (emulator != null) {
            try { emulator.close(); } catch (Exception ignored) {}
        }
    }

    // ============================================================
    // Native Crypto Operations
    // ============================================================

    public byte[] nativeEncrypt(byte[] plaintext) {
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put("operateType", 1);
        reqMap.put("encryptType", 1);
        reqMap.put("keyVersion", 2);
        reqMap.put("data", plaintext);

        DvmObject<?> requestObj = vm.resolveClass("com/vivo/seckeysdk/utils/NativeRequest").newObject(reqMap);
        DvmObject<?> resultObj = sdkCipherNativeClass.callStaticJniMethodObject(emulator,
                "execute(Lcom/vivo/seckeysdk/utils/NativeRequest;)Lcom/vivo/seckeysdk/utils/NativeResponse;",
                requestObj);

        Map<String, Object> resultMap = (Map<String, Object>) resultObj.getValue();
        return (byte[]) resultMap.get("output");
    }

    public byte[] nativeDecrypt(byte[] ciphertext) {
        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put("operateType", 2);
        reqMap.put("encryptType", 1);
        reqMap.put("keyVersion", 2);
        reqMap.put("data", ciphertext);

        DvmObject<?> requestObj = vm.resolveClass("com/vivo/seckeysdk/utils/NativeRequest").newObject(reqMap);
        DvmObject<?> resultObj = sdkCipherNativeClass.callStaticJniMethodObject(emulator,
                "execute(Lcom/vivo/seckeysdk/utils/NativeRequest;)Lcom/vivo/seckeysdk/utils/NativeResponse;",
                requestObj);

        Map<String, Object> resultMap = (Map<String, Object>) resultObj.getValue();
        return (byte[]) resultMap.get("output");
    }

    // ============================================================
    // Protocol Utils
    // ============================================================

    public byte[] buildProtocolPackage(int type, int keyVersion, String token, byte[] data) throws Exception {
        byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
        int headerTotalLen = 16 + tokenBytes.length;

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        CRC32 crc32 = new CRC32();
        ByteArrayOutputStream headerFieldBos = new ByteArrayOutputStream();
        DataOutputStream headerFieldDos = new DataOutputStream(headerFieldBos);
        headerFieldDos.writeShort(1);
        headerFieldDos.writeByte(tokenBytes.length);
        headerFieldDos.write(tokenBytes);
        headerFieldDos.writeShort(keyVersion);
        headerFieldDos.writeByte(type);
        crc32.update(headerFieldBos.toByteArray());

        dos.writeShort(headerTotalLen);
        dos.writeLong(crc32.getValue());
        dos.write(headerFieldBos.toByteArray());
        dos.write(data);
        return bos.toByteArray();
    }

    public byte[] extractEncryptedBody(byte[] fullPackage) throws Exception {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(fullPackage));
        int headerLen = dis.readUnsignedShort();
        int payloadLen = fullPackage.length - headerLen;
        byte[] payload = new byte[payloadLen];
        System.arraycopy(fullPackage, headerLen, payload, 0, payloadLen);
        return payload;
    }

    public String base64UrlEncode(byte[] data) {
        return java.util.Base64.getUrlEncoder().encodeToString(data);
    }

    public byte[] base64UrlDecode(String data) throws Exception {
        String decoded = URLDecoder.decode(data, "UTF-8").replace('-', '+').replace('_', '/');
        int pad = 4 - decoded.length() % 4;
        if (pad != 4) {
            StringBuilder sb = new StringBuilder(decoded);
            for (int i = 0; i < pad; i++) sb.append('=');
            decoded = sb.toString();
        }
        return java.util.Base64.getDecoder().decode(decoded);
    }

    public String encryptToJvq(String plaintext) throws Exception {
        byte[] encrypted = nativeEncrypt(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] pkg = buildProtocolPackage(5, 2, TOKEN_NATIVE, encrypted);
        return base64UrlEncode(pkg);
    }

    public String decryptResponse(String responseB64) throws Exception {
        byte[] fullPackage = base64UrlDecode(responseB64);
        byte[] encryptedBody = extractEncryptedBody(fullPackage);
        byte[] decrypted = nativeDecrypt(encryptedBody);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    // ============================================================
    // HTTP Operations
    // ============================================================

    public String httpPost(String urlString, String body) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("User-Agent", "okhttp/4.3.23");
        conn.setRequestProperty("Host", "sysupgrade.vivo.com.cn");
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.setRequestProperty("Accept-Encoding", "gzip");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if ("gzip".equals(conn.getContentEncoding())) is = new GZIPInputStream(is);

        StringBuilder resp = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) resp.append(line);
        }
        return resp.toString();
    }

    public String sendFreshEncryptedRequest(String plaintext) throws Exception {
        String jvqParam = encryptToJvq(plaintext);
        String response = httpPost("https://sysupgrade.vivo.com.cn/vgc/v2/getVgcAndPatch.do?", "jvq_param=" + jvqParam);
        if (!response.startsWith("ACw") && !response.startsWith("ACo")) return "[Error] " + response;
        return decryptResponse(response);
    }

    public String requestRedirPost(String plaintext) throws Exception {
        String jvqParam = encryptToJvq(plaintext);
        String response = httpPost("https://sysupgrade.vivo.com.cn/pk/redirPost.do", "jvq_param=" + jvqParam);
        if (!response.startsWith("ACw") && !response.startsWith("ACo")) return "[Error] " + response;
        return decryptResponse(response);
    }

    public String extractPkUrl(String updateJson) {
        String pkUrl = extractJsonStr(updateJson, "pk\":\"");
        return pkUrl.equals("(Not found)") ? null : pkUrl;
    }

    public static void printDownloadInfo(String jsonStr) {
        try {
            String pkName = extractJsonStr(jsonStr, "pkName\":\"");
            String pkLen = extractJsonStr(jsonStr, "pkLen\":\"");
            String version = extractJsonStr(jsonStr, "version\":\"");

            System.out.println("\n=== Update Information ===");
            System.out.println("  Version: " + version);
            System.out.println("  Filename: " + pkName);
            System.out.println("  Size: " + pkLen + " bytes (" + (Long.parseLong(pkLen) / 1048576) + " MB)");
        } catch (Exception e) {
            System.err.println("  [!] Failed to parse update info.");
        }
    }

    private static String extractJsonStr(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0) return "(Not found)";
        int start = idx + key.length();
        int end = json.indexOf('"', start);
        if (end < 0) {
            end = json.indexOf(',', start);
            if (end < 0) end = json.indexOf('}', start);
            return json.substring(start, end).trim();
        }
        return json.substring(start, end);
    }

    // ============================================================
    // Main Entry Point
    // ============================================================

    public static void main(String[] args) throws Exception {
        System.out.println("==============================================");
        System.out.println("  Vivo OTA Tracker");
        System.out.println("==============================================");

        // --- Configuration ---

        // Device type: "tablet" or "phone"
        final String DEVICE_TYPE = "phone";

        // Software version number and device model (e.g., DPD2429, PD2314)
        final String MODEL_SW_VER = "PD2408";

        // Device model (e.g., PA2573, V2314A)
        final String DEVICE_MODEL = "V2408A";

        // Software version (e.g., 15.0.12.18.W10, 15.2.13.1.W10)
        // Note: The version number must not be lower than the current lowest version, otherwise the server will reject the request
        final String SW_VERSION = "16.1.16.5.W10";

        // FuntouchOS major version number
        // Correspondence: 13=OriginOS3, 14=OriginOS4, 15=OriginOS5
        final int ANDROID_VER = 16;
        // ---------------------

        final String SNP = "A0000000000000A";
        final String HW_VER = MODEL_SW_VER + "MA";

        // Full Package / Delta-ota Package
        final boolean IS_FULL = true;

        // Verbose for check raw
        final boolean verbose = false;

        String FULL_VER = SW_VERSION.contains(".W") ? MODEL_SW_VER + "_A_" + SW_VERSION + ".V000L1" : MODEL_SW_VER + "_A_" + SW_VERSION;
        String VERSION_LONG = SW_VERSION.contains(".W") ? MODEL_SW_VER + "_N_" + MODEL_SW_VER + "MA_" + SW_VERSION + ".V000L1" : MODEL_SW_VER + "_N_" + MODEL_SW_VER + "MA_" + SW_VERSION;

        boolean isPhone = "phone".equals(DEVICE_TYPE);
        VivoOtaTracker tracker = new VivoOtaTracker();

        String ts = new SimpleDateFormat("yy_MM_dd-HH_mm_ss").format(new Date());
        int elapsedtime = isPhone ? 140000 + new Random().nextInt(80000) : 2000000 + new Random().nextInt(500000);
        int isFull = IS_FULL ? 1 : 0;

        String rawParams;

        if (isPhone) {
            rawParams = "vgcNewActiveVer=&nt=WIFI&vgcSwVer=1.1.1&vgcCu=V000&fullVer=" + FULL_VER + "&sf=1&emmcid=&si=null&sm1=null&dType=phone&sm2=null&model=" + MODEL_SW_VER + "&hasVgc=1&vgcNewPassiveVer=&s_n=null&ch=N&gn=0&newActiveVer=&elapsedtime=" + elapsedtime + "&version=" + VERSION_LONG + "&st2=0&st1=" + (100000 + new Random().nextInt(60000)) + "&cu=N&srm2=0&srm1=0&cy=CN-ZH&sn2=null&ne=null&sn1=null&imei=000000000000000&public_model=" + DEVICE_MODEL + "&newPassiveVer=&hwVer=" + HW_VER + "&swVer=" + SW_VERSION + "&language=zh_CN&ms=0&mtype=no&radiotype=L&isMan=1&isFull=" + isFull + "&protocalversion=1.0&checkTrige=MANUL&isstlifeover=false&hwFingerprint=";
        } else {
            rawParams = "vgcNewActiveVer=&romVersion=Funtouch+" + ANDROID_VER + ".0&nt=WIFI&occurTime=" + ts + "&vgcSwVer=1.0.1&vgcCu=NULL&battery=69&fullVer=" + FULL_VER + "&sf=0&emmcid=&si=&oem=" + MODEL_SW_VER + "_CN-ZH_FULL_SC_NULL&sm1=null&dType=tablet&sm2=null&model=" + MODEL_SW_VER + "&hasVgc=1&vgcNewPassiveVer=&ch=N&gn=0&oemProjects=" + String.join("+", MODEL_SW_VER, MODEL_SW_VER + "B") + "&newActiveVer=&verName=1.1.1.1&elapsedtime=" + elapsedtime + "&version=" + VERSION_LONG + "&verCode=000000001&st2=0&st1=0&cu=N&srm2=0&snp=" + SNP + "&srm1=0&cy=CN-ZH&sn2=null&ne=&sn1=null&imei=&sdkVersion=34&isCharge=false&public_model=" + DEVICE_MODEL + "&newPassiveVer=&hwVer=" + HW_VER + "&swVer=" + SW_VERSION + "&language=zh_CN&ms=-1&mtype=FULL_SC&radiotype=A&isMan=1&isFull=" + isFull + "&protocalversion=1.0&checkTrige=MANUL&isstlifeover=false&hwFingerprint=";
        }

        System.out.println("  Device: " + DEVICE_TYPE + " | " + DEVICE_MODEL + " / " + MODEL_SW_VER);
        System.out.println("  Base Version: " + SW_VERSION);
        if (verbose) System.out.println("  Raw Request Params: " + rawParams);

        String result = tracker.sendFreshEncryptedRequest(rawParams);
        if (verbose) System.out.println("  Raw Update Response: " + result);
        printDownloadInfo(result);

        String pkUrl = tracker.extractPkUrl(result);
        if (pkUrl != null) {
            try {
                String pkNameValue = "";
                int nameStart = pkUrl.indexOf("name=");
                if (nameStart >= 0) {
                    pkNameValue = pkUrl.substring(nameStart + 5);
                    int ampIdx = pkNameValue.indexOf('&');
                    if (ampIdx > 0) pkNameValue = pkNameValue.substring(0, ampIdx);
                }

                String newTs = new SimpleDateFormat("yy_MM_dd-HH_mm_ss").format(new Date());
                String nonce = UUID.randomUUID().toString().replace("-", "");
                long timestamp = System.currentTimeMillis();
                int newElapsed = isPhone ? 140000 + new Random().nextInt(80000) : 2000000 + new Random().nextInt(500000);
                int timeToUptouch = isPhone ? 900000 + new Random().nextInt(100000) : 1700000 + new Random().nextInt(100000);

                String redirPlaintext;
                if (isPhone) {
                    redirPlaintext = "vgcNewActiveVer=&nt=WIFI&vgcSwVer=1.1.1&vgcCu=V000&fullVer=" + FULL_VER + "&sf=1&emmcid=&si=null&sm1=null&dType=phone&sm2=null&model=" + MODEL_SW_VER + "&hasVgc=1&vgcNewPassiveVer=&s_n=null&ch=N&gn=0&newActiveVer=&elapsedtime=" + newElapsed + "&version=" + VERSION_LONG + "&st2=0&st1=" + (100000 + new Random().nextInt(60000)) + "&cu=N&srm2=0&srm1=0&cy=CN-ZH&sn2=null&ne=null&sn1=null&imei=null&public_model=" + DEVICE_MODEL + "&newPassiveVer=&hwVer=" + HW_VER + "&swVer=" + SW_VERSION + "&language=zh_CN&ms=0&mtype=no&radiotype=L&name=" + pkNameValue + "&fileType=relative&fileLength=" + extractJsonStr(result, "pkLen\":\"") + "&packageType=patch&upversion=&dlrequest=0&downloadType=NORMAL_MANUAL_DOWNLOAD&isPacakgeActive=1&timeToUptouch=" + timeToUptouch + "&httpsSupport=1&isTrialVersion=false&retry=0&timeStamp=" + timestamp + "&nonce=" + nonce + "&hwFingerprint=";
                } else {
                    redirPlaintext = "vgcNewActiveVer=&romVersion=Funtouch+" + ANDROID_VER + ".0&nt=WIFI&occurTime=" + newTs + "&vgcSwVer=1.0.1&vgcCu=NULL&battery=69&fullVer=" + FULL_VER + "&sf=0&emmcid=&si=&oem=" + MODEL_SW_VER + "_CN-ZH_FULL_SC_NULL&sm1=null&dType=tablet&sm2=null&model=" + MODEL_SW_VER + "&hasVgc=1&vgcNewPassiveVer=&ch=N&gn=0&oemProjects=" + String.join("+", MODEL_SW_VER, MODEL_SW_VER + "B") + "&newActiveVer=&verName1.1.1.1&elapsedtime=" + newElapsed + "&version=" + VERSION_LONG + "&verCode=000000001&st2=0&st1=0&cu=N&srm2=0&snp=" + SNP + "&srm1=0&cy=CN-ZH&sn2=null&ne=&sn1=null&imei=&sdkVersion=34&isCharge=false&public_model=" + DEVICE_MODEL + "&newPassiveVer=&hwVer=" + HW_VER + "&swVer=" + SW_VERSION + "&language=zh_CN&ms=-1&mtype=FULL_SC&radiotype=A&name=" + pkNameValue + "&fileType=relative&fileLength=" + extractJsonStr(result, "pkLen\":\"") + "&packageType=patch&upversion=&dlrequest=0&downloadType=NORMAL_MANUAL_DOWNLOAD&isPacakgeActive=1&timeToUptouch=" + timeToUptouch + "&httpsSupport=1&isTrialVersion=false&retry=0&timeStamp=" + timestamp + "&nonce=" + nonce + "&hwFingerprint=";
                }
                String redirResult = tracker.requestRedirPost(redirPlaintext);
                if (verbose) System.out.println("  redir Response: " + redirResult);
                System.out.println("  Download URL: " + extractJsonStr(redirResult, "data\":\""));
            } catch (Exception e) {
                System.out.println("  [!] redirPost.do request failed.");
            }
        }
        tracker.destroy();
    }
}