package com.example.project.legal;
import com.example.project.legal.LegalModels.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.*;
import java.time.LocalDate;

@Controller
public class LegalController {
 private final LegalService service;private final CaseRepository cases;private final BookmarkRepository bookmarks;private final RevisionRepository revisions;
 public LegalController(LegalService s,CaseRepository c,BookmarkRepository b,RevisionRepository r){service=s;cases=c;bookmarks=b;revisions=r;}
 @ModelAttribute void nav(Model m,Authentication auth){m.addAttribute("viewer",auth==null?null:auth.getName());m.addAttribute("isAdmin",admin(auth));m.addAttribute("categories",LegalService.CATEGORIES);}
 private boolean admin(Authentication a){return a!=null&&a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_ADMIN"));}
 @GetMapping("/") String home(Model m){var all=service.search("","","all");m.addAttribute("recent",all.stream().filter(c->!c.demo).limit(3).toList());m.addAttribute("examples",all.stream().filter(c->c.demo).limit(3).toList());m.addAttribute("realCount",all.stream().filter(c->!c.demo).count());m.addAttribute("demoCount",all.stream().filter(c->c.demo).count());return "legal/home";}
 @GetMapping("/cases") String list(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String category,@RequestParam(defaultValue="all")String type,@RequestParam(defaultValue="0")int page,Model m){var all=service.search(q,category,type);int p=Math.max(0,Math.min(page,Math.max(0,(all.size()-1)/12)));m.addAttribute("results",new PageImpl<>(all.subList(Math.min(p*12,all.size()),Math.min(p*12+12,all.size())),PageRequest.of(p,12),all.size()));m.addAttribute("q",q);m.addAttribute("category",category);m.addAttribute("type",type);return "legal/list";}
 @GetMapping("/cases/{id}") String detail(@PathVariable long id,Authentication a,Model m){var c=service.entry(id,admin(a));m.addAttribute("entry",c);m.addAttribute("saved",a==null?null:bookmarks.findByUsernameAndCaseId(a.getName(),id).orElse(null));m.addAttribute("related",service.search("",c.category,"all").stream().filter(x->!x.id.equals(id)).limit(3).toList());return "legal/detail";}
 @GetMapping("/compare") String compare(@RequestParam(required=false)Long left,@RequestParam(required=false)Long right,Model m){m.addAttribute("choices",service.search("","","all"));m.addAttribute("leftId",left);m.addAttribute("rightId",right);m.addAttribute("leftCase",left==null?null:service.entry(left,false));m.addAttribute("rightCase",right==null?null:service.entry(right,false));return "legal/compare";}
 @PostMapping("/cases/{id}/bookmark") String save(@PathVariable long id,Principal p,RedirectAttributes f){service.bookmark(p.getName(),id);f.addFlashAttribute("notice","내 보관함에 저장했습니다.");return "redirect:/cases/"+id;}
 @PostMapping("/cases/{id}/unbookmark") String remove(@PathVariable long id,Principal p,RedirectAttributes f){service.unbookmark(p.getName(),id);f.addFlashAttribute("notice","보관함에서 삭제했습니다.");return "redirect:/my";}
 @PostMapping("/cases/{id}/note") String note(@PathVariable long id,@RequestParam String note,Principal p,RedirectAttributes f){service.note(p.getName(),id,note);f.addFlashAttribute("notice","나만 볼 수 있는 메모를 저장했습니다.");return "redirect:/my";}
 @GetMapping("/my") String my(Principal p,Model m){var saved=bookmarks.findByUsernameOrderBySavedAtDesc(p.getName());var entries=new LinkedHashMap<Bookmark,CaseEntry>();for(var b:saved)cases.findById(b.caseId).filter(c->c.published).ifPresent(c->entries.put(b,c));m.addAttribute("savedEntries",entries);return "legal/my";}
 @GetMapping("/glossary") String glossary(@RequestParam(defaultValue="")String q,Model m){LegalService.require(q.length()<=80,"검색어는 80자 이하로 입력해 주세요.");m.addAttribute("terms",TERMS.stream().filter(t->(t.name()+t.explanation()).contains(q)).toList());m.addAttribute("q",q);return "legal/glossary";}
 @GetMapping("/guide") String guide(){return "legal/guide";}
 @GetMapping("/admin") String admin(Model m){m.addAttribute("entries",cases.findAllByOrderByUpdatedAtDesc());return "legal/admin";}
 @GetMapping("/admin/cases/new") String editor(Model m){m.addAttribute("caseForm",new CaseForm());m.addAttribute("editing",false);return "legal/editor";}
 @GetMapping("/admin/cases/{id}/edit") String edit(@PathVariable long id,Model m){m.addAttribute("caseForm",service.form(service.entry(id,true)));m.addAttribute("editing",true);m.addAttribute("entryId",id);m.addAttribute("history",revisions.findByCaseIdOrderByIdDesc(id));return "legal/editor";}
 @PostMapping({"/admin/cases/new","/admin/cases/{id}/edit"}) String submit(@PathVariable(required=false)Long id,@Valid @ModelAttribute CaseForm caseForm,BindingResult errors,Principal p,Model m,RedirectAttributes flash){m.addAttribute("editing",id!=null);m.addAttribute("entryId",id);if(errors.hasErrors())return "legal/editor";try{var c=service.save(id,caseForm,p.getName());flash.addFlashAttribute("notice",c.published?"자료를 공개하고 변경 이력을 남겼습니다.":"초안을 저장했습니다. 공개 목록에는 표시되지 않습니다.");return "redirect:/admin";}catch(ResponseStatusException e){if(e.getStatusCode()!=HttpStatus.BAD_REQUEST)throw e;errors.reject("invalid",e.getReason());return "legal/editor";}}
 @GetMapping("/api/cases") @ResponseBody List<CaseSummary> api(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String category){return service.search(q,category,"real").stream().limit(100).map(c->new CaseSummary(c.id,c.title,c.caseNumber,c.court,c.decisionDate,c.category,c.summary,c.sourceUrl)).toList();}
 public record CaseSummary(Long id,String title,String caseNumber,String court,LocalDate decisionDate,String category,String summary,String sourceUrl){}
 public record Term(String name,String explanation,String example){}
 static final List<Term> TERMS=List.of(
 new Term("원고·피고","민사소송에서 청구를 제기한 쪽과 그 청구를 받는 쪽입니다.","형사사건의 피고인과 민사사건의 피고는 구분합니다."),
 new Term("청구","법원에 어떤 판단이나 이행을 구하는 것입니다.","빌린 돈을 돌려달라는 대여금 지급 청구"),
 new Term("입증책임·증명책임","주요 사실이 증명되지 않았을 때 불리한 판단을 감수하는 책임입니다.","무슨 약정이 있었는지를 누가 증명해야 하는지 확인합니다."),
 new Term("인용·기각·각하","인용은 청구를 받아들임, 기각은 내용상 받아들이지 않음, 각하는 절차 요건 등으로 본안 판단 없이 종료하는 것입니다.","청구 일부만 인정되는 일부 인용도 있습니다."),
 new Term("상고·항소","항소는 제1심에 대한 불복, 상고는 상급심에 대한 법률상 불복 절차입니다.","구체적인 불복 가능성과 기간은 사건별 안내로 확인합니다."),
 new Term("파기환송","상급심이 원심을 깨고 다시 심리하도록 돌려보내는 것입니다.","파기환송을 최종 승소 확정으로 읽지 않습니다."),
 new Term("동시이행","서로 맞물린 의무의 이행을 함께 문제 삼는 관계입니다.","상대 의무·이행 제공 여부를 사실관계와 함께 확인합니다."),
 new Term("소멸시효","일정 기간 권리를 행사하지 않은 경우 권리 행사에 영향을 줄 수 있는 제도입니다.","기간·기산점·완성 여부는 권리와 사실관계에 따라 다릅니다."),
 new Term("통상임금","소정근로에 대해 정기적·일률적으로 지급하기로 정한 임금으로 법정수당 산정의 기준이 됩니다.","실제 지급액 전체와 같은 개념은 아닙니다."),
 new Term("소정근로","근로자와 사용자가 정한 근로를 말합니다.","소정근로의 대가인지와 추가 근로의 대가인지를 구분합니다."),
 new Term("판시사항·판결요지","판시사항은 다룬 법적 질문, 판결요지는 주요 판단을 정리한 부분입니다.","요지만으로 사실관계 전체를 알 수는 없어 전문을 함께 봅니다."),
 new Term("가압류·강제집행","가압류는 집행을 위한 보전, 강제집행은 집행권원 등을 바탕으로 이행을 실현하는 절차입니다.","판결을 받는 것과 실제 돈을 회수하는 것은 구별합니다.") );
}
