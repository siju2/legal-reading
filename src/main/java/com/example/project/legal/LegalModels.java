package com.example.project.legal;

import jakarta.persistence.*;
import java.time.*;

public final class LegalModels {
 private LegalModels() {}
 @Entity @Table(name="legal_cases")
 public static class CaseEntry {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Version public Long version;
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT",nullable=false) public String title;
  @Column(nullable=false,length=30) public String category;
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String tags="";
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String summary="";
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String caseNumber="";
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String court="";
  public LocalDate decisionDate;
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String outcome="";
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String facts="";
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String claims="";
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String issue="";
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String reasoning="";
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String comparison="";
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String checklist="";
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String excerpt="";
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String sourceUrl="";
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String lawName="";
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String lawUrl="";
  public boolean demo;
  public boolean published;
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String reviewStatus="편집 해설 · 전문가 검수 전";
  public LocalDate sourceCheckedAt;
  public LocalDateTime updatedAt=LocalDateTime.now();
 }
 @Entity @Table(name="legal_bookmarks",uniqueConstraints=@UniqueConstraint(columnNames={"username","case_id"}))
 public static class Bookmark {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false,length=30) public String username;
  @Column(name="case_id",nullable=false) public Long caseId;
  public LocalDateTime savedAt=LocalDateTime.now();
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String note="";
 }
 @Entity @Table(name="legal_revisions")
 public static class Revision {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false) public Long caseId;
  @Column(length=30,nullable=false) public String editor;
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String snapshot;
  public LocalDateTime createdAt=LocalDateTime.now();
 }
}
