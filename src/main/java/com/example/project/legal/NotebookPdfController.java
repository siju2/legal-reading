package com.example.project.legal;

import java.security.Principal;
import java.time.LocalDate;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class NotebookPdfController {
    private final NotebookPdfService pdf;
    public NotebookPdfController(NotebookPdfService pdf) {this.pdf=pdf;}
    @GetMapping("/notebooks/{id}/pdf") @ResponseBody ResponseEntity<byte[]> download(@PathVariable long id,Principal user,@RequestParam(required=false) java.util.List<String> sections,@RequestParam(defaultValue="true") boolean notes) {
        byte[] body=pdf.export(user.getName(),id,PdfSelection.parse(sections,PdfSelection.NOTEBOOK),notes);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"notebook-"+id+".pdf\"").body(body);
    }
    @PostMapping("/notebooks/{id}/pdf-preview") String preview(@PathVariable long id,@RequestParam MultipartFile file,Principal user,Model model,HttpServletResponse response) {
        model.addAttribute("preview",pdf.extract(user.getName(),id,file));model.addAttribute("today",LocalDate.now());
        response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");return "legal/pdf-preview";
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class) String oversized(Model model,HttpServletResponse response) {
        response.setStatus(413);model.addAttribute("code",413);model.addAttribute("message","PDF는 10MB 이하로 업로드해 주세요.");return "legal/error";
    }
}
