package com.example.project.legal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:additions-test;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","legal.law-api.oc=","legal.law-api.config=./target/no-api-key.properties"})
@AutoConfigureMockMvc class AddedFeaturesTest {
 @Autowired MockMvc mvc;@Autowired NotebookService service;@Autowired NotebookRepository books;@Autowired TimelineRepository timeline;@Autowired ChecklistRepository checklist;@Autowired CaseRepository cases;@Autowired TermExplainer terms;
 @BeforeEach void reset(){timeline.deleteAll();checklist.deleteAll();books.deleteAll();}
 @Test void situationAndApiPagesArePublicAndKeyStateIsHonest()throws Exception{
  mvc.perform(get("/situation")).andExpect(status().isOk());
  mvc.perform(get("/situation").param("topic","wage").param("agreement","yes").param("evidence","no")).andExpect(status().isOk()).andExpect(content().string(containsString("임금 항목의 지급 규칙")));
  mvc.perform(get("/situation").param("topic","made-up")).andExpect(status().isBadRequest());
  mvc.perform(get("/law-search").param("q","임금")).andExpect(status().isOk()).andExpect(content().string(containsString("OC 인증값 미설정"))).andExpect(content().string(not(containsString("공식 API에서 일치하는 결과를 찾지 못했습니다"))));
  mvc.perform(get("/api/law/status")).andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false)).andExpect(jsonPath("$.oc").doesNotExist());
  mvc.perform(get("/api/law/search").param("q","임금")).andExpect(status().isServiceUnavailable());
  mvc.perform(get("/law-search/precedents/600537")).andExpect(status().isOk()).andExpect(content().string(containsString("OC 인증값")));
 }
 @Test void notebooksRequireLoginAndCsrf()throws Exception{
  mvc.perform(get("/notebooks")).andExpect(status().is3xxRedirection());
  mvc.perform(post("/notebooks").with(user("alice")).param("title","test").param("category","주거")).andExpect(status().isForbidden());
  mvc.perform(get("/notebooks/new").with(user("alice"))).andExpect(status().isOk());
  mvc.perform(post("/notebooks").with(user("alice")).with(csrf()).param("title","주거 기록").param("category","주거")).andExpect(status().is3xxRedirection());
  mvc.perform(get("/notebooks").with(user("alice"))).andExpect(content().string(containsString("주거 기록")));
  mvc.perform(get("/notebooks").with(user("bob"))).andExpect(content().string(not(containsString("주거 기록"))));
 }
 @Test void ownerCanRecordTimelineAndPersistChecklist()throws Exception{
  var b=service.create("alice","나의 기록","돈·거래",null);var item=checklist.findByNotebookIdOrderByIdAsc(b.id).get(0);
  mvc.perform(post("/notebooks/"+b.id+"/events").with(user("alice")).with(csrf()).param("date","2026-01-10").param("kind","송금").param("title","입금 기록").param("note","private-timeline")).andExpect(redirectedUrl("/notebooks/"+b.id));
  service.addEvent("alice",b.id,LocalDate.of(2026,1,1),"계약","처음 약정","");
  assertThat(timeline.findByNotebookIdOrderByOccurredOnAscIdAsc(b.id).get(0).title).isEqualTo("처음 약정");
  mvc.perform(post("/notebooks/"+b.id+"/checks/"+item.id).with(user("alice")).with(csrf()).param("completed","true")).andExpect(status().is3xxRedirection());
  assertThat(checklist.findById(item.id).orElseThrow().completed).isTrue();
  mvc.perform(get("/notebooks/"+b.id).with(user("alice"))).andExpect(status().isOk()).andExpect(content().string(containsString("private-timeline")));
  service.setCompleted("alice",b.id,item.id,false);assertThat(checklist.findById(item.id).orElseThrow().completed).isFalse();
 }
 @Test void otherUsersCannotReadModifyOrDeleteNotebookEvenAsAdmin()throws Exception{
  var b=service.create("alice","private-book","주거",null);service.addEvent("alice",b.id,LocalDate.now(),"기타","private-event","");var e=timeline.findByNotebookIdOrderByOccurredOnAscIdAsc(b.id).get(0);var item=checklist.findByNotebookIdOrderByIdAsc(b.id).get(0);
  mvc.perform(get("/notebooks/"+b.id).with(user("bob"))).andExpect(status().isNotFound());
  mvc.perform(get("/notebooks/"+b.id).with(user("admin").roles("ADMIN"))).andExpect(status().isNotFound());
  mvc.perform(post("/notebooks/"+b.id+"/checks/"+item.id).with(user("bob")).with(csrf()).param("completed","true")).andExpect(status().isNotFound());
  mvc.perform(post("/notebooks/"+b.id+"/events/"+e.id+"/delete").with(user("bob")).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(post("/notebooks/"+b.id+"/delete").with(user("bob")).with(csrf())).andExpect(status().isNotFound());
  assertThat(books.existsById(b.id)).isTrue();assertThat(timeline.existsById(e.id)).isTrue();
 }
 @Test void unrelatedChecklistAndEventIdsAreRejected()throws Exception{
  var a=service.create("alice","one","주거",null);var b=service.create("alice","two","주거",null);var wrong=checklist.findByNotebookIdOrderByIdAsc(b.id).get(0);
  mvc.perform(post("/notebooks/"+a.id+"/checks/"+wrong.id).with(user("alice")).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(post("/notebooks/"+a.id+"/events").with(user("alice")).with(csrf()).param("date","2999-01-01").param("kind","기타").param("title","future")).andExpect(status().isBadRequest());
  mvc.perform(post("/notebooks/"+a.id+"/events").with(user("alice")).with(csrf()).param("date","2026-01-01").param("kind","invalid").param("title","bad")).andExpect(status().isBadRequest());
 }
 @Test void referencedCaseSuppliesChecklistAndDeletionRemovesChildren()throws Exception{
  var c=cases.findByPublishedTrueOrderByDecisionDateDescIdDesc().get(0);var b=service.create("alice","reference",c.category,c.id);
  assertThat(checklist.findByNotebookIdOrderByIdAsc(b.id)).isNotEmpty();service.addEvent("alice",b.id,LocalDate.now(),"기타","event","");
  mvc.perform(get("/notebooks/new").with(user("alice")).param("caseId",c.id.toString())).andExpect(status().isOk()).andExpect(content().string(containsString(c.title)));
  mvc.perform(post("/notebooks/"+b.id+"/delete").with(user("alice")).with(csrf())).andExpect(redirectedUrl("/notebooks"));
  assertThat(books.existsById(b.id)).isFalse();assertThat(checklist.findByNotebookIdOrderByIdAsc(b.id)).isEmpty();assertThat(timeline.findByNotebookIdOrderByOccurredOnAscIdAsc(b.id)).isEmpty();
 }
 @Test void inlineTermsPreserveOriginalTextAndEscapeMarkup()throws Exception{
  assertThat(terms.split("살피고 피고인 피고는")).filteredOn(p->p.explanation()!=null).extracting(TermExplainer.Part::text).containsExactly("피고");
  String input="<script>x</script> 통상임금과 입증책임";assertThat(terms.split(input).stream().map(TermExplainer.Part::text).reduce("",String::concat)).isEqualTo(input);assertThat(terms.split(input)).anyMatch(p->p.explanation()!=null);
  var c=cases.findByPublishedTrueOrderByDecisionDateDescIdDesc().get(0);mvc.perform(get("/cases/"+c.id)).andExpect(status().isOk()).andExpect(content().string(containsString("inline-term"))).andExpect(content().string(containsString("term-popover")));
 }
}
