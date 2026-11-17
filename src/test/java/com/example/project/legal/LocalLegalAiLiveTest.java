package com.example.project.legal;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@EnabledIfSystemProperty(named="legal.ai.smoke",matches="true")
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:local-ai-live;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","legal.ai.enabled=true","legal.ai.provider=local"})
@AutoConfigureMockMvc
class LocalLegalAiLiveTest {
    @Autowired MockMvc mvc; @Autowired NotebookService notebooks;
    @DynamicPropertySource static void localToken(DynamicPropertyRegistry registry) throws Exception {
        String token=Files.readString(Path.of(System.getProperty("legal.ai.smoke-token-file"))).strip();
        registry.add("legal.ai.local-token",()->token);
    }
    @Test void runningTrainedModelAnswersGeneralAndNotebookQuestions() throws Exception {
        mvc.perform(post("/legal-ai").with(user("local-smoke")).with(csrf()).param("question","월급이 제대로 들어오지 않았습니다. 임금 지급 원칙과 확인할 기록을 알려주세요."))
            .andExpect(status().isOk()).andExpect(content().string(containsString("로컬 AI의 설명"))).andExpect(content().string(containsString("근로기준법 제43조")));
        mvc.perform(post("/legal-ai/chat").with(user("local-smoke")).with(csrf()).param("question","쇼핑몰에서 산 물건을 반품하려면 어떤 조건을 확인하나요?"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.generated").value(true))
            .andExpect(jsonPath("$.sources[0].id").value("online-withdrawal"));
        var book=notebooks.create("local-smoke","임차권등기 판례 검토","주거",null);
        mvc.perform(post("/legal-ai").with(user("local-smoke")).with(csrf()).param("question","임대차가 끝났고 보증금을 못 받았다는 사례에서 어떤 사실을 확인해야 하나요?")
            .param("notebookId",book.id.toString()).param("consent","true"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("로컬 AI의 설명"))).andExpect(content().string(containsString("임차권등기명령"))).andExpect(content().string(containsString("2005다4529")));
    }
}
