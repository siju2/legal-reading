package com.example.project.legal;
import jakarta.persistence.*;
import java.time.*;
public final class NotebookModels {
 private NotebookModels(){}
 @org.hibernate.annotations.DynamicUpdate @Entity(name="Notebook") @Table(name="legal_notebooks") public static class Notebook {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false,length=30) public String username;
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT",nullable=false) public String title;
  @Column(nullable=false,length=30) public String category;
  public Long sourceCaseId;
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String evidenceMap;
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String consultationSummary;
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String consultationQuestions;
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String consultationGoal;
  public LocalDateTime createdAt=LocalDateTime.now();
 }
 @Entity(name="Event") @Table(name="legal_timeline") public static class Event {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false) public Long notebookId;
  @Column(nullable=false) public LocalDate occurredOn;
  @Column(nullable=false,length=20) public String kind;
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT",nullable=false) public String title;
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT") public String note="";
 }
 @Entity(name="CheckItem") @Table(name="legal_checklist") public static class CheckItem {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false) public Long notebookId;
  @Convert(converter=com.example.project.security.EncryptedTextConverter.class) @Column(columnDefinition="LONGTEXT",nullable=false) public String label;
  public boolean completed;
 }
}
