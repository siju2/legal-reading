package com.example.project.legal;
import com.example.project.legal.NotebookModels.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.time.LocalDate;
@Service public class NotebookService {
 public static final List<String> KINDS=List.of("계약","송금","통지","상담","기타");
 private final NotebookRepository books;private final TimelineRepository events;private final ChecklistRepository checks;private final LegalService legal;
 private final ConsultationLogRepository logs;private final ChatSessionRepository chatSessions;
 public NotebookService(NotebookRepository b,TimelineRepository e,ChecklistRepository c,LegalService l,ConsultationLogRepository logs,ChatSessionRepository chatSessions){books=b;events=e;checks=c;legal=l;this.logs=logs;this.chatSessions=chatSessions;}
 public Notebook owned(String user,long id){return books.findById(id).filter(b->b.username.equals(user)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
 @Transactional public Notebook create(String user,String title,String category,Long source){
  LegalService.require(!title.isBlank()&&title.length()<=120,"노트 제목은 1~120자로 입력해 주세요.");LegalService.require(LegalService.CATEGORIES.contains(category),"생활 분야를 확인해 주세요.");
  var b=new Notebook();b.username=user;b.title=title.strip();b.category=category;b.sourceCaseId=source;
  String checklist;
  if(source!=null){var c=legal.entry(source,false);LegalService.require(c.category.equals(category),"선택한 자료와 생활 분야를 맞춰 주세요.");checklist=c.checklist;}
  else checklist=switch(category){case "주거"->"계약서와 특약\n종료·갱신 관련 통지 기록\n입금·정산 자료\n날짜별 사실 정리";case "일자리"->"근로계약서·취업규칙\n급여명세·입금 내역\n근무 기록\n관련 대화·통지";case "돈·거래"->"거래 약정·판매 설명\n송금·결제 기록\n대화와 인수 기록\n다툼이 생긴 시점";case "온라인 생활"->"원본 게시물 주소\n작성·수정 시점\n공개 범위 기록\n관련 화면 보관";default->"발생 일시와 장소\n관련 대화·사진\n지출·처리 기록\n확인할 질문 정리";};
  books.saveAndFlush(b);for(String label:checklist.split("\\R")){if(label.isBlank())continue;var c=new CheckItem();c.notebookId=b.id;c.label=label.substring(0,Math.min(250,label.length()));checks.save(c);}return b;
 }
 @Transactional public void addEvent(String user,long id,LocalDate date,String kind,String title,String note){owned(user,id);LegalService.require(date!=null&&!date.isAfter(LocalDate.now()),"발생한 일의 날짜는 오늘 또는 이전으로 입력해 주세요.");LegalService.require(KINDS.contains(kind),"기록 유형을 확인해 주세요.");LegalService.require(!title.isBlank()&&title.length()<=120&&note.length()<=2000,"제목은 1~120자, 메모는 2,000자 이하로 입력해 주세요.");var e=new Event();e.notebookId=id;e.occurredOn=date;e.kind=kind;e.title=title.strip();e.note=note;events.save(e);}
 @Transactional public void setCompleted(String user,long id,long itemId,boolean completed){owned(user,id);var c=checks.findById(itemId).filter(x->x.notebookId.equals(id)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));c.completed=completed;}
 @Transactional public void removeEvent(String user,long id,long eventId){owned(user,id);var e=events.findById(eventId).filter(x->x.notebookId.equals(id)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));events.delete(e);}
 @Transactional public void addCheck(String user,long id,String label){owned(user,id);validLabel(label);LegalService.require(checks.findByNotebookIdOrderByIdAsc(id).size()<200,"준비 자료는 노트당 200개까지 등록할 수 있습니다.");var item=new CheckItem();item.notebookId=id;item.label=label.strip();checks.save(item);}
 @Transactional public void editCheck(String user,long id,long itemId,String label){owned(user,id);validLabel(label);var item=checks.findById(itemId).filter(c->c.notebookId.equals(id)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));item.label=label.strip();}
 @Transactional public void removeCheck(String user,long id,long itemId){owned(user,id);var item=checks.findById(itemId).filter(c->c.notebookId.equals(id)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));checks.delete(item);}
 private void validLabel(String label){LegalService.require(label!=null&&!label.isBlank()&&label.length()<=250,"준비 자료 이름은 1~250자로 입력해 주세요.");}
 @Transactional public void saveConsultationLog(String user,long id,Long logId,LocalDate date,String answer,String nextActions,boolean completed){
  owned(user,id);LegalService.require(date!=null&&!date.isAfter(LocalDate.now()),"상담 날짜는 오늘 또는 이전으로 입력해 주세요.");
  LegalService.require(answer!=null&&!answer.isBlank()&&answer.length()<=4000&&nextActions!=null&&nextActions.length()<=2000,"상담 답변은 1~4,000자, 다음 할 일은 2,000자 이하로 입력해 주세요.");
  if(logId==null)LegalService.require(logs.findByNotebookIdOrderByConsultedOnAscIdAsc(id).size()<100,"상담 후 기록은 노트당 100개까지 등록할 수 있습니다.");
  var log=logId==null?new ConsultationLog():logs.findById(logId).filter(l->l.notebookId.equals(id)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  log.notebookId=id;log.consultedOn=date;log.answer=answer.strip();log.nextActions=nextActions.strip();log.completed=completed;logs.save(log);
 }
 @Transactional public void removeConsultationLog(String user,long id,long logId){owned(user,id);var log=logs.findById(logId).filter(l->l.notebookId.equals(id)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));logs.delete(log);}
 @Transactional public void delete(String user,long id){var b=owned(user,id);events.deleteByNotebookId(id);checks.deleteByNotebookId(id);logs.deleteByNotebookId(id);chatSessions.deleteByNotebookId(id);books.delete(b);}
}
