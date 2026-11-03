package com.example.project.legal;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AiController {
    private final AiExplanationService ai;
    public AiController(AiExplanationService ai) { this.ai=ai; }
    @PostMapping("/cases/{id}/ai")
    String explain(@PathVariable long id,@RequestParam(defaultValue="summary") String mode,
                   @RequestParam(defaultValue="false") boolean consent,Principal user,Model model) {
        model.addAttribute("explanation",ai.explain(user.getName(),id,mode,consent));
        model.addAttribute("caseId",id);
        return "legal/ai-explanation";
    }
}
