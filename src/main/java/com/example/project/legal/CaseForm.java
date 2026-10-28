package com.example.project.legal;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
public class CaseForm {
 public Long version;
 @NotBlank @Size(max=180) public String title="";
 @NotBlank @Size(max=30) public String category="주거";
 @Size(max=500) public String tags="";
 @NotBlank @Size(max=500) public String summary="";
 @Size(max=120) public String caseNumber="";
 @Size(max=80) public String court="";
 @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) public LocalDate decisionDate;
 @Size(max=120) public String outcome="";
 @NotBlank @Size(max=12000) public String facts="";
 @Size(max=12000) public String claims="";
 @NotBlank @Size(max=12000) public String issue="";
 @NotBlank @Size(max=12000) public String reasoning="";
 @Size(max=12000) public String comparison="";
 @Size(max=12000) public String checklist="";
 @Size(max=12000) public String excerpt="";
 @Size(max=1500) public String sourceUrl="";
 @Size(max=300) public String lawName="";
 @Size(max=1500) public String lawUrl="";
 public boolean demo;
 public boolean published;
 public boolean reviewed;

 public Long getVersion(){return version;}
 public void setVersion(Long value){version=value;}
 public String getTitle(){return title;}
 public void setTitle(String value){title=value;}
 public String getCategory(){return category;}
 public void setCategory(String value){category=value;}
 public String getTags(){return tags;}
 public void setTags(String value){tags=value;}
 public String getSummary(){return summary;}
 public void setSummary(String value){summary=value;}
 public String getCaseNumber(){return caseNumber;}
 public void setCaseNumber(String value){caseNumber=value;}
 public String getCourt(){return court;}
 public void setCourt(String value){court=value;}
 public LocalDate getDecisionDate(){return decisionDate;}
 public void setDecisionDate(LocalDate value){decisionDate=value;}
 public String getOutcome(){return outcome;}
 public void setOutcome(String value){outcome=value;}
 public String getFacts(){return facts;}
 public void setFacts(String value){facts=value;}
 public String getClaims(){return claims;}
 public void setClaims(String value){claims=value;}
 public String getIssue(){return issue;}
 public void setIssue(String value){issue=value;}
 public String getReasoning(){return reasoning;}
 public void setReasoning(String value){reasoning=value;}
 public String getComparison(){return comparison;}
 public void setComparison(String value){comparison=value;}
 public String getChecklist(){return checklist;}
 public void setChecklist(String value){checklist=value;}
 public String getExcerpt(){return excerpt;}
 public void setExcerpt(String value){excerpt=value;}
 public String getSourceUrl(){return sourceUrl;}
 public void setSourceUrl(String value){sourceUrl=value;}
 public String getLawName(){return lawName;}
 public void setLawName(String value){lawName=value;}
 public String getLawUrl(){return lawUrl;}
 public void setLawUrl(String value){lawUrl=value;}
 public boolean isDemo(){return demo;}
 public void setDemo(boolean value){demo=value;}
 public boolean isPublished(){return published;}
 public void setPublished(boolean value){published=value;}
 public boolean isReviewed(){return reviewed;}
 public void setReviewed(boolean value){reviewed=value;}
}
