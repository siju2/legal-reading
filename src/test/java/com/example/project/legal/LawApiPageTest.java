package com.example.project.legal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:law-page-test;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc class LawApiPageTest {
 @Autowired MockMvc mvc;@MockitoBean LawApiService api;
 @Test void officialResultsAndBodyRenderAsEscapedText()throws Exception{
  when(api.configured()).thenReturn(true);
  mvc.perform(get("/law-search")).andExpect(redirectedUrl("/legal-ai"));
  when(api.detail("600537")).thenReturn(new LawApiService.Detail("임금","2020다247190","20241219","대법원","통상임금 쟁점","요지","관련 조문","기각 <img src=x onerror=bad()>","https://www.law.go.kr/LSW/precInfoP.do?precSeq=600537"));
  mvc.perform(get("/law-search/precedents/600537")).andExpect(status().isOk()).andExpect(content().string(containsString("term-popover"))).andExpect(content().string(containsString("&lt;img"))).andExpect(content().string(not(containsString("<img src=x"))));
 }
}
