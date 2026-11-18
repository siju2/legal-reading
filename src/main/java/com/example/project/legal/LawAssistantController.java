package com.example.project.legal;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class LawAssistantController {
    private final LawAssistantService assistant;private final NotebookRepository books;private final NotebookService notebooks;private final IntegrationSettings settings;private final ChatSessionService chatSessions;
    public LawAssistantController(LawAssistantService assistant,NotebookRepository books,NotebookService notebooks,IntegrationSettings settings,ChatSessionService chatSessions){this.assistant=assistant;this.books=books;this.notebooks=notebooks;this.settings=settings;this.chatSessions=chatSessions;}
    @GetMapping("/legal-ai")
    String page(Principal user,@RequestParam(required=false) Long notebookId,@RequestParam(required=false) Long sessionId,@RequestParam(defaultValue="") String q,Model model,HttpServletResponse response){
        model.addAttribute("question",q.substring(0,Math.min(q.length(),2000)));
        if(notebookId!=null)notebooks.owned(user.getName(),notebookId);
        if(sessionId!=null&&notebookId==null)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
        populate(user,notebookId,model,response);
        if(sessionId!=null)model.addAttribute("chatTurns",chatSessions.read(user.getName(),notebookId,sessionId).turns());
        else model.addAttribute("chatTurns",java.util.List.of());
        model.addAttribute("selectedSessionId",sessionId);return "legal/law-assistant";
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
    @PostMapping("/legal-ai/sessions") @ResponseBody
    ChatSessionService.Summary saveChat(Principal user,@RequestParam long notebookId,@RequestParam(required=false) Long sessionId,
            @RequestParam String turns,HttpServletResponse response){response.setHeader("Cache-Control","no-store");return chatSessions.save(user.getName(),notebookId,sessionId,turns);}
    @PostMapping("/legal-ai/sessions/{sessionId}/delete") @ResponseBody
    void deleteChat(Principal user,@PathVariable long sessionId,@RequestParam long notebookId,HttpServletResponse response){response.setHeader("Cache-Control","no-store");chatSessions.delete(user.getName(),notebookId,sessionId);}
    private void populate(Principal user,Long notebookId,Model model,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");model.addAttribute("notebooks",books.findTop100ByUsernameOrderByCreatedAtDesc(user.getName()));
        model.addAttribute("savedChats",notebookId==null?java.util.List.of():chatSessions.list(user.getName(),notebookId));
        model.addAttribute("chatTurns",java.util.List.of());model.addAttribute("selectedSessionId",null);
        model.addAttribute("selectedNotebookId",notebookId);model.addAttribute("localReady",settings.localAi()&&settings.aiReady());
    }
}
