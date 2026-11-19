package com.example.project.legal;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class IntegrationSettings {
    private final Environment env;
    public IntegrationSettings(Environment env) { this.env = env; }
    public String value(String key, String fallback) { return env.getProperty(key, fallback).strip(); }
    public boolean flag(String key) { return Boolean.parseBoolean(value(key, "false")); }
    public boolean localAi() { return "local".equals(value("legal.ai.provider", "local")); }
    public boolean aiReady() { return flag("legal.ai.enabled") && (localAi() || !value("legal.ai.api-key", "").isBlank()); }
    public String localAiToken() {
        String token=value("legal.ai.local-token", "");
        if(!token.isBlank())return token;
        Path runtime=Path.of(value("legal.ai.runtime-path",Path.of(System.getProperty("user.home"),"Documents","Codex","local-legal-ai").toString()));
        try { token=Files.readString(runtime.resolve("api-token.txt")).strip(); }
        catch(java.io.IOException unavailable) { return ""; }
        return token.length()>=32?token:"";
    }
    public int dailyLimit() { return Integer.parseInt(value("legal.ai.daily-limit", "0")); }
}
