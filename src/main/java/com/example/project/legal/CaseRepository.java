package com.example.project.legal;
import com.example.project.legal.LegalModels.CaseEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CaseRepository extends JpaRepository<CaseEntry,Long> {
 List<CaseEntry> findByPublishedTrueOrderByDecisionDateDescIdDesc();
 List<CaseEntry> findAllByOrderByUpdatedAtDesc();
 boolean existsByCaseNumber(String caseNumber);
}
