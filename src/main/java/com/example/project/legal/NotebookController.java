package com.example.project.legal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.format.annotation.DateTimeFormat;
import java.security.Principal;
import java.time.LocalDate;
@Controller public class NotebookController {
 private final NotebookService service;private final NotebookRepository books;private final TimelineRepository events;private final ChecklistRepository checks;private final LegalService legal;
 private final ConsultationLogRepository logs;private final EvidenceMapService evidence;
 public NotebookController(NotebookService s,NotebookRepository b,TimelineRepository e,ChecklistRepository c,LegalService l,ConsultationLogRepository logs,EvidenceMapService evidence){service=s;books=b;events=e;checks=c;legal=l;this.logs=logs;this.evidence=evidence;}
 @GetMapping("/notebooks") String list(Principal p,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,Model m){
  LegalService.require(q.length()<=100,"검색어는 100자 이하로 입력해 주세요.");var result=new NotebookSearchService(books,events,checks,logs,evidence).search(p.getName(),q,Math.max(0,Math.min(page,100000)));
  m.addAttribute("notebooks",result.getContent());m.addAttribute("q",q);m.addAttribute("page",result);return "legal/notebooks";
 }
 @GetMapping("/notebooks/new") String create(@RequestParam(required=false)Long caseId,Model m){m.addAttribute("sourceCase",caseId==null?null:legal.entry(caseId,false));return "legal/notebook-new";}
 @PostMapping("/notebooks") String create(Principal p,@RequestParam String title,@RequestParam String category,@RequestParam(required=false)Long caseId){return "redirect:/notebooks/"+service.create(p.getName(),title,category,caseId).id;}
 @GetMapping("/notebooks/{id}") String detail(@PathVariable long id,Principal p,Model m){var b=service.owned(p.getName(),id);var items=checks.findByNotebookIdOrderByIdAsc(id);m.addAttribute("book",b);var timeline=events.findByNotebookIdOrderByOccurredOnAscIdAsc(id);m.addAttribute("events",timeline);m.addAttribute("evidenceCards",evidence.read(b));m.addAttribute("evidenceKinds",EvidenceMapService.KINDS);m.addAttribute("evidenceEvents",timeline.stream().collect(java.util.stream.Collectors.toMap(e->e.id,e->e.title)));m.addAttribute("checks",items);m.addAttribute("completed",items.stream().filter(c->c.completed).count());m.addAttribute("kinds",NotebookService.KINDS);m.addAttribute("today",LocalDate.now());m.addAttribute("followups",logs.findByNotebookIdOrderByConsultedOnAscIdAsc(id));return "legal/notebook";}
 @PostMapping("/notebooks/{id}/events") String event(@PathVariable long id,Principal p,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate date,@RequestParam String kind,@RequestParam String title,@RequestParam(defaultValue="")String note,RedirectAttributes f){service.addEvent(p.getName(),id,date,kind,title,note);f.addFlashAttribute("notice","날짜순으로 기록을 저장했습니다.");f.addFlashAttribute("draftSaved","timeline");return "redirect:/notebooks/"+id;}
 @PostMapping("/notebooks/{id}/checks/{itemId}") String check(@PathVariable long id,@PathVariable long itemId,Principal p,@RequestParam(defaultValue="false")boolean completed){service.setCompleted(p.getName(),id,itemId,completed);return "redirect:/notebooks/"+id;}
 @PostMapping("/notebooks/{id}/events/{eventId}/delete") String remove(@PathVariable long id,@PathVariable long eventId,Principal p){service.removeEvent(p.getName(),id,eventId);return "redirect:/notebooks/"+id;}
 @PostMapping("/notebooks/{id}/delete") String delete(@PathVariable long id,Principal p){service.delete(p.getName(),id);return "redirect:/notebooks";}
}
