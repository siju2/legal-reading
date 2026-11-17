package com.example.project.legal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SituationController {
    @GetMapping("/situation")
    String page() {
        return "redirect:/legal-ai";
    }
}
