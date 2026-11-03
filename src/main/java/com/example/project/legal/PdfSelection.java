package com.example.project.legal;
import java.util.*;

public final class PdfSelection {
    private PdfSelection(){}
    public static final Set<String> NOTEBOOK=Set.of("timeline","checklist","followups");
    public static final Set<String> CONSULTATION=Set.of("summary","goal","questions","timeline","checklist","source","followups");
    public static Set<String> parse(List<String> sections,Set<String> allowed){
        if(sections==null)return allowed;
        sections=sections.stream().filter(s->!"_selection".equals(s)).toList();
        LegalService.require(!sections.isEmpty()&&sections.size()<=allowed.size()&&sections.stream().allMatch(allowed::contains),"PDF에 포함할 항목을 하나 이상 선택해 주세요.");
        return Set.copyOf(sections);
    }
}
