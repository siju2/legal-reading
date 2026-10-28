package com.example.project.legal;

import jakarta.persistence.*;
import java.time.*;

public final class LegalModels {
 private LegalModels() {}
 @Entity @Table(name="legal_cases")
 public static class CaseEntry {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Version public Long version;
  @Column(nullable=false,length=180) public String title;
  @Column(nullable=false,length=30) public String category;
  @Column(length=500) public String tags="";
  @Column(length=500) public String summary="";
  @Column(length=120) public String caseNumber="";
  @Column(length=80) public String court="";
  public LocalDate decisionDate;
  @Column(length=120) public String outcome="";
  @Lob @Column(columnDefinition="TEXT") public String facts="";
  @Lob @Column(columnDefinition="TEXT") public String claims="";
  @Lob @Column(columnDefinition="TEXT") public String issue="";
  @Lob @Column(columnDefinition="TEXT") public String reasoning="";
  @Lob @Column(columnDefinition="TEXT") public String comparison="";
  @Lob @Column(columnDefinition="TEXT") public String checklist="";
  @Lob @Column(columnDefinition="TEXT") public String excerpt="";
  @Column(length=1500) public String sourceUrl="";
  @Column(length=300) public String lawName="";
  @Column(length=1500) public String lawUrl="";
  public boolean demo;
  public boolean published;
  @Column(length=30) public String reviewStatus="편집 해설 · 전문가 검수 전";
  public LocalDate sourceCheckedAt;
  public LocalDateTime updatedAt=LocalDateTime.now();
 }
 @Entity @Table(name="legal_bookmarks",uniqueConstraints=@UniqueConstraint(columnNames={"username","case_id"}))
 public static class Bookmark {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false,length=30) public String username;
  @Column(name="case_id",nullable=false) public Long caseId;
  public LocalDateTime savedAt=LocalDateTime.now();
  @Column(length=3000) public String note="";
 }
 @Entity @Table(name="legal_revisions")
 public static class Revision {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false) public Long caseId;
  @Column(length=30,nullable=false) public String editor;
  @Lob @Column(columnDefinition="TEXT") public String snapshot;
  public LocalDateTime createdAt=LocalDateTime.now();
 }
}
