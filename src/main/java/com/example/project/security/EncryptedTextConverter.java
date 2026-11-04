package com.example.project.security;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
@Converter
public class EncryptedTextConverter implements AttributeConverter<String,String> {
    @Override public String convertToDatabaseColumn(String value){return DataCipher.encryptText(value);}
    @Override public String convertToEntityAttribute(String value){return DataCipher.decryptText(value);}
}
