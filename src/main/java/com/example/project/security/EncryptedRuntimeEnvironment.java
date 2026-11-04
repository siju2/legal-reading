package com.example.project.security;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.*;

/** Loads encrypted local secrets before datasource and HTTPS configuration. */
public class EncryptedRuntimeEnvironment implements EnvironmentPostProcessor,Ordered {
    @Override public int getOrder(){return Ordered.LOWEST_PRECEDENCE;}
    @Override public void postProcessEnvironment(ConfigurableEnvironment env,SpringApplication app){
        Path file=DataCipher.keyPath().toAbsolutePath().getParent().resolve("runtime.properties.enc");
        if(!Files.exists(file))return;
        try {
            Properties properties=new Properties();
            properties.load(new StringReader(new String(DataCipher.decryptFile(Files.readAllBytes(file)),java.nio.charset.StandardCharsets.UTF_8)));
            Map<String,Object> values=new HashMap<>();properties.forEach((k,v)->values.put(k.toString(),v));
            var source=new MapPropertySource("encryptedLocalRuntime",values);
            // Explicit test properties, command line and environment retain precedence.
            if(env.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))env.getPropertySources().addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,source);
            else env.getPropertySources().addLast(source);
        }catch(IOException e){throw new IllegalStateException("Encrypted runtime configuration unavailable",e);}
    }
}
