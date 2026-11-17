package com.example.project.legal;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class EvidenceMapController {
    private final EvidenceMapService evidence;
    public EvidenceMapController(EvidenceMapService evidence){this.evidence=evidence;}
    @PostMapping({"/notebooks/{id}/evidence","/notebooks/{id}/evidence/{cardId}"})
    String save(@PathVariable long id,@PathVariable(required=false) String cardId,Principal user,
        @RequestParam String kind,@RequestParam String title,@RequestParam(required=false) Long eventId,
        @RequestParam(defaultValue="") String gap,@RequestParam(defaultValue="") String nextAction,RedirectAttributes flash) {
        evidence.save(user.getName(),id,cardId,kind,title,eventId,gap,nextAction);
        flash.addFlashAttribute("notice","증거 지도 내용을 저장했습니다.");
        return "redirect:/notebooks/"+id+"#evidence-map";
    }
    @PostMapping("/notebooks/{id}/evidence/{cardId}/delete")
    String delete(@PathVariable long id,@PathVariable String cardId,Principal user,RedirectAttributes flash) {
        evidence.delete(user.getName(),id,cardId);flash.addFlashAttribute("notice","증거 지도 항목을 삭제했습니다.");
        return "redirect:/notebooks/"+id+"#evidence-map";
    }
}
