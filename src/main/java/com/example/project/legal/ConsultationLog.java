package com.example.project.legal;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity @Table(name="legal_consultation_logs")
public class ConsultationLog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false) public Long notebookId;
    @Column(nullable=false) public LocalDate consultedOn;
    @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT",nullable=false) public String answer;
    @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String nextActions="";
    public boolean completed;
}
