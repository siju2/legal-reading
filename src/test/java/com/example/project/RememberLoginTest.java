package com.example.project;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import jakarta.servlet.http.Cookie;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:remember-login;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class RememberLoginTest {
 @Autowired MockMvc mvc;@Autowired MemberRepository members;@Autowired PasswordEncoder encoder;@Autowired JdbcTemplate jdbc;
 @BeforeEach void setup(){jdbc.update("delete from legal_login_tokens");members.deleteAll();members.saveAndFlush(new Member("rememberuser",encoder.encode("test-password-123")));}
 private org.springframework.test.web.servlet.MvcResult login(boolean remember)throws Exception{
  return mvc.perform(post("/login").secure(true).with(csrf()).param("username","rememberuser").param("password","test-password-123").param("remember-me",remember?"on":"false")).andExpect(redirectedUrl("/")).andReturn();
 }
 @Test void optedInLoginUsesSecureCookieEncryptedStorageAndRotatesToken()throws Exception{
  var result=login(true);Cookie cookie=result.getResponse().getCookie("legal-remember-me");
  assertThat(cookie).isNotNull();assertThat(cookie.getSecure()).isTrue();assertThat(cookie.isHttpOnly()).isTrue();assertThat(cookie.getMaxAge()).isEqualTo(14*24*60*60);
  String raw=jdbc.queryForObject("select token from legal_login_tokens",String.class);assertThat(raw).startsWith("LRG1:");
  var next=mvc.perform(get("/notebooks").secure(true).cookie(cookie)).andExpect(status().isOk()).andExpect(model().attribute("viewer","rememberuser")).andReturn();
  assertThat(next.getResponse().getCookie("legal-remember-me").getValue()).isNotEqualTo(cookie.getValue());
 }
 @Test void uncheckedDoesNotPersistLogin()throws Exception{
  var result=login(false);assertThat(result.getResponse().getCookie("legal-remember-me")).isNull();assertThat(jdbc.queryForObject("select count(*) from legal_login_tokens",Integer.class)).isZero();
 }
 @Test void logoutRevokesPersistentToken()throws Exception{
  var result=login(true);Cookie cookie=result.getResponse().getCookie("legal-remember-me");
  var session=(MockHttpSession)result.getRequest().getSession(false);
  mvc.perform(post("/logout").secure(true).session(session).cookie(cookie).with(csrf())).andExpect(redirectedUrl("/login?logout"));
  assertThat(jdbc.queryForObject("select count(*) from legal_login_tokens",Integer.class)).isZero();
  mvc.perform(get("/notebooks").secure(true).cookie(cookie)).andExpect(status().is3xxRedirection());
 }
 @Test void expiredTokenCannotLogIn()throws Exception{
  Cookie cookie=login(true).getResponse().getCookie("legal-remember-me");
  jdbc.update("update legal_login_tokens set last_used=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(15*24*60*60)));
  mvc.perform(get("/notebooks").secure(true).cookie(cookie)).andExpect(status().is3xxRedirection());
 }
}
