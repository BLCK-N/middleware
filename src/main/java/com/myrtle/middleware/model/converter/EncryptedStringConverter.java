package com.myrtle.middleware.model.converter;

import com.myrtle.middleware.utils.FieldEncryptor;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Encrypts a column on write and decrypts it on read, so no call site has to remember to do either.
 * Applied only to fields that genuinely need to be recoverable - see {@link FieldEncryptor}.
 *
 * <p>Spring Boot wires Hibernate to its bean container, so this converter is dependency-injected
 * rather than instantiated reflectively.
 */
@Component
@Converter
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final FieldEncryptor fieldEncryptor;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return fieldEncryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return fieldEncryptor.decrypt(dbData);
    }
}
