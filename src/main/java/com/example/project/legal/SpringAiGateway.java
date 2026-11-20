package com.example.project.legal;

import java.time.Duration;
import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="legal.ai.provider",havingValue="openai")
public class SpringAiGateway implements AiGateway {
    private final IntegrationSettings settings;
    public SpringAiGateway(IntegrationSettings settings) { this.settings = settings; }
    @Override public String explain(String system, String context) {
        var options = OpenAiChatOptions.builder().model(settings.value("legal.ai.model", "gpt-5-mini"))
            .apiKey(settings.value("legal.ai.api-key", "")).maxCompletionTokens(3000)
            .reasoningEffort("low").store(false).maxRetries(1).timeout(Duration.ofSeconds(60)).build();
        var model = OpenAiChatModel.builder().options(options).build();
        var response = model.call(new Prompt(List.of(new SystemMessage(system), new UserMessage(context))));
        return response.getResult().getOutput().getText();
    }
}
