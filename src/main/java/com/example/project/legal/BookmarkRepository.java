package com.example.project.legal;
import com.example.project.legal.LegalModels.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface BookmarkRepository extends JpaRepository<Bookmark,Long> {
 Optional<Bookmark> findByUsernameAndCaseId(String username,Long caseId);
 List<Bookmark> findByUsernameOrderBySavedAtDesc(String username);
}
