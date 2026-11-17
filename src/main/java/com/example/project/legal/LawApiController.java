package com.example.project.legal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@Controller public class LawApiController {
 private final LawApiService api;
 public LawApiController(LawApiService a){api=a;}
 @GetMapping("/law-search") String page(@RequestParam(defaultValue="") String q,org.springframework.web.servlet.mvc.support.RedirectAttributes redirect){
  if(!q.isBlank())redirect.addAttribute("q",q.substring(0,Math.min(q.length(),2000)));
  return "redirect:/legal-ai";
 }
 @GetMapping("/law-search/precedents/{id}") String detail(@PathVariable String id,Model m){m.addAttribute("apiReady",api.configured());try{m.addAttribute("external",api.detail(id));}catch(ResponseStatusException e){m.addAttribute("apiError",e.getReason());}return "legal/external-case";}
 @GetMapping("/api/law/search") @ResponseBody LawApiService.Search search(@RequestParam(defaultValue="prec")String kind,@RequestParam String q,@RequestParam(defaultValue="1")int page){return api.search(kind,q,page);}
 @GetMapping("/api/law/status") @ResponseBody java.util.Map<String,Object> status(){return java.util.Map.of("configured",api.configured(),"provider","법제처 국가법령정보 공동활용");}
}
