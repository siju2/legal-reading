package com.example.project.legal;
import com.example.project.legal.NotebookModels.Notebook;
import java.util.*;
import org.springframework.data.domain.*;

/** Search only owned records after authenticated decryption, never ciphertext LIKE. */
public class NotebookSearchService {
    private final NotebookRepository books;private final TimelineRepository events;
    private final ChecklistRepository checks;private final ConsultationLogRepository logs;
    public NotebookSearchService(NotebookRepository b,TimelineRepository e,ChecklistRepository c,ConsultationLogRepository l){books=b;events=e;checks=c;logs=l;}
    public Page<Notebook> search(String user,String query,int page){
        String term=query.strip().toLowerCase(Locale.ROOT);
        var all=books.findByUsernameOrderByCreatedAtDesc(user);
        all.sort(Comparator.comparing((Notebook b)->b.createdAt).thenComparing(b->b.id).reversed());
        List<Notebook> found=all;
        if(!term.isEmpty()&&!all.isEmpty()){
            var ids=all.stream().map(b->b.id).toList();Set<Long> matching=new HashSet<>();
            for(var e:events.findByNotebookIdIn(ids))if(matches(term,e.title,e.note))matching.add(e.notebookId);
            for(var c:checks.findByNotebookIdIn(ids))if(matches(term,c.label))matching.add(c.notebookId);
            for(var l:logs.findByNotebookIdIn(ids))if(matches(term,l.answer,l.nextActions))matching.add(l.notebookId);
            found=all.stream().filter(b->matching.contains(b.id)||matches(term,b.title,b.category,b.consultationSummary,b.consultationQuestions,b.consultationGoal)).toList();
        }
        int from=Math.min(page*20,found.size()),to=Math.min(from+20,found.size());
        return new PageImpl<>(found.subList(from,to),PageRequest.of(page,20),found.size());
    }
    private static boolean matches(String term,String... values){return Arrays.stream(values).filter(Objects::nonNull).anyMatch(v->v.toLowerCase(Locale.ROOT).contains(term));}
}
