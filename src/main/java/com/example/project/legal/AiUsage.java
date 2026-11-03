package com.example.project.legal;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity @Table(name="legal_ai_usage", uniqueConstraints=@UniqueConstraint(columnNames={"username","usage_date"}))
public class AiUsage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false,length=30) public String username;
    @Column(name="usage_date",nullable=false) public LocalDate date;
    public int attempts;
}
