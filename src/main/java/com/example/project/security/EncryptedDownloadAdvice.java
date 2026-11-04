package com.example.project.security;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;

/** Encrypt attachments at the HTTP boundary; renderers remain usable in memory. */
@ControllerAdvice
public class EncryptedDownloadAdvice implements ResponseBodyAdvice<byte[]> {
    @Override public boolean supports(MethodParameter p,Class<? extends HttpMessageConverter<?>> type){return org.springframework.http.converter.ByteArrayHttpMessageConverter.class.isAssignableFrom(type);}
    @Override public byte[] beforeBodyWrite(byte[] body,MethodParameter p,MediaType media,Class<? extends HttpMessageConverter<?>> type,ServerHttpRequest request,ServerHttpResponse response){
        String disposition=response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        if(body==null||disposition==null||!disposition.startsWith("attachment;"))return body;
        String name=disposition.replaceFirst("(?<=\\.pdf|\\.json)(?=\\\")",".enc");
        response.getHeaders().set(HttpHeaders.CONTENT_DISPOSITION,name);
        response.getHeaders().setContentType(MediaType.APPLICATION_OCTET_STREAM);
        response.getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
        response.getHeaders().setCacheControl("no-store");
        return DataCipher.encryptFile(body);
    }
}
