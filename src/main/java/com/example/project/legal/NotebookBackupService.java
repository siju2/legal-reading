package com.example.project.legal;

import com.example.project.legal.NotebookModels.*;
import com.example.project.legal.LegalModels.CaseEntry;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotebookBackupService {
    public static final int MAX_BYTES=5*1024*1024;
    public record EventData(LocalDate date,String kind,String title,String note) {}
    public record ItemData(String label,boolean completed) {}
    public record LogData(LocalDate date,String answer,String nextActions,boolean completed) {}
    public record EvidenceData(String kind,String title,Integer eventIndex,String gap,String nextAction) {}
    public record ChatData(String title,List<ChatSessionService.Turn> turns) {}
    public record BookData(String title,String category,Long sourceCaseId,String summary,String questions,String goal,List<EventData> events,List<ItemData> items,List<LogData> followups,List<EvidenceData> evidence,List<ChatData> chats) {}
    public record Backup(String format,int version,List<BookData> notebooks) {}
    private final NotebookService service;private final NotebookRepository books;private final TimelineRepository events;
    private final ChecklistRepository checks;private final ConsultationLogRepository logs;private final ChatSessionRepository chats;private final CaseRepository cases;private final ObjectMapper mapper;private final EvidenceMapService evidence;
    public NotebookBackupService(NotebookService service,NotebookRepository books,TimelineRepository events,ChecklistRepository checks,ConsultationLogRepository logs,ChatSessionRepository chats,CaseRepository cases,ObjectMapper mapper,EvidenceMapService evidence){
        this.service=service;this.books=books;this.events=events;this.checks=checks;this.logs=logs;this.chats=chats;this.cases=cases;this.evidence=evidence;
        this.mapper=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
    @Transactional(readOnly=true)
    public byte[] export(String user,Long id){
        List<Notebook> notebooks=id==null?books.findTop101ByUsernameOrderByCreatedAtDesc(user):List.of(service.owned(user,id));
        LegalService.require(notebooks.size()<=100,"한 번에 100개까지 백업할 수 있습니다. 노트별 백업을 이용해 주세요.");
        var ids=notebooks.stream().map(b->b.id).toList();
        var timelines=ids.isEmpty()?List.<Event>of():events.findByNotebookIdInOrderByOccurredOnAscIdAsc(ids);
        var items=ids.isEmpty()?List.<CheckItem>of():checks.findByNotebookIdInOrderByIdAsc(ids);
        var followups=ids.isEmpty()?List.<ConsultationLog>of():logs.findByNotebookIdInOrderByConsultedOnAscIdAsc(ids);
        var savedChats=ids.isEmpty()?List.<NotebookModels.ChatSession>of():chats.findByNotebookIdInOrderByUpdatedAtDesc(ids);
        var sourceIds=notebooks.stream().map(b->b.sourceCaseId).filter(Objects::nonNull).distinct().toList();
        var sourceCases=sourceIds.isEmpty()?Map.<Long,CaseEntry>of():cases.findAllById(sourceIds).stream().collect(java.util.stream.Collectors.toMap(c->c.id,c->c));
        var timelineByBook=timelines.stream().collect(java.util.stream.Collectors.groupingBy(e->e.notebookId));
        var itemsByBook=items.stream().collect(java.util.stream.Collectors.groupingBy(c->c.notebookId));
        var followupsByBook=followups.stream().collect(java.util.stream.Collectors.groupingBy(l->l.notebookId));
        var chatsByBook=savedChats.stream().collect(java.util.stream.Collectors.groupingBy(c->c.notebookId));
        var data=notebooks.stream().map(b->bookData(b,timelineByBook.getOrDefault(b.id,List.of()),itemsByBook.getOrDefault(b.id,List.of()),followupsByBook.getOrDefault(b.id,List.of()),chatsByBook.getOrDefault(b.id,List.of()),sourceCases)).toList();
        Backup backup=new Backup("legal-notebooks",1,data);validate(backup);
        try {byte[] bytes=mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(backup);LegalService.require(bytes.length<=MAX_BYTES,"백업이 5MB를 초과했습니다. 노트별로 백업해 주세요.");return bytes;}
        catch(IOException e){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"백업 파일을 만들지 못했습니다.");}
    }
    private BookData bookData(Notebook b,List<Event> timeline,List<CheckItem> items,List<ConsultationLog> followups,List<NotebookModels.ChatSession> chats,Map<Long,CaseEntry> sourceCases) {
        var positions=new HashMap<Long,Integer>();for(int i=0;i<timeline.size();i++)positions.put(timeline.get(i).id,i);
        return new BookData(b.title,b.category,b.sourceCaseId==null?null:publicSource(sourceCases.get(b.sourceCaseId),b.category),text(b.consultationSummary),text(b.consultationQuestions),text(b.consultationGoal),
            timeline.stream().map(e->new EventData(e.occurredOn,e.kind,e.title,e.note)).toList(),
            items.stream().map(c->new ItemData(c.label,c.completed)).toList(),
            followups.stream().map(l->new LogData(l.consultedOn,l.answer,l.nextActions,l.completed)).toList(),
            evidence.read(b).stream().map(c->new EvidenceData(c.kind(),c.title(),positions.get(c.eventId()),c.gap(),c.nextAction())).toList(),
            chats.stream().map(c->{try{return new ChatData(c.title,mapper.readValue(c.transcript,new TypeReference<List<ChatSessionService.Turn>>(){}));}catch(IOException e){throw new IllegalStateException("저장한 대화를 백업에 포함하지 못했습니다.",e);}}).toList());
    }
    @Transactional
    public int restore(String user,byte[] bytes){
        LegalService.require(bytes.length>0&&bytes.length<=MAX_BYTES,"5MB 이하의 노트 백업 JSON 파일을 선택해 주세요.");
        Backup backup;
        try{backup=mapper.readValue(bytes,Backup.class);}catch(IOException|IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"올바른 판결읽기 백업 파일이 아닙니다.");}
        validate(backup);
        for(var data:backup.notebooks()){
            var b=new Notebook();b.username=user;b.title=data.title().strip();b.category=data.category();b.sourceCaseId=publicSource(data.sourceCaseId(),data.category());
            b.consultationSummary=text(data.summary());b.consultationQuestions=text(data.questions());b.consultationGoal=text(data.goal());books.saveAndFlush(b);
            var eventIds=new ArrayList<Long>();
            for(var e:data.events()){var row=new Event();row.notebookId=b.id;row.occurredOn=e.date();row.kind=e.kind();row.title=e.title().strip();row.note=text(e.note());events.save(row);eventIds.add(row.id);}
            if(data.evidence()!=null)evidence.write(b,data.evidence().stream().map(c->new EvidenceMapService.Card(UUID.randomUUID().toString(),c.kind(),c.title().strip(),c.eventIndex()==null?null:eventIds.get(c.eventIndex()),c.gap().strip(),c.nextAction().strip())).toList());
            for(var c:data.items()){var row=new CheckItem();row.notebookId=b.id;row.label=c.label().strip();row.completed=c.completed();checks.save(row);}
            for(var l:data.followups()){var row=new ConsultationLog();row.notebookId=b.id;row.consultedOn=l.date();row.answer=l.answer().strip();row.nextActions=text(l.nextActions());row.completed=l.completed();logs.save(row);}
            if(data.chats()!=null)for(var c:data.chats()){var row=new NotebookModels.ChatSession();row.notebookId=b.id;row.title=c.title().strip();try{row.transcript=mapper.writeValueAsString(c.turns());}catch(IOException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"저장한 대화를 복원하지 못했습니다.");}chats.save(row);}
        }
        return backup.notebooks().size();
    }
    private void validate(Backup backup){
        LegalService.require(backup!=null&&"legal-notebooks".equals(backup.format())&&backup.version()==1&&backup.notebooks()!=null&&backup.notebooks().size()<=100,"지원하는 판결읽기 백업 형식(version 1)을 확인해 주세요.");
        for(var b:backup.notebooks()){
            LegalService.require(b!=null,"백업 노트가 올바르지 않습니다.");required(b.title(),120,"노트 제목");LegalService.require(b.category()!=null&&LegalService.CATEGORIES.contains(b.category()),"백업 노트의 분야를 확인해 주세요.");
            length(b.summary(),2000,"사건 요약");length(b.questions(),3000,"상담 질문");length(b.goal(),1000,"상담 목표");
            LegalService.require(b.events()!=null&&b.items()!=null&&b.items().size()<=200&&b.followups()!=null&&b.followups().size()<=100,"날짜별 기록을 확인해 주세요. 노트별 자료는 200개, 상담 후 기록은 100개 이하여야 합니다.");
            LegalService.require(b.chats()==null||b.chats().size()<=50,"노트별 저장 대화는 50개 이하만 복원할 수 있습니다.");
            if(b.chats()!=null)for(var chat:b.chats()){LegalService.require(chat!=null,"저장 대화를 확인해 주세요.");required(chat.title(),80,"대화 제목");ChatSessionService.validate(chat.turns());}
            if(b.evidence()!=null) {
                LegalService.require(b.evidence().size()<=100,"증거 지도는 노트당 100개까지 복원할 수 있습니다.");
                for(var c:b.evidence()) {
                    LegalService.require(c!=null,"증거 지도 항목을 확인해 주세요.");
                    EvidenceMapService.validate(c.kind(),c.title(),c.gap(),c.nextAction());
                    LegalService.require(c.eventIndex()==null||(c.eventIndex()>=0&&c.eventIndex()<b.events().size()),"증거 지도의 연결 기록을 확인해 주세요.");
                }
            }
            for(var e:b.events()){LegalService.require(e!=null,"날짜별 기록을 확인해 주세요.");date(e.date());LegalService.require(e.kind()!=null&&NotebookService.KINDS.contains(e.kind()),"기록 유형을 확인해 주세요.");required(e.title(),120,"기록 제목");length(e.note(),2000,"기록 메모");}
            for(var c:b.items()){LegalService.require(c!=null,"준비 자료 항목을 확인해 주세요.");required(c.label(),250,"준비 자료 이름");}
            for(var l:b.followups()){LegalService.require(l!=null,"상담 후 기록을 확인해 주세요.");date(l.date());required(l.answer(),4000,"상담 답변");length(l.nextActions(),2000,"다음 할 일");}
        }
    }
    private Long publicSource(Long id,String category){return id==null?null:publicSource(cases.findById(id).orElse(null),category);}
    private Long publicSource(CaseEntry source,String category){return source!=null&&source.published&&Objects.equals(source.category,category)?source.id:null;}
    private void date(LocalDate date){LegalService.require(date!=null&&!date.isAfter(LocalDate.now()),"백업의 날짜는 오늘 또는 이전이어야 합니다.");}
    private void required(String value,int max,String name){LegalService.require(value!=null&&!value.isBlank(),name+"을 확인해 주세요.");length(value,max,name);}
    private void length(String value,int max,String name){LegalService.require(value==null||value.length()<=max,name+"의 글자 수를 확인해 주세요.");}
    private String text(String value){return value==null?"":value.strip();}
}
