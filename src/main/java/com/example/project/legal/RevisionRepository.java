package com.example.project.legal;
import com.example.project.legal.LegalModels.Revision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RevisionRepository extends JpaRepository<Revision,Long> {
 List<Revision> findByCaseIdOrderByIdDesc(Long id);
}
