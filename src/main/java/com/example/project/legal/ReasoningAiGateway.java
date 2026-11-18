package com.example.project.legal;

interface ReasoningAiGateway extends AiGateway {
    String explain(String system, String context, boolean deepAnalysis);
}
