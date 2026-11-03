package com.example.project.legal;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.security.core.Authentication;

@ControllerAdvice
public class IntegrationNavigation {
    private final IntegrationSettings settings;
    public IntegrationNavigation(IntegrationSettings settings) { this.settings=settings; }
    @ModelAttribute("aiReady") boolean aiReady() { return settings.aiReady(); }
    @ModelAttribute("viewer") String viewer(Authentication auth) { return auth==null?null:auth.getName(); }
    @ModelAttribute("isAdmin") boolean admin(Authentication auth) { return auth!=null&&auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")); }
}
