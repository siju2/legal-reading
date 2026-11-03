package com.example.project.legal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import java.security.Principal;
import java.time.LocalDate;
@Controller public class NotebookController {
 private final NotebookService service;private final NotebookRepository books;private final TimelineRepository events;private final ChecklistRepository checks;private final LegalService legal;
 private final ConsultationLogRepository logs;
 public NotebookController(NotebookService s,NotebookRepository b,TimelineRepository e,ChecklistRepository c,LegalService l,ConsultationLogRepository logs){service=s;books=b;events=e;checks=c;legal=l;this.logs=logs;}
 @ModelAttribute void nav(Model m,Authentication a){m.addAttribute("viewer",a==null?null:a.getName());m.addAttribute("isAdmin",a!=null&&a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_ADMIN")));m.addAttribute("categories",LegalService.CATEGORIES);}
 @GetMapping("/notebooks") String list(Principal p,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="0") int page,Model m){
  LegalService.require(q.length()<=100,"검색어는 100자 이하로 입력해 주세요.");String term="%"+q.strip().toLowerCase(java.util.Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
  var result=books.searchOwned(p.getName(),term,org.springframework.data.domain.PageRequest.of(Math.max(0,Math.min(page,100000)),20,org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"createdAt","id")));
  m.addAttribute("notebooks",result.getContent());m.addAttribute("q",q);m.addAttribute("page",result);return "legal/notebooks";
 }
 @GetMapping("/notebooks/new") String create(@RequestParam(required=false)Long caseId,Model m){m.addAttribute("sourceCase",caseId==null?null:legal.entry(caseId,false));return "legal/notebook-new";}
 @PostMapping("/notebooks") String create(Principal p,@RequestParam String title,@RequestParam String category,@RequestParam(required=false)Long caseId){return "redirect:/notebooks/"+service.create(p.getName(),title,category,caseId).id;}
 @GetMapping("/notebooks/{id}") String detail(@PathVariable long id,Principal p,Model m){var b=service.owned(p.getName(),id);var items=checks.findByNotebookIdOrderByIdAsc(id);m.addAttribute("book",b);m.addAttribute("events",events.findByNotebookIdOrderByOccurredOnAscIdAsc(id));m.addAttribute("checks",items);m.addAttribute("completed",items.stream().filter(c->c.completed).count());m.addAttribute("kinds",NotebookService.KINDS);m.addAttribute("today",LocalDate.now());m.addAttribute("followups",logs.findByNotebookIdOrderByConsultedOnAscIdAsc(id));return "legal/notebook";}
 @PostMapping("/notebooks/{id}/events") String event(@PathVariable long id,Principal p,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate date,@RequestParam String kind,@RequestParam String title,@RequestParam(defaultValue="")String note,RedirectAttributes f){service.addEvent(p.getName(),id,date,kind,title,note);f.addFlashAttribute("notice","날짜순으로 기록을 저장했습니다.");f.addFlashAttribute("draftSaved","timeline");return "redirect:/notebooks/"+id;}
 @PostMapping("/notebooks/{id}/checks/{itemId}") String check(@PathVariable long id,@PathVariable long itemId,Principal p,@RequestParam(defaultValue="false")boolean completed){service.setCompleted(p.getName(),id,itemId,completed);return "redirect:/notebooks/"+id;}
 @PostMapping("/notebooks/{id}/events/{eventId}/delete") String remove(@PathVariable long id,@PathVariable long eventId,Principal p){service.removeEvent(p.getName(),id,eventId);return "redirect:/notebooks/"+id;}
 @PostMapping("/notebooks/{id}/delete") String delete(@PathVariable long id,Principal p){service.delete(p.getName(),id);return "redirect:/notebooks";}
}
