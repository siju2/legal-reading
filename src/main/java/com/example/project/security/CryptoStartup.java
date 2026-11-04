package com.example.project.security;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class CryptoStartup {
    @Bean ApplicationRunner verifyEncryptionKey(){return args->DataCipher.verifyKey();}
}
