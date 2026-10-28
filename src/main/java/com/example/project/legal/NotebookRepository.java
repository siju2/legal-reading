package com.example.project.legal;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.project.legal.NotebookModels.Notebook;
import java.util.List;
public interface NotebookRepository extends JpaRepository<Notebook,Long>{List<Notebook> findByUsernameOrderByCreatedAtDesc(String username);}
