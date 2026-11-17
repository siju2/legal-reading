package com.example.project.legal;

import java.time.*;
import java.util.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:notebook-tools-test;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","legal.ai.enabled=false"})
@AutoConfigureMockMvc
class NotebookToolsTest {
    @Autowired MockMvc mvc;@Autowired NotebookService service;@Autowired ConsultationService consultation;@Autowired NotebookBackupService backups;
    @Autowired NotebookRepository books;@Autowired TimelineRepository timeline;@Autowired ChecklistRepository checks;@Autowired ConsultationLogRepository logs;@Autowired ObjectMapper mapper;
    @BeforeEach void reset(){logs.deleteAll();timeline.deleteAll();checks.deleteAll();books.deleteAll();}
    NotebookModels.Notebook create(String owner){return service.create(owner,"보증금 기록","주거",null);}
    @Test void checklistCanBeAddedEditedAndDeletedWithoutLosingCompletedState()throws Exception{
        var book=create("alice");String root="/notebooks/"+book.id;
        mvc.perform(post(root+"/checks/new").with(user("alice")).with(csrf()).param("label","추가 입금자료")).andExpect(redirectedUrl(root));
        var item=checks.findByNotebookIdOrderByIdAsc(book.id).stream().filter(c->c.label.equals("추가 입금자료")).findFirst().orElseThrow();service.setCompleted("alice",book.id,item.id,true);
        mvc.perform(post(root+"/checks/"+item.id+"/edit").with(user("alice")).with(csrf()).param("label","수정 입금자료")).andExpect(redirectedUrl(root));
        assertThat(checks.findById(item.id).orElseThrow().completed).isTrue();
        mvc.perform(get(root).with(user("alice"))).andExpect(status().isOk()).andExpect(content().string(containsString("수정 입금자료"))).andExpect(content().string(containsString("notebook-drafts.js")));
        mvc.perform(post(root+"/checks/"+item.id+"/delete").with(user("alice")).with(csrf())).andExpect(redirectedUrl(root));assertThat(checks.existsById(item.id)).isFalse();
    }
    @Test void checklistChangesRequireOwnershipCsrfAndValidLength()throws Exception{
        var book=create("alice");var other=create("bob");long wrong=checks.findByNotebookIdOrderByIdAsc(other.id).get(0).id;String root="/notebooks/"+book.id;
        mvc.perform(post(root+"/checks/new").with(user("alice")).param("label","x")).andExpect(status().isForbidden());
        mvc.perform(post(root+"/checks/new").with(user("bob")).with(csrf()).param("label","x")).andExpect(status().isNotFound());
        mvc.perform(post(root+"/checks/"+wrong+"/edit").with(user("alice")).with(csrf()).param("label","x")).andExpect(status().isNotFound());
        for(String label:List.of(" ","x".repeat(251)))mvc.perform(post(root+"/checks/new").with(user("alice")).with(csrf()).param("label",label)).andExpect(status().isBadRequest());
    }
    @Test void followupRecordsSaveEditRenderAndDeleteWithNotebook()throws Exception{
        var book=create("alice");String root="/notebooks/"+book.id;
        mvc.perform(post(root+"/followups").with(user("alice")).with(csrf()).param("date","2026-01-01").param("answer","답변 <script>x</script>").param("nextActions","입금 자료 준비")).andExpect(redirectedUrl(root)).andExpect(flash().attribute("draftSaved","followup"));
        var log=logs.findByNotebookIdOrderByConsultedOnAscIdAsc(book.id).get(0);
        mvc.perform(post(root+"/followups/"+log.id+"/edit").with(user("alice")).with(csrf()).param("date","2026-01-02").param("answer","상담 답변 수정").param("nextActions","자료 제출").param("completed","true")).andExpect(redirectedUrl(root));
        assertThat(logs.findById(log.id).orElseThrow().completed).isTrue();
        mvc.perform(get(root).with(user("alice"))).andExpect(status().isOk()).andExpect(content().string(containsString("상담 답변 수정")));
        service.delete("alice",book.id);assertThat(logs.existsById(log.id)).isFalse();
    }
    @Test void followupRejectsCrossNotebookUserAndFutureDates()throws Exception{
        var a=create("alice");var b=create("bob");service.saveConsultationLog("bob",b.id,null,LocalDate.now(),"private","",false);var log=logs.findByNotebookIdOrderByConsultedOnAscIdAsc(b.id).get(0);
        mvc.perform(post("/notebooks/"+a.id+"/followups/"+log.id+"/delete").with(user("alice")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/notebooks/"+b.id+"/followups/"+log.id+"/delete").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/notebooks/"+a.id+"/followups").with(user("alice")).with(csrf()).param("date","2999-01-01").param("answer","answer")).andExpect(status().isBadRequest());
        assertThat(logs.existsById(log.id)).isTrue();
    }
    @Test void selectedPdfExcludesUnselectedSectionsAndPrivateNotes()throws Exception{
        var b=create("alice");service.addEvent("alice",b.id,LocalDate.of(2026,1,1),"계약","공개할 사건 제목","private-timeline-note");service.saveConsultationLog("alice",b.id,null,LocalDate.of(2026,1,2),"private-answer","확인할 다음 행동",false);
        var form=new ConsultationForm();form.setSummary("private-summary");form.setQuestions("상담 질문입니다");consultation.save("alice",b.id,form);
        byte[] bytes=com.example.project.security.DataCipher.decryptFile(mvc.perform(get("/notebooks/"+b.id+"/consultation/pdf").with(user("alice")).param("sections","_selection","timeline","followups").param("notes","false")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        try(var doc=Loader.loadPDF(bytes)){
            assertThat(new PDFTextStripper().getText(doc)).contains("공개할 사건 제목","확인할 다음 행동","상세 메모 제외").doesNotContain("private-timeline-note","private-answer","private-summary","상담 질문입니다","준비 자료 -");
            Files.createDirectories(Path.of("target/tools-review"));Files.write(Path.of("target/tools-review/selected.pdf"),bytes);ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0,110),"png",Path.of("target/tools-review/selected.png").toFile());
        }
        mvc.perform(get("/notebooks/"+b.id+"/pdf").with(user("alice")).param("sections","_selection")).andExpect(status().isBadRequest());
        mvc.perform(get("/notebooks/"+b.id+"/pdf").with(user("alice")).param("sections","unknown")).andExpect(status().isBadRequest());
    }
    @Test void backupRoundTripPreservesDataAndRestoresAsNewOwnedCopies()throws Exception{
        var b=create("alice");service.addEvent("alice",b.id,LocalDate.of(2026,1,1),"계약","원본 기록","기록 메모");service.setCompleted("alice",b.id,checks.findByNotebookIdOrderByIdAsc(b.id).get(0).id,true);
        service.saveConsultationLog("alice",b.id,null,LocalDate.of(2026,1,2),"상담 답변","다음 할 일",true);var form=new ConsultationForm();form.setSummary("사건 요약");form.setQuestions("상담 질문");consultation.save("alice",b.id,form);
        var original=consultation.prepare("alice",b.id);byte[] bytes=backups.export("alice",b.id);assertThat(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("username","password");
        assertThat(backups.restore("bob",bytes)).isEqualTo(1);assertThat(books.findByUsernameOrderByCreatedAtDesc("alice")).hasSize(1);
        var restored=books.findByUsernameOrderByCreatedAtDesc("bob").get(0);assertThat(restored.id).isNotEqualTo(b.id);var copy=consultation.prepare("bob",restored.id);
        assertThat(copy.summary()).isEqualTo(original.summary());assertThat(copy.questions()).isEqualTo(original.questions());assertThat(copy.events()).isEqualTo(original.events());assertThat(copy.items()).isEqualTo(original.items());assertThat(copy.followups()).isEqualTo(original.followups());
        mvc.perform(get("/notebooks/backup").with(user("bob"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void restoreValidatesWholeBackupAndCannotAssignForeignOwnership()throws Exception{
        var a=create("alice");var root=(ObjectNode)mapper.readTree(backups.export("alice",a.id));var data=(com.fasterxml.jackson.databind.node.ArrayNode)root.get("notebooks");data.add(data.get(0).deepCopy());((ObjectNode)data.get(1)).put("title","");long count=books.count();
        byte[] invalid=mapper.writeValueAsBytes(root);assertThatThrownBy(()->backups.restore("bob",invalid)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);assertThat(books.count()).isEqualTo(count);
        root=(ObjectNode)mapper.readTree(backups.export("alice",a.id));((ObjectNode)root.get("notebooks").get(0)).put("username","alice");byte[] forged=mapper.writeValueAsBytes(root);
        assertThatThrownBy(()->backups.restore("bob",forged)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);assertThat(books.count()).isEqualTo(count);
    }
    @Test void restoreEndpointsRequireExplicitCopyConsentCsrfAndLogin()throws Exception{
        var a=create("alice");byte[] data=backups.export("alice",a.id);String path="/notebooks/restore";
        mvc.perform(get("/notebooks/"+a.id+"/backup").with(user("bob"))).andExpect(status().isNotFound());
        mvc.perform(multipart(path).file(new MockMultipartFile("file","backup.json","application/octet-stream",com.example.project.security.DataCipher.encryptFile(data))).with(user("bob")).param("confirm","true")).andExpect(status().isForbidden());
        mvc.perform(multipart(path).file(new MockMultipartFile("file","backup.json","application/octet-stream",com.example.project.security.DataCipher.encryptFile(data))).with(user("bob")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(multipart(path).file(new MockMultipartFile("file","backup.json","application/octet-stream",com.example.project.security.DataCipher.encryptFile(data))).with(user("bob")).with(csrf()).param("confirm","true")).andExpect(redirectedUrl("/notebooks"));
        mvc.perform(get("/notebooks/backup")).andExpect(status().is3xxRedirection());
    }
    @Test void corruptedOversizedAndUnsupportedBackupLeaveDataUntouched()throws Exception{
        create("alice");long count=books.count();for(byte[] bytes:List.of("not json".getBytes(),"null".getBytes(),"{\"format\":\"legal-notebooks\",\"version\":99,\"notebooks\":[]}".getBytes(),new byte[NotebookBackupService.MAX_BYTES+1]))assertThatThrownBy(()->backups.restore("bob",bytes)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(books.count()).isEqualTo(count);
    }
    @Test void searchFindsQuestionsNotesChecksAndFollowupsWithoutCrossAccountLeaks()throws Exception{
        var a=create("alice");var b=create("bob");service.addEvent("alice",a.id,LocalDate.of(2026,1,1),"기타","기록","독특한메모");service.addCheck("alice",a.id,"준비할증거");service.saveConsultationLog("alice",a.id,null,LocalDate.of(2026,1,2),"상담답변키워드","실행할행동",false);var form=new ConsultationForm();form.setQuestions("질문키워드");consultation.save("alice",a.id,form);
        for(String term:List.of("독특한메모","준비할증거","상담답변키워드","실행할행동","질문키워드")){
            mvc.perform(get("/notebooks").with(user("alice")).param("q",term)).andExpect(status().isOk()).andExpect(model().attribute("notebooks",hasSize(1)));
            mvc.perform(get("/notebooks").with(user("bob")).param("q",term)).andExpect(status().isOk()).andExpect(model().attribute("notebooks",hasSize(0)));
        }
        mvc.perform(get("/notebooks").with(user("admin").roles("ADMIN")).param("q","独特")).andExpect(model().attribute("notebooks",hasSize(0)));
    }
    @Test void literalWildcardsAreNotExpandedAndSearchIsPaginated()throws Exception{
        var a=create("alice");a.title="자료_100%";books.saveAndFlush(a);create("alice");
        for(String term:List.of("_","%"))mvc.perform(get("/notebooks").with(user("alice")).param("q",term)).andExpect(status().isOk()).andExpect(model().attribute("notebooks",hasSize(1)));
        for(int i=0;i<22;i++){var b=new NotebookModels.Notebook();b.username="alice";b.title="노트 "+i;b.category="주거";books.save(b);}
        mvc.perform(get("/notebooks").with(user("alice"))).andExpect(model().attribute("notebooks",hasSize(20)));
        mvc.perform(get("/notebooks").with(user("alice")).param("page","1")).andExpect(model().attribute("notebooks",hasSize(4)));
        mvc.perform(get("/notebooks").with(user("alice")).param("q","x".repeat(101))).andExpect(status().isBadRequest());
    }
    @Test void backupRoundTripsMoreThanOneThousandTimelineRecords() {
        var book=create("alice");var records=new ArrayList<NotebookModels.Event>();
        for(int i=0;i<1001;i++) {
            var event=new NotebookModels.Event();event.notebookId=book.id;event.occurredOn=LocalDate.of(2026,1,1);
            event.kind="기타";event.title="기록 "+i;event.note="보존할 메모";records.add(event);
        }
        timeline.saveAllAndFlush(records);
        assertThat(backups.restore("bob",backups.export("alice",book.id))).isEqualTo(1);
        var restored=books.findByUsernameOrderByCreatedAtDesc("bob").get(0);
        var restoredEvents=timeline.findByNotebookIdOrderByOccurredOnAscIdAsc(restored.id);
        assertThat(restoredEvents).hasSize(1001);
        assertThat(restoredEvents.get(1000).title).isEqualTo("기록 1000");
    }
}
