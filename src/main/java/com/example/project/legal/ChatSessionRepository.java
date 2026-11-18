package com.example.project.legal;

import com.example.project.legal.NotebookModels.ChatSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession,Long> {
    List<ChatSession> findTop50ByNotebookIdOrderByUpdatedAtDesc(Long notebookId);
    List<ChatSession> findByNotebookIdInOrderByUpdatedAtDesc(List<Long> notebookIds);
    Optional<ChatSession> findByIdAndNotebookId(Long id,Long notebookId);
    long countByNotebookId(Long notebookId);
    void deleteByNotebookId(Long notebookId);
}
