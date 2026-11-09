package com.companya.customs.service;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EncryptedDocumentBytesConverter implements AttributeConverter<byte[], byte[]> {
    @Override public byte[] convertToDatabaseColumn(byte[] value) { return value == null ? null : Aes256AttributeConverter.encryptBytes(value); }
    @Override public byte[] convertToEntityAttribute(byte[] value) { return value == null ? null : Aes256AttributeConverter.decryptBytes(value); }
}
