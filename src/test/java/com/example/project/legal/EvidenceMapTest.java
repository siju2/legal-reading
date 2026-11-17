package com.example.project.legal;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:evidence-test;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","legal.ai.enabled=false"})
@AutoConfigureMockMvc
class EvidenceMapTest {
    @Autowired EvidenceMapService evidence; @Autowired MockMvc mvc; @Autowired NotebookService notebooks; @Autowired NotebookRepository books;
    @Autowired TimelineRepository events; @Autowired NotebookBackupService backups; @Autowired ObjectMapper mapper; @Autowired JdbcTemplate jdbc;
    NotebookModels.Notebook book; long eventId;
    @BeforeEach void setup() {
        book=notebooks.create("alice","증거 지도 테스트","주거",null);
        notebooks.addEvent("alice",book.id,LocalDate.of(2026,1,1),"송금","입금 기록","관련 메모");
        eventId=events.findByNotebookIdOrderByOccurredOnAscIdAsc(book.id).get(0).id;
    }
    String root(){return "/notebooks/"+book.id;}
    String addCard() throws Exception {
        mvc.perform(post(root()+"/evidence").with(user("alice")).with(csrf()).param("kind","내 주장").param("title","보증금 미반환 <script>x</script>")
            .param("eventId",Long.toString(eventId)).param("gap","종료 통지 날짜 확인").param("nextAction","문자 기록 찾기")).andExpect(redirectedUrl(root()+"#evidence-map"));
        String html=mvc.perform(get(root()).with(user("alice"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("보증금 미반환 &lt;script&gt;"))).andExpect(content().string(containsString("문자 기록 찾기"))).andReturn().getResponse().getContentAsString();
        java.nio.file.Files.writeString(java.nio.file.Path.of("C:/Users/user/Documents/Codex/2026-11-11/wkq/work/evidence-preview.html"),html);
        var match=Pattern.compile("data-evidence-id=\"([^\"]+)\"").matcher(html);assertThat(match.find()).isTrue();return match.group(1);
    }
    @Test void cardsArePrivateEncryptedEditableAndSurviveDeletedTimelineLinks() throws Exception {
        String id=addCard();
        String encrypted=jdbc.queryForObject("select evidence_map from legal_notebooks where id=?",String.class,book.id);
        assertThat(encrypted).startsWith("LRG1:").doesNotContain("보증금");
        mvc.perform(post(root()+"/evidence/"+id).with(user("alice")).with(csrf()).param("kind","확인한 사실").param("title","수정된 사실").param("eventId",Long.toString(eventId))).andExpect(status().is3xxRedirection());
        notebooks.removeEvent("alice",book.id,eventId);
        mvc.perform(get(root()).with(user("alice"))).andExpect(status().isOk()).andExpect(content().string(containsString("연결한 기록이 삭제되었습니다")));
        mvc.perform(post(root()+"/evidence/"+id+"/delete").with(user("bob")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post(root()+"/evidence/"+id+"/delete").with(user("alice")).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(get(root()).with(user("alice"))).andExpect(content().string(org.hamcrest.Matchers.not(containsString("data-evidence-id="))));
    }
    @Test void cardWritesRequireLoginCsrfOwnershipAndValidFields() throws Exception {
        mvc.perform(post(root()+"/evidence").with(csrf()).param("kind","내 주장").param("title","x")).andExpect(status().is3xxRedirection());
        mvc.perform(post(root()+"/evidence").with(user("alice")).param("kind","내 주장").param("title","x")).andExpect(status().isForbidden());
        mvc.perform(post(root()+"/evidence").with(user("bob")).with(csrf()).param("kind","내 주장").param("title","x")).andExpect(status().isNotFound());
        for(String title:List.of(" ","x".repeat(301)))mvc.perform(post(root()+"/evidence").with(user("alice")).with(csrf()).param("kind","내 주장").param("title",title)).andExpect(status().isBadRequest());
        var other=notebooks.create("bob","다른 노트","주거",null);notebooks.addEvent("bob",other.id,LocalDate.of(2026,1,1),"기타","비공개","비밀");
        long foreign=events.findByNotebookIdOrderByOccurredOnAscIdAsc(other.id).get(0).id;
        mvc.perform(post(root()+"/evidence").with(user("alice")).with(csrf()).param("kind","내 주장").param("title","x").param("eventId",Long.toString(foreign))).andExpect(status().isNotFound());
        mvc.perform(post(root()+"/evidence").with(user("alice")).with(csrf()).param("kind","판정").param("title","x")).andExpect(status().isBadRequest());
    }
    @Test void backupRestoresCardsWithNewTimelineIdsAndAcceptsOldBackups() throws Exception {
        addCard();byte[] data=backups.export("alice",book.id);assertThat(backups.restore("bob",data)).isEqualTo(1);
        var copy=books.findByUsernameOrderByCreatedAtDesc("bob").get(0);
        var json=mapper.readTree(backups.export("bob",copy.id));assertThat(json.at("/notebooks/0/evidence/0/eventIndex").asInt()).isZero();
        String html=mvc.perform(get("/notebooks/"+copy.id).with(user("bob"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("문자 기록 찾기","입금 기록").doesNotContain("연결한 기록이 삭제되었습니다");
        var legacy=(ObjectNode)mapper.readTree(data);((ObjectNode)legacy.at("/notebooks/0")).remove("evidence");
        assertThat(backups.restore("legacy",mapper.writeValueAsBytes(legacy))).isEqualTo(1);
    }
    @Test void simultaneousCardsAreRetainedAndCardLimitDoesNotDiscardExistingData() throws Exception {
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
        try {
            var first=executor.submit(()->{start.await();evidence.save("alice",book.id,null,"내 주장","첫 카드",eventId,"","할 일");return true;});
            var second=executor.submit(()->{start.await();evidence.save("alice",book.id,null,"내 추측","둘째 카드",null,"미확인","");return true;});
            start.countDown();first.get(10,java.util.concurrent.TimeUnit.SECONDS);second.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(evidence.read(books.findById(book.id).orElseThrow())).extracting(EvidenceMapService.Card::title).containsExactlyInAnyOrder("첫 카드","둘째 카드");
        } finally {executor.shutdownNow();}
        var cards=new ArrayList<EvidenceMapService.Card>();
        for(int i=0;i<100;i++)cards.add(new EvidenceMapService.Card(UUID.randomUUID().toString(),"내 주장","카드 "+i,null,"",""));
        var current=books.findById(book.id).orElseThrow();evidence.write(current,cards);books.saveAndFlush(current);
        mvc.perform(post(root()+"/evidence").with(user("alice")).with(csrf()).param("kind","내 주장").param("title","초과 항목")).andExpect(status().isBadRequest());
        assertThat(evidence.read(books.findById(book.id).orElseThrow())).hasSize(100);
    }
    @Test void invalidBackupReferencesAreRejectedBeforeAnyNotebookIsCreated() throws Exception {
        addCard();var data=(ObjectNode)mapper.readTree(backups.export("alice",book.id));long count=books.count();
        var card=(ObjectNode)data.at("/notebooks/0/evidence/0");card.put("eventIndex",999999);
        assertThatThrownBy(()->backups.restore("bob",mapper.writeValueAsBytes(data))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        card.putNull("kind");card.put("eventIndex",0);
        assertThatThrownBy(()->backups.restore("bob",mapper.writeValueAsBytes(data))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(books.count()).isEqualTo(count);
    }
}
