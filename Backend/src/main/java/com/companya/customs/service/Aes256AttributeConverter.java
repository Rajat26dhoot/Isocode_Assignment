package com.companya.customs.service;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM converter. Set AES_ENCRYPTION_KEY to a secret managed outside the application. */
@Converter
public class Aes256AttributeConverter implements AttributeConverter<String, String> {
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] BLOB_MARKER = new byte[] { 0x43, 0x53, 0x54, 0x31 };
    private static volatile SecretKeySpec key;
    public static void configureKey(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalStateException("AES_ENCRYPTION_KEY must contain at least 32 bytes");
        key = new SecretKeySpec(sha256(secret), "AES");
    }

    @Override public String convertToDatabaseColumn(String value) {
        if (value == null || value.isBlank()) return value;
        try {
            byte[] iv = new byte[IV_LENGTH]; RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception ex) { throw new IllegalStateException("Unable to encrypt personal data", ex); }
    }
    @Override public String convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) return value;
        try {
            byte[] payload = Base64.getDecoder().decode(value); byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH); System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("Unable to decrypt personal data", ex); }
    }
    private static byte[] sha256(String value) { try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private static SecretKeySpec key() { SecretKeySpec configured = key; if (configured == null) throw new IllegalStateException("AES_ENCRYPTION_KEY is not configured"); return configured; }

    public static byte[] encryptBytes(byte[] value) {
        try {
            byte[] iv = new byte[IV_LENGTH]; RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(value);
            return ByteBuffer.allocate(BLOB_MARKER.length + iv.length + encrypted.length).put(BLOB_MARKER).put(iv).put(encrypted).array();
        } catch (Exception ex) { throw new IllegalStateException("Unable to encrypt source document", ex); }
    }
    public static byte[] decryptBytes(byte[] value) {
        if (value == null || value.length < BLOB_MARKER.length || !MessageDigest.isEqual(BLOB_MARKER, java.util.Arrays.copyOf(value, BLOB_MARKER.length))) return value;
        try {
            ByteBuffer buffer = ByteBuffer.wrap(value); buffer.position(BLOB_MARKER.length);
            byte[] iv = new byte[IV_LENGTH]; buffer.get(iv); byte[] encrypted = new byte[buffer.remaining()]; buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH, iv));
            return cipher.doFinal(encrypted);
        } catch (Exception ex) { throw new IllegalStateException("Unable to decrypt source document", ex); }
    }
}
