package com.example.project.legal;

import com.example.project.legal.NotebookModels.ChatSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatSessionService {
    public record Turn(String role,String text,List<LawAssistantService.Source> sources) {}
    public record Summary(Long id,String title,LocalDateTime updatedAt) {}
    public record Saved(Long id,String title,List<Turn> turns) {}
    private static final TypeReference<List<Turn>> TURNS=new TypeReference<>() {};
    private final ChatSessionRepository sessions;
    private final NotebookService notebooks;
    private final ObjectMapper mapper;

    public ChatSessionService(ChatSessionRepository sessions,NotebookService notebooks,ObjectMapper mapper) {
        this.sessions=sessions;this.notebooks=notebooks;this.mapper=mapper;
    }

    @Transactional(readOnly=true)
    public List<Summary> list(String user,long notebookId) {
        notebooks.owned(user,notebookId);
        return sessions.findTop50ByNotebookIdOrderByUpdatedAtDesc(notebookId).stream()
            .map(s->new Summary(s.id,s.title,s.updatedAt)).toList();
    }

    @Transactional(readOnly=true)
    public Saved read(String user,long notebookId,long sessionId) {
        notebooks.owned(user,notebookId);
        var session=sessions.findByIdAndNotebookId(sessionId,notebookId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {return new Saved(session.id,session.title,mapper.readValue(session.transcript,TURNS));}
        catch(Exception e) {throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"저장한 대화를 읽지 못했습니다.");}
    }

    @Transactional
    public Summary save(String user,long notebookId,Long sessionId,String json) {
        notebooks.owned(user,notebookId);
        LegalService.require(json!=null&&json.length()<=200_000,"대화 저장 크기를 확인해 주세요.");
        List<Turn> turns;
        try {turns=mapper.readValue(json,TURNS);}
        catch(Exception e) {throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"대화 내용을 확인해 주세요.");}
        validate(turns);
        ChatSession session;
        if(sessionId==null) {
            LegalService.require(sessions.countByNotebookId(notebookId)<50,"사건 노트에는 대화 50개까지 저장할 수 있습니다. 오래된 대화를 삭제한 뒤 다시 저장해 주세요.");
            session=new ChatSession();session.notebookId=notebookId;
        } else session=sessions.findByIdAndNotebookId(sessionId,notebookId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        String first=turns.stream().filter(t->"사용자".equals(t.role())).map(Turn::text).findFirst().orElse("저장한 법률 대화").strip();
        session.title=first.substring(0,Math.min(80,first.length()));
        try {session.transcript=mapper.writeValueAsString(turns);}
        catch(Exception e) {throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"대화를 저장하지 못했습니다.");}
        session.updatedAt=LocalDateTime.now();
        sessions.save(session);
        return new Summary(session.id,session.title,session.updatedAt);
    }

    @Transactional
    public void delete(String user,long notebookId,long sessionId) {
        notebooks.owned(user,notebookId);
        sessions.delete(sessions.findByIdAndNotebookId(sessionId,notebookId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    static void validate(List<Turn> turns) {
        LegalService.require(turns!=null&&!turns.isEmpty()&&turns.size()<=50,"대화는 1~50개 메시지까지 저장할 수 있습니다.");
        int size=0;
        for(Turn turn:turns) {
            LegalService.require(turn!=null&&("사용자".equals(turn.role())||"AI".equals(turn.role()))&&turn.text()!=null&&!turn.text().isBlank()&&turn.text().length()<=4000,"대화 내용을 확인해 주세요.");
            size+=turn.text().length();
            List<LawAssistantService.Source> sources=turn.sources()==null?List.of():turn.sources();
            LegalService.require(sources.size()<=10,"메시지당 근거 자료는 10개까지 저장할 수 있습니다.");
            for(var source:sources) {
                LegalService.require("AI".equals(turn.role())&&source!=null&&source.id()!=null&&source.id().length()<=100&&source.title()!=null&&source.title().length()<=300&&source.text()!=null&&source.text().length()<=4000&&source.url()!=null&&source.url().length()<=2000&&(source.checkedAt()==null||source.checkedAt().length()<=40)&&(source.keywords()==null||source.keywords().size()<=30),"근거 자료를 확인해 주세요.");
                try {var uri=URI.create(source.url());LegalService.require("https".equalsIgnoreCase(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null,"공식 근거 링크를 확인해 주세요.");}
                catch(IllegalArgumentException e) {throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"공식 근거 링크를 확인해 주세요.");}
                if(source.keywords()!=null)for(String keyword:source.keywords()){LegalService.require(keyword!=null&&keyword.length()<=80,"근거 검색어를 확인해 주세요.");size+=keyword.length();}
                size+=source.id().length()+source.title().length()+source.text().length()+source.url().length()+(source.checkedAt()==null?0:source.checkedAt().length());
            }
        }
        LegalService.require(size<=100_000,"대화가 너무 깁니다. 새 대화를 시작해 주세요.");
    }
}
