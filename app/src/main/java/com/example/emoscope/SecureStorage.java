package com.example.emoscope;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 基于 Android Keystore 的安全存储 — 无需外部依赖。
 * 使用 AES-256-GCM 加密，密钥存储在硬件安全区域 (TEE/StrongBox)。
 * 用于保护 API Key 和紧急联系人等敏感数据。
 */
public final class SecureStorage {

    private static final String KEYSTORE_ALIAS = "EmoScopeMasterKey";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final SharedPreferences prefs;

    public SecureStorage(Context context) {
        this.prefs = context.getSharedPreferences(
                Constants.PREFS_NAME + "_secure", Context.MODE_PRIVATE);
        ensureKeyExists();
    }

    /** 确保 AES 密钥已在 Keystore 中生成，若不存在则创建 */
    private void ensureKeyExists() {
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
            ks.load(null);
            if (!ks.containsAlias(KEYSTORE_ALIAS)) {
                generateKey();
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "SecureStorage: key check failed", e);
        }
    }

    /** 在 Android Keystore 中生成 AES-256 密钥 */
    private void generateKey() throws Exception {
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build();

        KeyGenerator kg = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        kg.init(spec);
        kg.generateKey();
        Log.i(Constants.TAG, "SecureStorage: AES-256 key generated");
    }

    /** 从 Android Keystore 获取密钥 */
    private SecretKey getKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        return (SecretKey) ks.getKey(KEYSTORE_ALIAS, null);
    }

    /** 加密字符串 → Base64(IV + ciphertext) */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return "";
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getKey());

            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // IV (12 bytes) + 密文 → Base64
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(Constants.TAG, "SecureStorage: encrypt failed", e);
            return ""; // 加密失败返回空，而非明文
        }
    }

    /** 解密 Base64(IV + ciphertext) → 原始字符串 */
    public String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.isEmpty()) return "";
        try {
            byte[] combined = Base64.decode(encryptedBase64, Base64.NO_WRAP);

            // 分离 IV (前12字节) 和 密文 (剩余)
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(Constants.TAG, "SecureStorage: decrypt failed", e);
            return "";
        }
    }

    /** 安全存储字符串值 */
    public void put(String key, String value) {
        String encrypted = encrypt(value);
        prefs.edit().putString(key, encrypted).apply();
    }

    /** 读取安全存储的字符串值 */
    public String get(String key, String defaultValue) {
        String encrypted = prefs.getString(key, null);
        if (encrypted == null) return defaultValue;
        String decrypted = decrypt(encrypted);
        return decrypted.isEmpty() ? defaultValue : decrypted;
    }

    /** Clear all encrypted values stored by EmoScope. */
    public void clear() {
        prefs.edit().clear().apply();
    }
}
