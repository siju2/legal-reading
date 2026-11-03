package com.example.project.legal;

import java.time.LocalDate;
import java.nio.file.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:consultation-test;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","legal.ai.enabled=false","legal.billing.enforce-premium=false","legal.billing.enabled=false"})
@AutoConfigureMockMvc
class ConsultationPreparationTest {
    @Autowired MockMvc mvc;@Autowired NotebookService books;@Autowired NotebookRepository repository;@Autowired ConsultationService consultation;@Autowired CaseRepository cases;
    @Test void preparationRequiresLoginOwnershipAndCsrfEvenForAdmin()throws Exception {
        var book=books.create("alice","비공개 상담","주거",null);String path="/notebooks/"+book.id+"/consultation";
        mvc.perform(get(path)).andExpect(status().is3xxRedirection());
        for(String user:new String[]{"bob","admin"}) {
            mvc.perform(get(path).with(user(user).roles("ADMIN"))).andExpect(status().isNotFound());
            mvc.perform(get(path+"/pdf").with(user(user).roles("ADMIN"))).andExpect(status().isNotFound());
            mvc.perform(post(path).with(user(user).roles("ADMIN")).with(csrf()).param("summary","changed")).andExpect(status().isNotFound());
        }
        mvc.perform(post(path).with(user("alice")).param("summary","changed")).andExpect(status().isForbidden());
        assertThat(consultation.prepare("alice",book.id).summary()).isEmpty();
    }
    @Test void savesClearsAndEscapesPrivateTextAndRetainsTimelineOrder()throws Exception {
        var book=books.create("alice","상담 준비","주거",null);String path="/notebooks/"+book.id+"/consultation";
        books.addEvent("alice",book.id,LocalDate.of(2026,2,2),"통지","두 번째 기록","");books.addEvent("alice",book.id,LocalDate.of(2026,1,1),"계약","첫 번째 기록","");
        mvc.perform(post(path).with(user("alice")).with(csrf()).param("summary","  보증금 기록 <script>alert(1)</script>  ").param("questions","추가로 준비할 자료는?").param("goal","사실 확인"))
            .andExpect(redirectedUrl(path));
        var prep=consultation.prepare("alice",book.id);assertThat(prep.summary()).startsWith("보증금 기록");assertThat(prep.questions()).isEqualTo("추가로 준비할 자료는?");assertThat(prep.events()).extracting(ConsultationService.Event::title).containsExactly("첫 번째 기록","두 번째 기록");
        mvc.perform(get(path).with(user("alice"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(content().string(containsString("&lt;script&gt;"))).andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
        mvc.perform(post(path).with(user("alice")).with(csrf()).param("summary","").param("questions","").param("goal","")).andExpect(redirectedUrl(path));
        assertThat(consultation.prepare("alice",book.id).summary()).isEmpty();
    }
    @Test void invalidInputKeepsEnteredValuesWithoutOverwritingSavedData()throws Exception {
        var book=books.create("alice","입력 확인","주거",null);String path="/notebooks/"+book.id+"/consultation";
        var form=new ConsultationForm();form.setSummary("기존 요약");consultation.save("alice",book.id,form);
        mvc.perform(post(path).with(user("alice")).with(csrf()).param("summary","가".repeat(2001)).param("questions","유지할 질문"))
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("사건 요약은 2,000자 이하"))).andExpect(content().string(containsString("유지할 질문")));
        assertThat(consultation.prepare("alice",book.id).summary()).isEqualTo("기존 요약");
    }
    @Test void pdfContainsSummaryQuestionsTimelineChecklistAndPublicSource()throws Exception {
        var source=cases.findByPublishedTrueOrderByDecisionDateDescIdDesc().stream().filter(c->!c.demo).findFirst().orElseThrow();
        var book=books.create("alice","상담용 사건 정리",source.category,source.id);var form=new ConsultationForm();form.setSummary("계약 후 정산 내용을 확인하려고 합니다.");form.setGoal("자료와 사실관계를 확인하고 싶습니다.");form.setQuestions("1. 추가 자료가 필요한가요?\n2. 어떤 사실을 더 정리할까요?");consultation.save("alice",book.id,form);
        books.addEvent("alice",book.id,LocalDate.of(2026,1,1),"계약","계약 내용 확인","계약서의 내용과 실제 입금 내역을 정리했습니다.");
        var response=mvc.perform(get("/notebooks/"+book.id+"/consultation/pdf").with(user("alice"))).andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(header().string("Cache-Control","no-store")).andReturn();
        byte[] data=response.getResponse().getContentAsByteArray();
        try(var doc=Loader.loadPDF(data)) {
            assertThat(new PDFTextStripper().getText(doc)).contains("상담 준비 자료","계약 후 정산","추가 자료가 필요한가요","계약 내용 확인","준비 자료",source.caseNumber,"공식 출처");
            Files.createDirectories(Path.of("target/consultation-review"));Files.write(Path.of("target/consultation-review/sample.pdf"),data);
            var renderer=new PDFRenderer(doc);for(int i=0;i<doc.getNumberOfPages();i++)ImageIO.write(renderer.renderImageWithDPI(i,110),"png",Path.of("target/consultation-review/page-"+(i+1)+".png").toFile());
        }
    }
    @Test void previouslyLinkedUnpublishedSourceIsNotExposedAndDeletingNotebookDeletesPreparation()throws Exception {
        var book=books.create("alice","연결 확인","주거",null);var draft=new LegalModels.CaseEntry();draft.title="공개하면 안 되는 초안";draft.category="주거";draft.published=false;cases.saveAndFlush(draft);
        book.sourceCaseId=draft.id;repository.saveAndFlush(book);var form=new ConsultationForm();form.setQuestions("private consultation");consultation.save("alice",book.id,form);
        assertThat(consultation.prepare("alice",book.id).source()).isNull();
        mvc.perform(get("/notebooks/"+book.id+"/consultation").with(user("alice"))).andExpect(content().string(not(containsString(draft.title))));
        books.delete("alice",book.id);assertThat(repository.existsById(book.id)).isFalse();
    }
}
