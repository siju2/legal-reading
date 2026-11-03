package com.example.project.legal;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultationService {
    private final NotebookService notebooks;
    private final TimelineRepository timeline;
    private final ChecklistRepository checks;
    private final CaseRepository cases;
    private final ConsultationLogRepository logs;
    public ConsultationService(NotebookService notebooks,TimelineRepository timeline,ChecklistRepository checks,CaseRepository cases,ConsultationLogRepository logs) {
        this.notebooks=notebooks;this.timeline=timeline;this.checks=checks;this.cases=cases;this.logs=logs;
    }
    public record Event(LocalDate date,String kind,String title,String note) {}
    public record Item(String label,boolean completed) {}
    public record Source(long id,String title,String number,String url,boolean demo) {}
    public record Followup(LocalDate date,String answer,String nextActions,boolean completed) {}
    public record Preparation(long id,String title,String category,String summary,String questions,String goal,List<Event> events,List<Item> items,Source source,List<Followup> followups) {
        public long completed(){return items.stream().filter(Item::completed).count();}
        public ConsultationForm form(){var f=new ConsultationForm();f.setSummary(summary);f.setQuestions(questions);f.setGoal(goal);return f;}
    }
    @Transactional(readOnly=true)
    public Preparation prepare(String user,long id) {
        var b=notebooks.owned(user,id);
        var events=timeline.findByNotebookIdOrderByOccurredOnAscIdAsc(id).stream().map(e->new Event(e.occurredOn,e.kind,e.title,e.note)).toList();
        var items=checks.findByNotebookIdOrderByIdAsc(id).stream().map(c->new Item(c.label,c.completed)).toList();
        Source source=b.sourceCaseId==null?null:cases.findById(b.sourceCaseId).filter(c->c.published)
            .map(c->new Source(c.id,c.title,c.caseNumber,c.sourceUrl,c.demo)).orElse(null);
        var followups=logs.findByNotebookIdOrderByConsultedOnAscIdAsc(id).stream().map(l->new Followup(l.consultedOn,l.answer,l.nextActions,l.completed)).toList();
        return new Preparation(b.id,b.title,b.category,text(b.consultationSummary),text(b.consultationQuestions),text(b.consultationGoal),events,items,source,followups);
    }
    @Transactional
    public void save(String user,long id,ConsultationForm form) {
        var b=notebooks.owned(user,id);
        String summary=text(form.getSummary()),questions=text(form.getQuestions()),goal=text(form.getGoal());
        LegalService.require(summary.length()<=2000&&questions.length()<=3000&&goal.length()<=1000,"입력 가능한 글자 수를 확인해 주세요.");
        b.consultationSummary=summary;b.consultationQuestions=questions;b.consultationGoal=goal;
    }
    private String text(String value){return value==null?"":value.strip();}
}
