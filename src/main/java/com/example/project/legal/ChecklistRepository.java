package com.example.project.legal;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.project.legal.NotebookModels.CheckItem;
import java.util.List;
public interface ChecklistRepository extends JpaRepository<CheckItem,Long>{List<CheckItem> findByNotebookIdOrderByIdAsc(Long id);void deleteByNotebookId(Long id);}
