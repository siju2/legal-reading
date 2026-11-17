package com.example.project.legal;

import com.example.project.legal.NotebookModels.Notebook;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EvidenceMapService {
    public static final List<String> KINDS=List.of("확인한 사실","내 주장","내 추측","상대방 주장");
    public record Card(String id,String kind,String title,Long eventId,String gap,String nextAction) {}
    private final NotebookRepository books;
    private final TimelineRepository events;
    private final ObjectMapper mapper;
    public EvidenceMapService(NotebookRepository books,TimelineRepository events,ObjectMapper mapper) {
        this.books=books;this.events=events;this.mapper=mapper;
    }
    public List<Card> read(Notebook book) {
        if(book.evidenceMap==null||book.evidenceMap.isBlank())return new ArrayList<>();
        try{return new ArrayList<>(Arrays.asList(mapper.readValue(book.evidenceMap,Card[].class)));}
        catch(JsonProcessingException e){throw new IllegalStateException("증거 지도를 읽을 수 없습니다.",e);}
    }
    static void validate(String kind,String title,String gap,String nextAction) {
        LegalService.require(kind!=null&&KINDS.contains(kind),"사실과 주장의 구분을 선택해 주세요.");
        LegalService.require(title!=null&&!title.isBlank()&&title.length()<=300,"내용은 1~300자로 입력해 주세요.");
        LegalService.require(gap!=null&&gap.length()<=1000&&nextAction!=null&&nextAction.length()<=1000,"미확인점과 다음 할 일은 각각 1,000자 이하로 입력해 주세요.");
    }
    void write(Notebook book,List<Card> cards) {
        LegalService.require(cards.size()<=100,"증거 지도는 노트당 100개까지 등록할 수 있습니다.");
        for(var card:cards)validate(card.kind(),card.title(),card.gap(),card.nextAction());
        try{book.evidenceMap=mapper.writeValueAsString(cards);}
        catch(JsonProcessingException e){throw new IllegalStateException("증거 지도를 저장할 수 없습니다.",e);}
    }
    private Notebook ownedForUpdate(String user,long id) {
        return books.lockById(id).filter(b->b.username.equals(user)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    @Transactional
    public void save(String user,long id,String cardId,String kind,String title,Long eventId,String gap,String nextAction) {
        var book=ownedForUpdate(user,id);validate(kind,title,gap,nextAction);
        var cards=read(book);
        int index=-1;
        if(cardId!=null) {
            for(int i=0;i<cards.size();i++)if(cards.get(i).id().equals(cardId)){index=i;break;}
            if(index<0)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if(eventId!=null)events.findById(eventId).filter(e->e.notebookId.equals(id)).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        var card=new Card(cardId==null?UUID.randomUUID().toString():cardId,kind,title.strip(),eventId,gap.strip(),nextAction.strip());
        if(index<0)cards.add(card);else cards.set(index,card);
        write(book,cards);
    }
    @Transactional
    public void delete(String user,long id,String cardId) {
        var book=ownedForUpdate(user,id);var cards=read(book);
        if(!cards.removeIf(c->c.id().equals(cardId)))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        write(book,cards);
    }
}
