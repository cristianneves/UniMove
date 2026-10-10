package com.unimove.shared.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Cifra o atributo ao gravar e decifra ao ler. Instanciado pelo Spring (o
 * Hibernate do Boot usa o container do Spring para converters), por isso
 * recebe a {@link FieldCrypto} por construtor.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final FieldCrypto crypto;

    public EncryptedStringConverter(FieldCrypto crypto) {
        this.crypto = crypto;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return crypto.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return crypto.decrypt(dbData);
    }
}
