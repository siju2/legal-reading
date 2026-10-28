package com.example.project.legal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class LawApiServiceTest {
 @TempDir Path dir;ObjectMapper json=new ObjectMapper();
 @Test void parserHandlesArraySingletonAndLawResults()throws Exception{
  String row="{\"판례일련번호\":\"600537\",\"사건명\":\"임금\",\"사건번호\":\"2020다247190\",\"선고일자\":\"20241219\",\"법원명\":\"대법원\"}";
  var single=LawApiService.parseSearch(json.readTree("{\"PrecSearch\":{\"totalCnt\":\"1\",\"prec\":"+row+"}}"),"prec",1);assertThat(single.items()).hasSize(1);assertThat(single.items().get(0).url()).endsWith("precSeq=600537");
  var array=LawApiService.parseSearch(json.readTree("{\"PrecSearch\":{\"totalCnt\":1,\"prec\":["+row+"]}}"),"prec",2);assertThat(array.page()).isEqualTo(2);
  var law=LawApiService.parseSearch(json.readTree("{\"LawSearch\":{\"totalCnt\":\"1\",\"law\":{\"법령일련번호\":\"123\",\"법령명한글\":\"민법\",\"시행일자\":\"20260101\"}}}"),"law",1);assertThat(law.items().get(0).title()).isEqualTo("민법");
 }
 @Test void emptyAndFailureResponsesAreDifferent()throws Exception{
  assertThat(LawApiService.parseSearch(json.readTree("{\"PrecSearch\":{\"totalCnt\":\"0\"}}"),"prec",1).items()).isEmpty();
  assertThatThrownBy(()->LawApiService.parseSearch(json.readTree("{\"error\":\"invalid OC\"}"),"prec",1)).hasMessageContaining("인증");
  assertThatThrownBy(()->LawApiService.parseSearch(json.readTree("{\"PrecSearch\":{\"totalCnt\":\"1\"}}"),"prec",1)).hasMessageContaining("목록");
 }
 @Test void keyFileCanBeConfiguredWithoutRestartAndSampleKeyIsRejected()throws Exception{
  Path config=dir.resolve("law-api.properties");var api=new LawApiService(RestClient.create(),json,"",config.toString());assertThat(api.configured()).isFalse();
  Files.writeString(config,"legal.law-api.oc=YOUR_OC\n");assertThat(api.configured()).isFalse();Files.writeString(config,"legal.law-api.oc=fake-local-credential\n");assertThat(api.configured()).isTrue();
 }
 @Test void fixedHttpsRequestAndCacheWorkWithoutLeakingCredential()throws Exception{
  var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();var api=new LawApiService(builder.build(),json,"fake-credential",dir.resolve("none").toString());
  server.expect(requestTo(startsWith("https://www.law.go.kr/DRF/lawSearch.do?"))).andExpect(method(HttpMethod.GET)).andRespond(withSuccess("{\"PrecSearch\":{\"totalCnt\":\"0\"}}",MediaType.APPLICATION_JSON));
  assertThat(api.search("prec","임금",1).total()).isZero();assertThat(api.search("prec","임금",1).total()).isZero();server.verify();
 }
 @Test void detailIsSanitizedAndUpstreamErrorIsRedacted()throws Exception{
  var builder=RestClient.builder();var server=MockRestServiceServer.bindTo(builder).build();var api=new LawApiService(builder.build(),json,"super-secret-oc",dir.resolve("none").toString());
  server.expect(requestTo(startsWith("https://www.law.go.kr/DRF/lawService.do?"))).andRespond(withSuccess("{\"PrecService\":{\"사건명\":\"임금\",\"판례내용\":\"<script>bad()</script><b>본문</b><br>다음\"}}",MediaType.APPLICATION_JSON));
  var d=api.detail("600537");assertThat(d.body()).isEqualTo("본문\n다음");server.verify();
  var badBuilder=RestClient.builder();var bad=MockRestServiceServer.bindTo(badBuilder).build();var fail=new LawApiService(badBuilder.build(),json,"super-secret-oc",dir.resolve("none").toString());bad.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything()).andRespond(withStatus(HttpStatus.FORBIDDEN));
  assertThatThrownBy(()->fail.search("prec","임금",1)).hasMessageContaining("인증").hasMessageNotContaining("super-secret-oc");bad.verify();
 }
 @Test void invalidQueryAndIdentifierNeverCallRemote(){var api=new LawApiService(RestClient.create(),json,"fake",dir.resolve("none").toString());assertThatThrownBy(()->api.detail("../600537")).hasMessageContaining("식별자");assertThatThrownBy(()->api.search("wrong","q",1)).hasMessageContaining("대상");assertThatThrownBy(()->api.search("prec","q",101)).hasMessageContaining("페이지");assertThatThrownBy(()->api.search("prec","x".repeat(121),1)).hasMessageContaining("120");}
}

