package com.example.project.legal;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IntegrationNavigationTest {
    @Controller static class Page {
        @GetMapping("/navigation-test") String page() { return "navigation-view"; }
    }
    @Test void commonNavigationPreservesAnonymousMemberAndAdminModels() throws Exception {
        var navigation = new IntegrationNavigation(new IntegrationSettings(new MockEnvironment()));
        var mvc = MockMvcBuilders.standaloneSetup(new Page()).setControllerAdvice(navigation).build();
        mvc.perform(get("/navigation-test"))
            .andExpect(model().attribute("categories", LegalService.CATEGORIES))
            .andExpect(model().attribute("isAdmin", false))
            .andExpect(model().attribute("aiReady", false));
        for (String role : new String[]{"ROLE_USER", "ROLE_ADMIN"}) {
            var user = new UsernamePasswordAuthenticationToken("alice", "unused", AuthorityUtils.createAuthorityList(role));
            mvc.perform(get("/navigation-test").principal(user))
                .andExpect(model().attribute("viewer", "alice"))
                .andExpect(model().attribute("categories", LegalService.CATEGORIES))
                .andExpect(model().attribute("isAdmin", role.equals("ROLE_ADMIN")));
        }
    }
}