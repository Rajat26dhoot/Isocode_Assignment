package com.companya.customs.config;

import com.companya.customs.service.Aes256AttributeConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;

@Configuration
public class EncryptionConfig {
    @Bean HibernatePropertiesCustomizer configureImporterContactEncryption(@Value("${AES_ENCRYPTION_KEY:}") String secret) {
        Aes256AttributeConverter.configureKey(secret);
        return properties -> { };
    }
}
