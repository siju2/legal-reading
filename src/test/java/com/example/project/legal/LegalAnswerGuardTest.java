package com.example.project.legal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LegalAnswerGuardTest {
    private final LawAssistantService.Source source = new LawAssistantService.Source(
        "law-1", "공식 자료", "계약이 종료된 경우의 기본 내용을 설명합니다.", "https://www.law.go.kr/법령/민법", "2026-11-16", List.of());

    @Test void acceptsNaturalParaphraseOfGeneralLegalConcepts() {
        assertFalse(LegalAnswerGuard.hasUnsupportedLegalReference(
            "계약이 끝난 뒤 보증금을 돌려받지 못했다면 손해배상 청구도 검토할 수 있어요.", List.of(source)));
    }

    @Test void stillRejectsUnverifiedStatutesAndDeadlines() {
        assertTrue(LegalAnswerGuard.hasUnsupportedLegalReference("민법 제999조가 적용돼요.", List.of(source)));
        assertTrue(LegalAnswerGuard.hasUnsupportedLegalReference("3개월 안에 신청해야 해요.", List.of(source)));
    }
}
