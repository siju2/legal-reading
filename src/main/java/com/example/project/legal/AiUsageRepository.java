package com.example.project.legal;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUsageRepository extends JpaRepository<AiUsage,Long> {
    Optional<AiUsage> findByUsernameAndDate(String username, LocalDate date);
}
