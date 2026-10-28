package com.example.project.legal;
import org.springframework.context.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;
import java.security.*;
import javax.net.ssl.*;
@Configuration public class LawApiConfig {
 @Bean("lawApiClient") RestClient lawApiClient(){
  var builder=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NEVER);
  if(System.getProperty("os.name","").toLowerCase().contains("windows")){try{KeyStore store=KeyStore.getInstance("Windows-ROOT");store.load(null,null);var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(store);var ssl=SSLContext.getInstance("TLS");ssl.init(null,tm.getTrustManagers(),null);builder.sslContext(ssl);}catch(Exception e){throw new IllegalStateException("Windows 인증서 저장소를 사용할 수 없습니다.",e);}}
  var factory=new JdkClientHttpRequestFactory(builder.build());factory.setReadTimeout(Duration.ofSeconds(8));return RestClient.builder().requestFactory(factory).defaultHeader("Accept","application/json").build();
 }
}
