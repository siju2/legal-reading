package com.example.project.legal;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.project.legal.NotebookModels.Event;
import java.util.List;
public interface TimelineRepository extends JpaRepository<Event,Long>{List<Event> findByNotebookIdOrderByOccurredOnAscIdAsc(Long id);void deleteByNotebookId(Long id);}
