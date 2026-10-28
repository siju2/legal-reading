package com.example.project.legal;
import com.example.project.legal.LegalModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.net.URI;
import java.time.*;

@Service
public class LegalService {
 public static final List<String> CATEGORIES=List.of("주거","일자리","돈·거래","온라인 생활","일상 사고");
 private final CaseRepository cases;private final BookmarkRepository bookmarks;private final RevisionRepository revisions;private final ObjectMapper json;
 public LegalService(CaseRepository c,BookmarkRepository b,RevisionRepository r,ObjectMapper j){cases=c;bookmarks=b;revisions=r;json=j;}
 public CaseEntry entry(long id,boolean admin){var c=cases.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if(!c.published&&!admin)throw new ResponseStatusException(HttpStatus.NOT_FOUND);return c;}
 public List<CaseEntry> search(String q,String category,String type){
  require(q.length()<=160,"검색어는 160자 이하로 입력해 주세요.");require(category.isBlank()||CATEGORIES.contains(category),"분야를 확인해 주세요.");require(List.of("all","real","demo").contains(type),"자료 유형을 확인해 주세요.");
  var terms=expand(q);return cases.findByPublishedTrueOrderByDecisionDateDescIdDesc().stream().filter(c->category.isBlank()||c.category.equals(category)).filter(c->type.equals("all")||(type.equals("demo")==c.demo)).filter(c->q.isBlank()||terms.stream().anyMatch(t->searchable(c).contains(t))).toList();
 }
 private String searchable(CaseEntry c){return (c.title+" "+c.summary+" "+c.tags+" "+c.issue+" "+c.reasoning+" "+c.caseNumber+" "+c.court).toLowerCase(Locale.ROOT);}
 private List<String> expand(String q){var result=new ArrayList<>(Arrays.asList(q.trim().toLowerCase(Locale.ROOT).split("\\s+")));var aliases=Map.of("월급","임금","알바","임금","집주인","보증금","전세","보증금","빌려","대여금","중고","하자","악플","모욕","상여금","통상임금");aliases.forEach((k,v)->{if(q.contains(k))result.add(v);});result.removeIf(String::isBlank);return result;}
 public static boolean officialUrl(String value){try{var u=URI.create(value);String h=u.getHost();return "https".equals(u.getScheme())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&h!=null&&(h.equals("law.go.kr")||h.equals("www.law.go.kr")||h.equals("easylaw.go.kr")||h.equals("www.easylaw.go.kr")||h.equals("scourt.go.kr")||h.endsWith(".scourt.go.kr"));}catch(Exception e){return false;}}
 @Transactional public CaseEntry save(Long id,CaseForm f,String editor){
  require(CATEGORIES.contains(f.category),"분야를 확인해 주세요.");require(f.sourceUrl.isBlank()||officialUrl(f.sourceUrl),"출처는 HTTPS 공식 법령·법원 주소만 입력할 수 있습니다.");require(f.lawUrl.isBlank()||officialUrl(f.lawUrl),"법령 주소를 확인해 주세요.");
  if(!f.demo&&f.published){require(!f.caseNumber.isBlank()&&!f.court.isBlank()&&f.decisionDate!=null&&!f.decisionDate.isAfter(LocalDate.now())&&!f.sourceUrl.isBlank()&&!f.excerpt.isBlank(),"실제 판결 공개에는 사건번호·법원·선고일·공식 출처·근거 발췌가 필요합니다.");}
  if(f.published)require(f.reviewed,"출처와 개인정보를 확인한 뒤 공개 확인란을 선택해 주세요.");
  var c=id==null?new CaseEntry():entry(id,true);if(id!=null)require(Objects.equals(c.version,f.version),"다른 수정이 먼저 저장되었습니다. 새로고침 후 다시 편집해 주세요.");
  c.title=f.title;c.category=f.category;c.tags=f.tags;c.summary=f.summary;c.caseNumber=f.demo?"":f.caseNumber;c.court=f.demo?"":f.court;c.decisionDate=f.demo?null:f.decisionDate;c.outcome=f.outcome;c.facts=f.facts;c.claims=f.claims;c.issue=f.issue;c.reasoning=f.reasoning;c.comparison=f.comparison;c.checklist=f.checklist;c.excerpt=f.excerpt;c.sourceUrl=f.sourceUrl;c.lawName=f.lawName;c.lawUrl=f.lawUrl;c.demo=f.demo;c.published=f.published;c.sourceCheckedAt=f.reviewed?LocalDate.now():null;c.reviewStatus="편집 해설 · 전문가 검수 전";c.updatedAt=LocalDateTime.now();cases.saveAndFlush(c);
  var r=new Revision();r.caseId=c.id;r.editor=editor;try{r.snapshot=json.writeValueAsString(c);}catch(Exception e){throw new IllegalStateException(e);}revisions.save(r);return c;
 }
 public CaseForm form(CaseEntry c){return json.convertValue(c,CaseForm.class);}
 @Transactional public void bookmark(String username,long id){entry(id,false);if(bookmarks.findByUsernameAndCaseId(username,id).isEmpty()){var b=new Bookmark();b.username=username;b.caseId=id;bookmarks.saveAndFlush(b);}}
 @Transactional public void unbookmark(String username,long id){bookmarks.findByUsernameAndCaseId(username,id).ifPresent(bookmarks::delete);}
 @Transactional public void note(String username,long id,String note){require(note.length()<=3000,"메모는 3,000자 이하로 입력해 주세요.");var b=bookmarks.findByUsernameAndCaseId(username,id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));b.note=note;}
 static void require(boolean condition,String message){if(!condition)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
