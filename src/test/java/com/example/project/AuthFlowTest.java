package com.example.project;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:auth-test;DB_CLOSE_DELAY=-1", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class AuthFlowTest {
    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder encoder;
    @BeforeEach void clear() { members.deleteAll(); }
    @Test void fullFlow() throws Exception {
        mvc.perform(get("/my")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/signup")).andExpect(status().isOk());
        mvc.perform(post("/signup").with(csrf()).param("username", "tester").param("password", "password123").param("passwordConfirm", "password123"))
            .andExpect(redirectedUrl("/login?registered"));
        Member member = members.findByUsername("tester").orElseThrow();
        assertThat(member.getPassword()).isNotEqualTo("password123");
        assertThat(encoder.matches("password123", member.getPassword())).isTrue();
        mvc.perform(post("/login").with(csrf()).param("username", "tester").param("password", "wrong"))
            .andExpect(redirectedUrl("/login?error"));
        MockHttpSession session = (MockHttpSession) mvc.perform(post("/login").with(csrf()).param("username", "tester").param("password", "password123"))
            .andExpect(redirectedUrl("/")).andReturn().getRequest().getSession(false);
        mvc.perform(get("/").session(session)).andExpect(status().isOk()).andExpect(model().attribute("viewer", "tester"));
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        assertThat(session.isInvalid()).isTrue();
    }
    @Test void duplicateAndInvalidSignup() throws Exception {
        members.saveAndFlush(new Member("tester", encoder.encode("password123")));
        mvc.perform(post("/signup").with(csrf()).param("username", "tester").param("password", "password123").param("passwordConfirm", "password123"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("signupForm", "username"));
        mvc.perform(post("/signup").with(csrf()).param("username", "newuser").param("password", "password123").param("passwordConfirm", "different"))
            .andExpect(model().attributeHasFieldErrors("signupForm", "passwordConfirm"));
        mvc.perform(post("/signup").with(csrf()).param("username", "a!").param("password", "short").param("passwordConfirm", "short"))
            .andExpect(model().attributeHasFieldErrors("signupForm", "username", "password"));
        assertThat(members.count()).isEqualTo(1);
    }
    @Test void csrfRequiredAndLongUtf8PasswordRejected() throws Exception {
        mvc.perform(post("/signup").param("username", "tester").param("password", "password123").param("passwordConfirm", "password123"))
            .andExpect(status().isForbidden());
        String longPassword = "가".repeat(25);
        mvc.perform(post("/signup").with(csrf()).param("username", "tester").param("password", longPassword).param("passwordConfirm", longPassword))
            .andExpect(model().attributeHasFieldErrors("signupForm", "password"));
        assertThat(members.count()).isZero();
    }
}