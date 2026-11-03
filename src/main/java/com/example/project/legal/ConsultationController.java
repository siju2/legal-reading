package com.example.project.legal;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class ConsultationController {
    private final ConsultationService service;
    private final NotebookPdfService pdf;
    public ConsultationController(ConsultationService service,NotebookPdfService pdf){this.service=service;this.pdf=pdf;}
    @GetMapping("/notebooks/{id}/consultation")
    String page(@PathVariable long id,Principal user,Model model,HttpServletResponse response) {
        var prep=service.prepare(user.getName(),id);model.addAttribute("prep",prep);model.addAttribute("consultationForm",prep.form());
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");return "legal/consultation";
    }
    @PostMapping("/notebooks/{id}/consultation")
    String save(@PathVariable long id,Principal user,@Valid @ModelAttribute ConsultationForm consultationForm,BindingResult errors,Model model,RedirectAttributes flash,HttpServletResponse response) {
        var prep=service.prepare(user.getName(),id);
        if(errors.hasErrors()){model.addAttribute("prep",prep);response.setStatus(400);response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");return "legal/consultation";}
        service.save(user.getName(),id,consultationForm);
        flash.addFlashAttribute("draftSaved","consultation");flash.addFlashAttribute("notice","상담 준비 내용을 저장했습니다. 아래 미리보기와 PDF에 반영됩니다.");return "redirect:/notebooks/"+id+"/consultation";
    }
    @GetMapping("/notebooks/{id}/consultation/pdf") @ResponseBody
    ResponseEntity<byte[]> download(@PathVariable long id,Principal user,@RequestParam(required=false) java.util.List<String> sections,@RequestParam(defaultValue="true") boolean notes) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"consultation-"+id+".pdf\"").body(pdf.exportConsultation(user.getName(),id,PdfSelection.parse(sections,PdfSelection.CONSULTATION),notes));
    }
}
