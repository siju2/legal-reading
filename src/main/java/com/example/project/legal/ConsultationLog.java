package com.example.project.legal;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity @Table(name="legal_consultation_logs")
public class ConsultationLog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false) public Long notebookId;
    @Column(nullable=false) public LocalDate consultedOn;
    @Column(nullable=false,length=4000) public String answer;
    @Column(length=2000) public String nextActions="";
    public boolean completed;
}
