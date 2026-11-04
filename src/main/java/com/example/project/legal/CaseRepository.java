package com.example.project.legal;
import com.example.project.legal.LegalModels.CaseEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CaseRepository extends JpaRepository<CaseEntry,Long> {
 List<CaseEntry> findByPublishedTrueOrderByDecisionDateDescIdDesc();
 List<CaseEntry> findAllByOrderByUpdatedAtDesc();
 default boolean existsByCaseNumber(String number){return findAll().stream().anyMatch(c->java.util.Objects.equals(c.caseNumber,number));}
}
