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
    private final SecretKeySpec key = new SecretKeySpec(sha256(System.getenv().getOrDefault("AES_ENCRYPTION_KEY", "local-development-key-change-me")), "AES");

    @Override public String convertToDatabaseColumn(String value) {
        if (value == null || value.isBlank()) return value;
        try {
            byte[] iv = new byte[IV_LENGTH]; RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
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
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("Unable to decrypt personal data", ex); }
    }
    private static byte[] sha256(String value) { try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); } catch (Exception ex) { throw new IllegalStateException(ex); } }
}
