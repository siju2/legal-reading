package com.example.project.legal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.*;
@Controller public class SituationController {
 private final LegalService legal;
 public SituationController(LegalService l){legal=l;}
 @ModelAttribute void nav(Model m,Authentication a){m.addAttribute("viewer",a==null?null:a.getName());m.addAttribute("isAdmin",a!=null&&a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_ADMIN")));m.addAttribute("categories",LegalService.CATEGORIES);}
 @GetMapping("/situation") String page(@RequestParam(defaultValue="")String topic,@RequestParam(defaultValue="unknown")String agreement,@RequestParam(defaultValue="unknown")String evidence,Model m){
  LegalService.require(topic.isBlank()||TOPICS.containsKey(topic),"상황을 선택해 주세요.");LegalService.require(List.of("yes","no","unknown").contains(agreement)&&List.of("yes","no","unknown").contains(evidence),"선택한 답변을 확인해 주세요.");m.addAttribute("topics",TOPICS);m.addAttribute("topic",topic);m.addAttribute("agreement",agreement);m.addAttribute("evidence",evidence);
  if(!topic.isBlank()){var t=TOPICS.get(topic);var questions=new ArrayList<String>();questions.add(t.question());questions.add(agreement.equals("yes")?"작성한 문서의 원본·특약·체결 시점을 확인해 보세요.":"문서가 없거나 불확실하면 당시 대화와 합의 경위를 정리해 보세요.");questions.add(evidence.equals("yes")?"가지고 있는 자료의 작성일·상대방·원본 여부를 정리해 보세요.":"어떤 기록이 남아 있는지 확인하되 자료를 새로 만들거나 사실을 바꾸지 마세요.");m.addAttribute("questions",questions);m.addAttribute("selection",t);m.addAttribute("matches",legal.search(t.query(),t.category(),"all"));}
  return "legal/situation";
 }
 public record Topic(String label,String category,String query,String question){}
 static final Map<String,Topic> TOPICS;
 static {var m=new LinkedHashMap<String,Topic>();m.put("deposit",new Topic("보증금·임대차 정산","주거","보증금","계약 종료·통지·인도·정산에 관한 사실과 시점을 구분해 보세요."));m.put("wage",new Topic("월급·상여금·수당","일자리","임금","임금 항목의 지급 규칙, 근로 기간, 실제 근무 기록을 비교해 보세요."));m.put("loan",new Topic("빌려준 돈·지급 약정","돈·거래","대여금","송금 사실과 별도로 어떤 반환·지급 약정을 주장하는지 정리해 보세요."));m.put("trade",new Topic("중고거래·물품 상태","돈·거래","하자","거래 전 설명·합의와 인수 상태·문제 발생 시점을 나누어 보세요."));m.put("post",new Topic("온라인 게시글·표현","온라인 생활","게시글","표현 내용·대상·공개 범위·작성 시점을 확인해 보세요."));TOPICS=Collections.unmodifiableMap(m);}
}
