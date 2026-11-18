package com.example.project.legal;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.project.legal.NotebookModels.Notebook;
import java.util.List;
public interface NotebookRepository extends JpaRepository<Notebook,Long>{
 List<Notebook> findByUsernameOrderByCreatedAtDesc(String username);
 List<Notebook> findByUsernameOrderByCreatedAtDescIdDesc(String username);
 List<Notebook> findTop101ByUsernameOrderByCreatedAtDesc(String username);
 interface Choice { Long getId(); String getTitle(); }
 List<Choice> findTop100ByUsernameOrderByCreatedAtDesc(String username);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select n from Notebook n where n.id=:id")
 java.util.Optional<Notebook> lockById(@org.springframework.data.repository.query.Param("id") Long id);

}
