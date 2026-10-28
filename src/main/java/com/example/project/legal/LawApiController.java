package com.example.project.legal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
@Controller public class LawApiController {
 private final LawApiService api;
 public LawApiController(LawApiService a){api=a;}
 @ModelAttribute void nav(Model m,Authentication a){m.addAttribute("viewer",a==null?null:a.getName());m.addAttribute("isAdmin",a!=null&&a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_ADMIN")));m.addAttribute("categories",LegalService.CATEGORIES);}
 @GetMapping("/law-search") String page(@RequestParam(defaultValue="prec")String kind,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="1")int page,Model m){LawApiService.validate(kind,q,page);m.addAttribute("kind",kind);m.addAttribute("q",q);m.addAttribute("page",page);boolean ready=api.configured();m.addAttribute("apiReady",ready);if(ready&&!q.isBlank()){try{m.addAttribute("result",api.search(kind,q,page));}catch(ResponseStatusException e){m.addAttribute("apiError",e.getReason());}}return "legal/law-search";}
 @GetMapping("/law-search/precedents/{id}") String detail(@PathVariable String id,Model m){m.addAttribute("apiReady",api.configured());try{m.addAttribute("external",api.detail(id));}catch(ResponseStatusException e){m.addAttribute("apiError",e.getReason());}return "legal/external-case";}
 @GetMapping("/api/law/search") @ResponseBody LawApiService.Search search(@RequestParam(defaultValue="prec")String kind,@RequestParam String q,@RequestParam(defaultValue="1")int page){return api.search(kind,q,page);}
 @GetMapping("/api/law/status") @ResponseBody java.util.Map<String,Object> status(){return java.util.Map.of("configured",api.configured(),"provider","법제처 국가법령정보 공동활용");}
}
