package com.example.project.legal;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="legal.ai.provider",havingValue="openai")
public class SpringAiGateway implements AiGateway {
    private final IntegrationSettings settings;
    public SpringAiGateway(IntegrationSettings settings) { this.settings = settings; }
    @Override public String explain(String system, String context) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        var api = OpenAiApi.builder().apiKey(settings.value("legal.ai.api-key", ""))
            .restClientBuilder(RestClient.builder().requestFactory(factory)).build();
        var options = OpenAiChatOptions.builder().model(settings.value("legal.ai.model", "gpt-5-mini"))
            .maxCompletionTokens(3000).reasoningEffort("low").store(false).build();
        var model = OpenAiChatModel.builder().openAiApi(api).defaultOptions(options)
            .retryTemplate(RetryTemplate.builder().maxAttempts(1).noBackoff().build()).build();
        var response = model.call(new Prompt(List.of(new SystemMessage(system), new UserMessage(context))));
        return response.getResult().getOutput().getText();
    }
}
