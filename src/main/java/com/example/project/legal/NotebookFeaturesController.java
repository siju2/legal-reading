package com.example.project.legal;

import java.security.Principal;
import java.time.LocalDate;
import org.springframework.stereotype.Controller;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class NotebookFeaturesController {
    private final NotebookService service;
    public NotebookFeaturesController(NotebookService service){this.service=service;}
    @PostMapping("/notebooks/{id}/checks/new") String add(@PathVariable long id,Principal user,@RequestParam String label,RedirectAttributes flash){service.addCheck(user.getName(),id,label);return back(id,flash,"준비 자료를 추가했습니다.");}
    @PostMapping("/notebooks/{id}/checks/{itemId}/edit") String edit(@PathVariable long id,@PathVariable long itemId,Principal user,@RequestParam String label,RedirectAttributes flash){service.editCheck(user.getName(),id,itemId,label);return back(id,flash,"준비 자료 이름을 수정했습니다.");}
    @PostMapping("/notebooks/{id}/checks/{itemId}/delete") String delete(@PathVariable long id,@PathVariable long itemId,Principal user,RedirectAttributes flash){service.removeCheck(user.getName(),id,itemId);return back(id,flash,"준비 자료 항목을 삭제했습니다.");}
    @PostMapping({"/notebooks/{id}/followups","/notebooks/{id}/followups/{logId}/edit"})
    String followup(@PathVariable long id,@PathVariable(required=false) Long logId,Principal user,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,@RequestParam String answer,
        @RequestParam(defaultValue="") String nextActions,@RequestParam(defaultValue="false") boolean completed,RedirectAttributes flash){
        service.saveConsultationLog(user.getName(),id,logId,date,answer,nextActions,completed);flash.addFlashAttribute("draftSaved",logId==null?"followup":"followup-"+logId);return back(id,flash,"상담 후 기록을 저장했습니다.");
    }
    @PostMapping("/notebooks/{id}/followups/{logId}/delete") String remove(@PathVariable long id,@PathVariable long logId,Principal user,RedirectAttributes flash){service.removeConsultationLog(user.getName(),id,logId);return back(id,flash,"상담 후 기록을 삭제했습니다.");}
    private String back(long id,RedirectAttributes flash,String notice){flash.addFlashAttribute("notice",notice);return "redirect:/notebooks/"+id;}
}
