package com.example.project.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name="legal.ai.provider",havingValue="local")
public class LocalAiGateway implements ReasoningAiGateway {
    private final IntegrationSettings settings;private final ObjectMapper json;
    private final HttpClient client=HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    public LocalAiGateway(IntegrationSettings settings,ObjectMapper json){this.settings=settings;this.json=json;}
    @Override public String explain(String system,String context) { return explain(system,context,true); }
    @Override public String explain(String system,String context,boolean deepAnalysis) {
        try {
            var token=settings.value("legal.ai.local-token","");
            if(token.isBlank())throw new IllegalStateException("Local API token missing");
            var body=Map.of("messages",List.of(Map.of("role","system","content",system),Map.of("role","user","content",context)),"thinking",deepAnalysis,"max_tokens",deepAnalysis?1024:512);
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:11435/chat")).timeout(Duration.ofSeconds(120))
                .header("Content-Type","application/json").header("X-Local-AI-Token",token).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
            try(var stream=response.body()) {
                byte[] bytes=stream.readNBytes(131073);
                if(response.statusCode()==429)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"로컬 AI가 답변 중입니다. 잠시 후 다시 시도해 주세요.");
                if(response.statusCode()!=200||bytes.length>131072)throw new IllegalStateException("Local model unavailable");
                String text=json.readTree(bytes).path("answer").asText("");
                if(text.isBlank())throw new IllegalStateException("Empty local answer");return text;
            }
        } catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable();}
          catch(ResponseStatusException e){throw e;}
          catch(Exception e){throw unavailable();}
    }
    private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"로컬 AI에 연결하지 못했습니다. 학습 모델 서버의 실행 상태를 확인해 주세요. 외부 AI로 대신 전송하지 않습니다.");}
}
