package com.example.project.legal;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class IntegrationSettings {
    private final Environment env;
    public IntegrationSettings(Environment env) { this.env = env; }
    public String value(String key, String fallback) { return env.getProperty(key, fallback).strip(); }
    public boolean flag(String key) { return Boolean.parseBoolean(value(key, "false")); }
    public boolean localAi() { return "local".equals(value("legal.ai.provider", "openai")); }
    public boolean aiReady() { return flag("legal.ai.enabled") && (localAi() || !value("legal.ai.api-key", "").isBlank()); }
    public int dailyLimit() { return Integer.parseInt(value("legal.ai.daily-limit", "10")); }
}
