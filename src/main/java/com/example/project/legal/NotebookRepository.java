package com.example.project.legal;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.project.legal.NotebookModels.Notebook;
import java.util.List;
public interface NotebookRepository extends JpaRepository<Notebook,Long>{
 List<Notebook> findByUsernameOrderByCreatedAtDesc(String username);
 @org.springframework.data.jpa.repository.Query("""
  select b from Notebook b where b.username=:user and (
   lower(b.title) like :term escape '!' or lower(b.category) like :term escape '!'
   or lower(coalesce(b.consultationSummary,'')) like :term escape '!'
   or lower(coalesce(b.consultationQuestions,'')) like :term escape '!'
   or lower(coalesce(b.consultationGoal,'')) like :term escape '!'
   or exists(select e.id from Event e where e.notebookId=b.id and (lower(e.title) like :term escape '!' or lower(e.note) like :term escape '!'))
   or exists(select c.id from CheckItem c where c.notebookId=b.id and lower(c.label) like :term escape '!')
   or exists(select l.id from ConsultationLog l where l.notebookId=b.id and (lower(l.answer) like :term escape '!' or lower(l.nextActions) like :term escape '!')))
  """)
 org.springframework.data.domain.Page<Notebook> searchOwned(@org.springframework.data.repository.query.Param("user") String user,@org.springframework.data.repository.query.Param("term") String term,org.springframework.data.domain.Pageable pageable);
}
