package com.example.project.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;

/** Versioned AES-256-GCM envelope. Key creation is an explicit operator action. */
public final class DataCipher {
    public static final String PREFIX="LRG1:";
    private static final byte[] AAD="legal-reading/aes-gcm/v1".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM=new SecureRandom();
    private DataCipher(){}
    public static Path keyPath(){
        String configured=System.getProperty("legal.crypto.key-file",System.getenv("LEGAL_CRYPTO_KEY_FILE"));
        return configured==null||configured.isBlank()?Path.of(System.getProperty("user.home"),".legal-reading","security","data-key.bin"):Path.of(configured);
    }
    private static SecretKeySpec key(){
        try {byte[] bytes=Files.readAllBytes(keyPath());if(bytes.length!=32)throw new IllegalStateException("Encryption key must contain exactly 32 bytes");return new SecretKeySpec(bytes,"AES");}
        catch(java.io.IOException e){throw new IllegalStateException("Encryption key unavailable. Configure LEGAL_CRYPTO_KEY_FILE; never generate a replacement for an existing database.",e);}
    }
    public static void verifyKey(){key();}
    public static String rememberMeKey(){
        try {var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key().getEncoded(),"HmacSHA256"));return Base64.getEncoder().encodeToString(mac.doFinal("legal-reading/remember-me/v1".getBytes(StandardCharsets.US_ASCII)));}
        catch(java.security.GeneralSecurityException e){throw new IllegalStateException("Remember-me key unavailable",e);}
    }

    public static byte[] encrypt(byte[] plain){return crypt(Cipher.ENCRYPT_MODE,plain);}
    public static byte[] decrypt(byte[] envelope){return crypt(Cipher.DECRYPT_MODE,envelope);}
    private static byte[] crypt(int mode,byte[] input){
        try {
            byte[] nonce=new byte[12];byte[] payload=input;
            if(mode==Cipher.ENCRYPT_MODE)RANDOM.nextBytes(nonce);
            else {if(input.length<28)throw new IllegalArgumentException("Invalid encrypted envelope");System.arraycopy(input,0,nonce,0,12);payload=Arrays.copyOfRange(input,12,input.length);}
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,key(),new GCMParameterSpec(128,nonce));cipher.updateAAD(AAD);
            byte[] result=cipher.doFinal(payload);
            if(mode==Cipher.DECRYPT_MODE)return result;
            byte[] output=Arrays.copyOf(nonce,nonce.length+result.length);System.arraycopy(result,0,output,nonce.length,result.length);return output;
        }catch(java.security.GeneralSecurityException e){throw new IllegalStateException("Encrypted data authentication failed",e);}
    }
    public static String encryptText(String value){return value==null?null:PREFIX+Base64.getEncoder().encodeToString(encrypt(value.getBytes(StandardCharsets.UTF_8)));}
    public static String decryptText(String value){
        if(value==null)return null;
        if(!value.startsWith(PREFIX))throw new IllegalStateException("Unencrypted data detected: run the offline migration before starting the server");
        return new String(decrypt(Base64.getDecoder().decode(value.substring(PREFIX.length()))),StandardCharsets.UTF_8);
    }
    public static byte[] encryptFile(byte[] value){return encryptText(Base64.getEncoder().encodeToString(value)).getBytes(StandardCharsets.US_ASCII);}
    public static byte[] decryptFile(byte[] value){return Base64.getDecoder().decode(decryptText(new String(value,StandardCharsets.US_ASCII)));}
}
