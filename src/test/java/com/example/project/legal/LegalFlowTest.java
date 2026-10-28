package com.example.project.legal;
import com.example.project.legal.LegalModels.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:legal-test;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","legal.bootstrap-admin-password="})
@AutoConfigureMockMvc
class LegalFlowTest {
 @Autowired MockMvc mvc;@Autowired CaseRepository cases;@Autowired BookmarkRepository bookmarks;@Autowired RevisionRepository revisions;@Autowired LegalService service;
 long id;
 @BeforeEach void setup(){bookmarks.deleteAll();id=cases.findByPublishedTrueOrderByDecisionDateDescIdDesc().stream().filter(c->!c.demo).findFirst().orElseThrow().id;}
 @Test void allPublicPagesAndActualCaseApiRender()throws Exception{
  for(String url:new String[]{"/","/cases","/cases?type=real","/cases?category=주거","/glossary","/guide","/compare","/login","/signup","/cases/"+id,"/compare?left="+id+"&right="+id})mvc.perform(get(url)).andExpect(status().isOk());
  mvc.perform(get("/")).andExpect(content().string(containsString("판결읽기"))).andExpect(content().string(not(containsString("로스트미디어"))));
  mvc.perform(get("/api/cases")).andExpect(status().isOk()).andExpect(jsonPath("$[0].sourceUrl",startsWith("https://www.law.go.kr/")));
 }
 @Test void searchFiltersAndEmptyStates()throws Exception{
  assertThat(service.search("월급을 못 받았어요","","real")).isNotEmpty();
  assertThat(service.search("","주거","real")).isEmpty();
  assertThat(service.search("","주거","demo")).allMatch(c->c.demo);
  mvc.perform(get("/cases").param("q","없는자료xyz")).andExpect(content().string(containsString("일치하는 자료가 없어요")));
  mvc.perform(get("/cases").param("q","x".repeat(161))).andExpect(status().isBadRequest());
  mvc.perform(get("/cases").param("category","다른분야")).andExpect(status().isBadRequest());
 }
 @Test void authenticationCsrfAndAdminBoundaries()throws Exception{
  mvc.perform(get("/my")).andExpect(status().is3xxRedirection());
  mvc.perform(get("/admin").with(user("reader"))).andExpect(status().isForbidden());
  mvc.perform(post("/cases/"+id+"/bookmark").with(user("reader"))).andExpect(status().isForbidden());
  mvc.perform(get("/admin").with(user("editor").roles("ADMIN"))).andExpect(status().isOk());
  mvc.perform(get("/admin/cases/new").with(user("editor").roles("ADMIN"))).andExpect(status().isOk());
  mvc.perform(get("/admin/cases/"+id+"/edit").with(user("editor").roles("ADMIN"))).andExpect(status().isOk());
 }
 @Test void bookmarksAreIdempotentAndNotesArePrivate()throws Exception{
  for(int i=0;i<2;i++)mvc.perform(post("/cases/"+id+"/bookmark").with(user("alice")).with(csrf())).andExpect(redirectedUrl("/cases/"+id));
  assertThat(bookmarks.findByUsernameOrderBySavedAtDesc("alice")).hasSize(1);
  mvc.perform(post("/cases/"+id+"/note").with(user("alice")).with(csrf()).param("note","private-alice-note")).andExpect(redirectedUrl("/my"));
  mvc.perform(get("/my").with(user("alice"))).andExpect(content().string(containsString("private-alice-note")));
  mvc.perform(get("/my").with(user("bob"))).andExpect(content().string(not(containsString("private-alice-note"))));
  mvc.perform(post("/cases/"+id+"/note").with(user("bob")).with(csrf()).param("note","overwrite")).andExpect(status().isNotFound());
  mvc.perform(post("/cases/"+id+"/unbookmark").with(user("alice")).with(csrf())).andExpect(redirectedUrl("/my"));
  assertThat(bookmarks.findByUsernameOrderBySavedAtDesc("alice")).isEmpty();
 }
 @Test void unpublishedCasesNeverLeakIntoPublicViews()throws Exception{
  var f=demoForm();f.title="secret-unpublished-title";var c=service.save(null,f,"editor");
  try{
   mvc.perform(get("/cases/"+c.id)).andExpect(status().isNotFound());
   mvc.perform(get("/compare").param("left",c.id.toString())).andExpect(status().isNotFound());
   mvc.perform(get("/cases")).andExpect(content().string(not(containsString(c.title))));
   mvc.perform(post("/cases/"+c.id+"/bookmark").with(user("alice")).with(csrf())).andExpect(status().isNotFound());
   mvc.perform(get("/cases/"+c.id).with(user("editor").roles("ADMIN"))).andExpect(status().isOk());
  }finally{revisions.deleteAll(revisions.findByCaseIdOrderByIdDesc(c.id));cases.deleteById(c.id);}
 }
 @Test void publicationSourceValidationAndHistory(){
  var f=demoForm();f.demo=false;f.published=true;f.reviewed=true;
  assertThatThrownBy(()->service.save(null,f,"editor")).isInstanceOf(ResponseStatusException.class);
  f.sourceUrl="javascript:alert(1)";assertThatThrownBy(()->service.save(null,f,"editor")).isInstanceOf(ResponseStatusException.class);
  assertThat(LegalService.officialUrl("https://www.law.go.kr.evil.example/a")).isFalse();
  assertThat(LegalService.officialUrl("https://www.law.go.kr@evil.example/a")).isFalse();
  assertThat(LegalService.officialUrl("https://www.law.go.kr:444/a")).isFalse();
  var d=demoForm();var c=service.save(null,d,"editor");
  try{var edit=service.form(c);edit.title="edited-title";service.save(c.id,edit,"editor");assertThat(revisions.findByCaseIdOrderByIdDesc(c.id)).hasSize(2);assertThatThrownBy(()->service.save(c.id,edit,"editor")).isInstanceOf(ResponseStatusException.class);}
  finally{revisions.deleteAll(revisions.findByCaseIdOrderByIdDesc(c.id));cases.deleteById(c.id);}
 }
 @Test void htmlInputIsEscapedInPublicContentAndPrivateNotes()throws Exception{
  var f=demoForm();f.title="<script>alert('x')</script>";f.published=true;f.reviewed=true;var c=service.save(null,f,"editor");
  try{mvc.perform(get("/cases/"+c.id)).andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;"))).andExpect(content().string(not(containsString("<script>alert"))));}
  finally{revisions.deleteAll(revisions.findByCaseIdOrderByIdDesc(c.id));cases.deleteById(c.id);}
 }
 @Test void invalidAdminFormKeepsErrorsAndDoesNotSave()throws Exception{
  long before=cases.count();mvc.perform(post("/admin/cases/new").with(user("editor").roles("ADMIN")).with(csrf()).param("title","")).andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("caseForm","title"));assertThat(cases.count()).isEqualTo(before);
 }
 static CaseForm demoForm(){var f=new CaseForm();f.demo=true;f.title="test";f.summary="summary";f.facts="facts";f.issue="issue";f.reasoning="reasoning";return f;}
}
