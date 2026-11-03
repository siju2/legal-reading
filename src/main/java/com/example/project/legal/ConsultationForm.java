package com.example.project.legal;

import jakarta.validation.constraints.Size;

public class ConsultationForm {
    @Size(max=2000,message="사건 요약은 2,000자 이하로 입력해 주세요.") private String summary="";
    @Size(max=3000,message="상담 질문은 3,000자 이하로 입력해 주세요.") private String questions="";
    @Size(max=1000,message="상담 목표는 1,000자 이하로 입력해 주세요.") private String goal="";
    public String getSummary(){return summary;}
    public void setSummary(String value){summary=value;}
    public String getQuestions(){return questions;}
    public void setQuestions(String value){questions=value;}
    public String getGoal(){return goal;}
    public void setGoal(String value){goal=value;}
}
