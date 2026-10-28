package com.example.project.legal;
import com.example.project.legal.LegalModels.CaseEntry;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Component
public class LegalInitializer implements ApplicationRunner {
 private final CaseRepository cases;
 public LegalInitializer(CaseRepository c){cases=c;}
 @Override @Transactional public void run(ApplicationArguments args){
  if(cases.count()!=0)return;
  var a=base("재직 중인 사람에게만 주는 상여금, 통상임금일까요?","일자리",false);
  a.caseNumber="2020다247190";a.court="대법원 전원합의체";a.decisionDate=LocalDate.of(2024,12,19);a.outcome="상고기각";
  a.tags="월급 임금 상여금 통상임금 재직조건 연장근로";a.summary="상여금에 재직조건이 있다는 이유만으로 통상임금에서 제외할 수 있는지 살펴본 판결입니다.";
  a.facts="정기상여금 등에 지급일 현재 재직 중일 것을 요구하는 조건이 있었고, 그 임금이 법정수당 산정 기준에 들어가는지가 다투어졌습니다.";
  a.claims="상여금의 통상임금 포함 여부에 관한 당사자들의 법적 다툼입니다. 각 당사자의 구체적 청구 범위는 원문을 확인하세요.";
  a.issue="특정 시점 재직조건 자체가 통상임금성을 부정하는가?";
  a.reasoning="대법원은 종전 판단 요소인 고정성을 제외하고 소정근로 대가성·정기성·일률성으로 판단 기준을 재정립했습니다. 재직조건만으로 제외할 수 없으며 순수 성과급과 최소 지급분 등은 구분해야 합니다.";
  a.comparison="내 임금 항목의 지급 규칙과 대상 기간을 확인하세요. 새로운 법리의 적용 시점과 병행사건 예외가 있어 이 판결을 모든 과거 임금에 그대로 적용할 수는 없습니다.";
  a.checklist="근로계약서·취업규칙·보수규정\n상여금 지급 조건과 급여명세\n분쟁 대상 근로 기간";
  a.excerpt="따라서 어떠한 임금을 지급받기 위하여 특정 시점에 재직 중이어야 한다는 조건이 부가되어 있다는 사정만으로 그 임금의 소정근로 대가성이나 통상임금성이 부정되지 않는다.";
  a.sourceUrl="https://www.law.go.kr/LSW/precInfoP.do?precSeq=600537";a.lawName="근로기준법 시행령 제6조 / 판결 당시 적용 조문은 원문 참조";a.lawUrl="https://www.law.go.kr/법령/근로기준법시행령";cases.save(a);
  var b=base("근무일수 조건이 붙은 임금은 어떻게 판단할까요?","일자리",false);
  b.caseNumber="2023다302838";b.court="대법원 전원합의체";b.decisionDate=LocalDate.of(2024,12,19);b.outcome="파기환송";
  b.tags="임금 상여금 근무일수 통상임금 소정근로";b.summary="일정 근무일수를 채워야 지급하는 임금의 성격과 적용 시점을 살펴봅니다.";
  b.facts="일정 근무일수 조건이 붙은 임금을 통상임금에 포함할 수 있는지가 쟁점이 되었습니다.";b.claims="근무일수 조건이 통상임금 판단에 미치는 영향에 관한 법적 다툼입니다.";
  b.issue="소정근로일수 이내의 조건과 그 범위를 넘는 추가 근로 조건은 어떻게 다른가?";
  b.reasoning="소정근로를 온전히 제공하면 충족하는 조건만으로 통상임금성을 부정할 수 없다고 보았습니다. 반면 소정근로일수를 넘는 추가 근로의 대가는 구별했습니다. 원심을 파기하고 다시 심리하도록 했습니다.";
  b.comparison="조건의 이름보다 실제 소정근로일수와 지급 규칙을 비교해야 합니다. 원칙적인 새 법리 적용 시점과 병행사건 예외를 확인하세요. 파기환송은 최종 승소 확정과 다릅니다.";
  b.checklist="소정근로일수와 실제 근무 기록\n수당·상여금 지급 기준\n문제가 된 기간과 소송 진행 상태";
  b.excerpt="그와 같은 조건이 소정근로를 온전하게 제공하는 근로자라면 충족할 조건, 즉 소정근로일수 이내로 정해진 근무일수 조건인 경우에는 그러한 조건이 부가되어 있다는 사정만으로 그 임금의 통상임금성이 부정되지 않는다.";
  b.sourceUrl="https://www.law.go.kr/LSW/precInfoP.do?precSeq=600539";b.lawName=a.lawName;b.lawUrl=a.lawUrl;cases.save(b);
  var c=base("오래된 대여금 사건, 약정을 누가 증명해야 할까요?","돈·거래",false);
  c.caseNumber="2019다16000";c.court="대법원";c.decisionDate=LocalDate.of(2019,12,27);c.outcome="파기환송";
  c.tags="대여금 빌려준 돈 약정 증명책임 입증책임 추후보완항소";c.summary="기록이 폐기된 뒤 항소가 제기되어도 약정의 증명책임이 자동으로 바뀌는 것은 아니라는 사례입니다.";
  c.facts="다른 사람 채무의 일부를 피고가 지급하기로 했다는 주장이 있었고, 제1심 이후 보존 기간이 지나 기록이 폐기된 상태에서 추후보완항소가 제기되었습니다.";
  c.claims="원고는 지급 약정이 있었다고 주장했고 피고는 약정 사실을 다투었습니다.";c.issue="기록 폐기와 제1심 인용만으로 약정 사실을 추정하거나 증명책임을 바꿀 수 있는가?";
  c.reasoning="약정을 주장하는 원고에게 증명책임이 있고, 기록 폐기 등 사정만으로 전환되지 않는다고 보았습니다. 증거·대체 증거와 제출 가능성을 살피고 충분한 증명 기회를 주어야 한다는 취지로 파기환송했습니다.";
  c.comparison="송금 사실만이 아니라 어떤 약정을 누가 주장하는지 확인하세요. 이 사건의 추후보완항소·기록 폐기 상황을 일반 대여금 분쟁과 구분해야 합니다.";
  c.checklist="약정서·당시 대화·송금 자료\n청구의 구체적 근거\n종전 증거 또는 대체 증거";
  c.excerpt="그 약정사실에 대한 증명책임은 이를 주장하는 원고에게 있다.";c.sourceUrl="https://www.law.go.kr/LSW/precInfoP.do?precSeq=225923";c.lawName="민법 제598조·민사소송법 제202조·제288조 / 판결 원문 참조";c.lawUrl="https://www.law.go.kr/법령/민법";cases.save(c);
  demo("보증금 반환, 어떤 사실부터 정리할까요?","주거","보증금 전세 집주인 임대차 계약 만료","계약이 끝났다고 생각하는 세입자와 정산할 항목이 남았다고 말하는 집주인이 있습니다.","계약 종료·통지·인도·정산 사실을 구분해 볼 수 있을까요?","계약서와 통지 내용, 이사·인도 관련 기록을 시간순으로 정리하는 연습입니다. 이 예시에는 법원의 인정 사실이나 결론이 없습니다.","임대차계약서\n종료·갱신 통지와 수신 기록\n인도 및 정산 관련 기록");
  demo("중고 물품의 하자, 설명과 실제 상태 비교하기","돈·거래","중고 환불 하자 거래 물품","구매자는 작동 불량을, 판매자는 거래 전 상태 설명을 이야기합니다.","거래 당시 설명·합의와 하자 발생 시점을 어떻게 구분할까요?","판매글·대화·인수 상태를 비교하며 쟁점을 찾는 가상 학습 자료입니다. 계약과 증거에 따라 달라질 수 있어 환불 가능 여부를 단정하지 않습니다.","판매 게시글·거래 대화\n결제·인수 기록\n고장 확인 자료");
  demo("온라인 게시글, 내용과 공개 범위를 정리하기","온라인 생활","악플 모욕 명예훼손 게시글 온라인","이용자가 자신에 관한 게시글을 발견했습니다.","누구를 가리키는지, 어떤 표현인지, 누구에게 공개됐는지 구분할 수 있을까요?","온라인 표현 관련 개념을 살피기 위한 가상 사례입니다. 특정 죄의 성립이나 손해배상 결과를 판정하지 않습니다.","게시물 원본 주소와 작성 시점\n화면·공개 범위 기록\n삭제·수정 전후 기록");
 }
 private CaseEntry base(String title,String category,boolean demo){var c=new CaseEntry();c.title=title;c.category=category;c.demo=demo;c.published=true;c.sourceCheckedAt=demo?null:LocalDate.of(2026,10,28);return c;}
 private void demo(String title,String cat,String tags,String facts,String issue,String reasoning,String checklist){var d=base(title,cat,true);d.tags=tags;d.summary="실제 사건이 아닌 학습용 가상 사례 · 사실관계와 준비 자료를 정리하는 연습입니다.";d.facts=facts;d.claims="가상 상황이며 실제 당사자의 주장이 아닙니다.";d.issue=issue;d.reasoning=reasoning;d.comparison="이 예시는 판결 근거나 승소 사례로 사용할 수 없습니다. 관련 실제 판결과 공식 자료를 따로 확인하세요.";d.checklist=checklist;d.outcome="판결 없음 · 학습용";d.reviewStatus="가상 학습 자료";cases.save(d);}
}
