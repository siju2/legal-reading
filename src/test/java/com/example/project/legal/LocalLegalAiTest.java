package com.example.project.legal;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:local-law-test;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","legal.ai.enabled=true","legal.ai.provider=local","legal.ai.daily-limit=2"})
@AutoConfigureMockMvc
class LocalLegalAiTest {
    @Autowired MockMvc mvc; @Autowired NotebookService notebooks; @Autowired AiUsageRepository usage;
    @MockitoBean AiGateway ai;
    @BeforeEach void setup(){reset(ai);usage.deleteAll();when(ai.explain(anyString(),anyString())).thenReturn("쉬운 설명\n임금 지급 원칙을 확인하세요. [1] <script>alert(1)</script>");}
    @Test void answersGeneralQuestionsWithServerControlledSourcesAndEscapedText() throws Exception {
        mvc.perform(get("/legal-ai")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/legal-ai").with(user("alice")).param("question","월급 지급 원칙")).andExpect(status().isForbidden());
        mvc.perform(post("/legal-ai").with(user("alice")).with(csrf()).param("question","월급 지급 원칙"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("근로기준법 제43조")))
            .andExpect(content().string(containsString("&lt;script&gt;"))).andExpect(header().string("Cache-Control","no-store"));
        var prompt=org.mockito.ArgumentCaptor.forClass(String.class);verify(ai).explain(anyString(),prompt.capture());
        assertThat(prompt.getValue()).contains("월급 지급 원칙","제43조").doesNotContain("사건 노트:");
    }
    @Test void notesNeedExplicitConsentAndOwnershipBeforeBeingSentToLocalModel() throws Exception {
        var book=notebooks.create("alice","private-notebook-marker","주거",null);
        mvc.perform(post("/legal-ai").with(user("bob")).with(csrf()).param("question","보증금 반환").param("notebookId",book.id.toString()).param("consent","true")).andExpect(status().isNotFound());
        mvc.perform(post("/legal-ai").with(user("alice")).with(csrf()).param("question","보증금 반환").param("notebookId",book.id.toString())).andExpect(status().isBadRequest());verifyNoInteractions(ai);
        mvc.perform(post("/legal-ai").with(user("alice")).with(csrf()).param("question","보증금 반환").param("notebookId",book.id.toString()).param("consent","true")).andExpect(status().isOk());
        var prompt=org.mockito.ArgumentCaptor.forClass(String.class);verify(ai).explain(anyString(),prompt.capture());assertThat(prompt.getValue()).contains("private-notebook-marker","사용자 작성");
    }
    @Test void unsupportedQuestionsDoNotGenerateAndDailyQuotaIsEnforced() throws Exception {
        mvc.perform(post("/legal-ai").with(user("alice")).with(csrf()).param("question","양자역학 실험 설계"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("어떤 일이 있었는지")));verifyNoInteractions(ai);
        for(int i=0;i<2;i++)mvc.perform(post("/legal-ai").with(user("alice")).with(csrf()).param("question","월급 지급 원칙")).andExpect(status().isOk());
        mvc.perform(post("/legal-ai").with(user("alice")).with(csrf()).param("question","월급 지급 원칙")).andExpect(status().isTooManyRequests());
        verify(ai,times(2)).explain(anyString(),anyString());
    }
    @Test void followupUsesPreviousQuestionToFindOfficialSources() throws Exception {
        mvc.perform(post("/legal-ai/chat").with(user("alice")).with(csrf())
                .param("question","어떤 기록을 챙겨야 하나요?").param("context","월급을 못 받았어요."))
            .andExpect(status().isOk()).andExpect(jsonPath("$.sources[0].id").value("wage-payment"));
        verify(ai).explain(anyString(),contains("월급을 못 받았어요."));
    }
    @Test void chatFindsAnArticleFromTheLegalCorpus() throws Exception {
        mvc.perform(post("/legal-ai/chat").with(user("alice")).with(csrf())
                .param("question","민법 제1000조를 설명해 주세요."))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sources[0].url").value("https://www.law.go.kr/법령/%EB%AF%BC%EB%B2%95/%EC%A0%9C1000%EC%A1%B0"))
            .andExpect(jsonPath("$.sources[0].title").value(containsString("상속의 순위")));
        verify(ai).explain(anyString(),contains("상속의 순위"));
    }
    @Test void conversationCarriesTheAssistantQuestionAndShortUserReply() throws Exception {
        mvc.perform(post("/legal-ai/chat").with(user("alice")).with(csrf())
                .param("question","지난주요.")
                .param("context","사용자: 월급을 못 받았어요.\nAI: 퇴직한 날짜는 언제인가요?"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.generated").value(true));
        verify(ai).explain(contains("한 번에 하나"),contains("퇴직한 날짜는 언제인가요?"));
        mvc.perform(get("/situation")).andExpect(redirectedUrl("/legal-ai"));
        mvc.perform(get("/compare")).andExpect(redirectedUrl("/legal-ai"));
    }
    @Test void chatFindsAnActualJudgmentFromExpandedOfficialCorpus() throws Exception {
        mvc.perform(post("/legal-ai/chat").with(user("alice")).with(csrf())
                .param("question","2024다322068 판결을 설명해 주세요."))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sources[0].id").value(org.hamcrest.Matchers.startsWith("prec-625289-")))
            .andExpect(jsonPath("$.sources[0].url").value("https://www.law.go.kr/LSW/precInfoP.do?precSeq=625289"))
            .andExpect(jsonPath("$.sources.length()").value(1))
            .andExpect(jsonPath("$.sources[0].title").value(containsString("2024다322068")));
        verify(ai).explain(anyString(),contains("2024다322068"));
    }
    @Test void depositQuestionExcludesGenericContractDamages() throws Exception {
        mvc.perform(post("/legal-ai/chat").with(user("alice")).with(csrf())
                .param("question","집 계약이 끝났는데 보증금을 돌려받지 못했어요."))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sources[0].id").value("housing-deposit"))
            .andExpect(jsonPath("$.sources[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("contract-damages"))));
        var context=org.mockito.ArgumentCaptor.forClass(String.class);
        var system=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(ai).explain(system.capture(),context.capture());
        assertThat(context.getValue()).doesNotContain("유언","구수증서");
        assertThat(system.getValue()).contains("적용법을 가르는 사실","이미 사용자가 제공한 사실");
    }
    @Test void generatedAnswerDoesNotPolluteFollowupRetrieval() throws Exception {
        mvc.perform(post("/legal-ai/chat").with(user("alice")).with(csrf())
                .param("question","그다음에는요?")
                .param("context","사용자: 보증금을 돌려받지 못했어요.\nAI: 계약 위약 불이행 손해 배상 약속을 확인하세요.\n민법 제390조를 보세요."))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sources[0].id").value("housing-deposit"))
            .andExpect(jsonPath("$.sources[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("contract-damages"))));
    }
    @Test void newSpecificTopicUsesCurrentQuestionInsteadOfOldConversation() throws Exception {
        mvc.perform(post("/legal-ai/chat").with(user("alice")).with(csrf())
                .param("question","월급이 밀렸어요.")
                .param("context","사용자: 보증금 전세 임대차 이사 집주인 문제예요.\nAI: 임대차를 확인해요."))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sources[0].id").value("wage-payment"))
            .andExpect(jsonPath("$.sources[*].id").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("housing-deposit"))));
    }
    @Test void ambiguousDepositSituationAsksBeforeAssumingHousingLaw() throws Exception {
        mvc.perform(post("/legal-ai/chat").with(user("alice")).with(csrf())
                .param("question","계약이 끝났는데 보증금을 돌려받지 못했어요."))
            .andExpect(status().isOk()).andExpect(jsonPath("$.generated").value(false))
            .andExpect(jsonPath("$.sources").isEmpty())
            .andExpect(jsonPath("$.text").value(containsString("집의 임대차 보증금인가요?")));
        verifyNoInteractions(ai);
    }
}
