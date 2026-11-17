package com.example.project.legal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:disabled-integrations-test;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop","legal.ai.enabled=false","legal.ai.api-key="})
@AutoConfigureMockMvc
class DisabledIntegrationsTest {
    @Autowired MockMvc mvc;@Autowired CaseRepository cases;
    @Test void missingKeysGiveHonestPagesAndPreventRequests()throws Exception {
        long id=cases.findByPublishedTrueOrderByDecisionDateDescIdDesc().stream().filter(c->!c.demo).findFirst().orElseThrow().id;
        mvc.perform(get("/cases/"+id)).andExpect(status().isOk()).andExpect(content().string(containsString("AI 연결 준비 중")));
        mvc.perform(post("/cases/"+id+"/ai").with(user("alice")).with(csrf()).param("consent","true")).andExpect(status().isServiceUnavailable());
    }
    @Test void retiredPaymentRoutesCannotBeUsed() throws Exception {
        mvc.perform(get("/billing").with(user("alice"))).andExpect(status().isNotFound());
        mvc.perform(post("/billing/orders").with(user("alice")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/")).andExpect(content().string(not(containsString("Plus 이용권"))));
    }
}
