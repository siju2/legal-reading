package com.example.project.legal;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class IntegrationSettings {
    private final Environment env;
    public IntegrationSettings(Environment env) { this.env = env; }
    public String value(String key, String fallback) { return env.getProperty(key, fallback).strip(); }
    public boolean flag(String key) { return Boolean.parseBoolean(value(key, "false")); }
    public boolean aiReady() { return flag("legal.ai.enabled") && !value("legal.ai.api-key", "").isBlank(); }
    public boolean billingReady() {
        return flag("legal.billing.enabled") && !value("legal.billing.api-secret", "").isBlank()
            && !value("legal.billing.store-id", "").isBlank() && !value("legal.billing.channel-key", "").isBlank()
            && !value("legal.billing.webhook-secret", "").isBlank();
    }
    public int dailyLimit() { return Integer.parseInt(value("legal.ai.daily-limit", "10")); }
    public long price() { return Long.parseLong(value("legal.billing.price", "9900")); }
    public int passDays() { return Integer.parseInt(value("legal.billing.pass-days", "30")); }
}
