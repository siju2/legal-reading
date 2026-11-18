package com.example.project.legal;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class LawAssistantController {
    private final LawAssistantService assistant;private final NotebookRepository books;private final NotebookService notebooks;private final IntegrationSettings settings;
    public LawAssistantController(LawAssistantService assistant,NotebookRepository books,NotebookService notebooks,IntegrationSettings settings){this.assistant=assistant;this.books=books;this.notebooks=notebooks;this.settings=settings;}
    @GetMapping("/legal-ai")
    String page(Principal user,@RequestParam(required=false) Long notebookId,@RequestParam(defaultValue="") String q,Model model,HttpServletResponse response){
        model.addAttribute("question",q.substring(0,Math.min(q.length(),2000)));
        if(notebookId!=null)notebooks.owned(user.getName(),notebookId);
        populate(user,notebookId,model,response);return "legal/law-assistant";
    }
    @PostMapping("/legal-ai")
    String ask(Principal user,@RequestParam String question,@RequestParam(required=false) Long notebookId,@RequestParam(defaultValue="false") boolean consent,@RequestParam(defaultValue="deep") String mode,Model model,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");
        model.addAttribute("answer",assistant.ask(user.getName(),question,notebookId,consent,"","deep".equals(mode)));model.addAttribute("question",question);
        populate(user,notebookId,model,response);return "legal/law-assistant";
    }
    @PostMapping("/legal-ai/chat")
    @ResponseBody
    LawAssistantService.Answer chat(Principal user,@RequestParam String question,
            @RequestParam(defaultValue="") String context,@RequestParam(required=false) Long notebookId,
            @RequestParam(defaultValue="false") boolean consent,@RequestParam(defaultValue="deep") String mode,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");
        return assistant.ask(user.getName(),question,notebookId,consent,context,"deep".equals(mode));
    }
    private void populate(Principal user,Long notebookId,Model model,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");model.addAttribute("notebooks",books.findTop100ByUsernameOrderByCreatedAtDesc(user.getName()));
        model.addAttribute("selectedNotebookId",notebookId);model.addAttribute("localReady",settings.localAi()&&settings.aiReady());
    }
}
