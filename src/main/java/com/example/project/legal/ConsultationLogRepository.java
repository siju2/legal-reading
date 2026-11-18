package com.example.project.legal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsultationLogRepository extends JpaRepository<ConsultationLog,Long> {
 List<ConsultationLog> findByNotebookIdIn(java.util.Collection<Long> ids);
    List<ConsultationLog> findByNotebookIdInOrderByConsultedOnAscIdAsc(java.util.Collection<Long> ids);

    List<ConsultationLog> findByNotebookIdOrderByConsultedOnAscIdAsc(Long notebookId);
    void deleteByNotebookId(Long notebookId);
}
