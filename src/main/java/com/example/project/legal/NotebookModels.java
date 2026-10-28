package com.example.project.legal;
import jakarta.persistence.*;
import java.time.*;
public final class NotebookModels {
 private NotebookModels(){}
 @Entity @Table(name="legal_notebooks") public static class Notebook {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false,length=30) public String username;
  @Column(nullable=false,length=120) public String title;
  @Column(nullable=false,length=30) public String category;
  public Long sourceCaseId;
  public LocalDateTime createdAt=LocalDateTime.now();
 }
 @Entity @Table(name="legal_timeline") public static class Event {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false) public Long notebookId;
  @Column(nullable=false) public LocalDate occurredOn;
  @Column(nullable=false,length=20) public String kind;
  @Column(nullable=false,length=120) public String title;
  @Column(length=2000) public String note="";
 }
 @Entity @Table(name="legal_checklist") public static class CheckItem {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false) public Long notebookId;
  @Column(nullable=false,length=250) public String label;
  public boolean completed;
 }
}
