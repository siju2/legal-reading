package com.example.project.legal;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.io.*;
import java.net.URI;

@Service public class LawApiService {
 private final RestClient client;private final ObjectMapper json;private final String environmentOc;private final Path config;
 private final Semaphore limit=new Semaphore(2);private final Map<String,Cached> cache=Collections.synchronizedMap(new LinkedHashMap<>());
 record Cached(Instant at,JsonNode value){}
 public record Item(String id,String title,String number,String date,String court,String url){}
 public record Search(List<Item> items,int total,int page){}
 public record Detail(String title,String number,String date,String court,String issues,String summary,String references,String body,String url){}
 public LawApiService(@Qualifier("lawApiClient")RestClient client,ObjectMapper json,@Value("${legal.law-api.oc:}")String oc,@Value("${legal.law-api.config:./data/law-api.properties}")String config){this.client=client;this.json=json;this.environmentOc=oc;this.config=Path.of(config);}
 private String oc(){String value=environmentOc.strip();if(value.isEmpty()&&Files.isRegularFile(config)){try(var in=Files.newBufferedReader(config,StandardCharsets.UTF_8)){var p=new Properties();p.load(in);value=p.getProperty("legal.law-api.oc","").strip();}catch(IOException e){throw unavailable("API 설정 파일을 읽지 못했습니다.");}}if(value.equals("YOUR_OC")||value.equals("CHANGE_ME")||value.equals("test"))return "";return value;}
 public boolean configured(){return !oc().isBlank();}
 public Search search(String kind,String q,int page){validate(kind,q,page);if(q.isBlank())return new Search(List.of(),0,page);var root=request("lawSearch.do",Map.of("target",kind,"query",q,"search","2","display","10","page",Integer.toString(page),"sort",kind.equals("prec")?"ddes":"lasc"));return parseSearch(root,kind,page);}
 static void validate(String kind,String q,int page){LegalService.require(List.of("prec","law").contains(kind),"조회 대상을 확인해 주세요.");LegalService.require(q.length()<=120&&!q.contains("\n")&&!q.contains("\r"),"검색어는 한 줄 120자 이하로 입력해 주세요.");LegalService.require(page>=1&&page<=100,"페이지는 1~100 범위로 선택해 주세요.");}
 public Detail detail(String id){LegalService.require(id.matches("[0-9]{1,12}"),"판례 식별자를 확인해 주세요.");var r=request("lawService.do",Map.of("target","prec","ID",id));var n=r.path("PrecService");if(n.isMissingNode()||n.isNull()||n.size()==0)throw unavailable("판례 본문 응답을 확인할 수 없습니다.");if(text(n,"사건명").isBlank())throw unavailable("판례를 찾지 못했거나 응답 형식이 다릅니다.");return new Detail(text(n,"사건명"),text(n,"사건번호"),text(n,"선고일자"),text(n,"법원명"),text(n,"판시사항"),text(n,"판결요지"),text(n,"참조조문"),text(n,"판례내용"),"https://www.law.go.kr/LSW/precInfoP.do?precSeq="+id);}
 JsonNode request(String endpoint,Map<String,String> params){String credential=oc();if(credential.isBlank())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"법제처 OC 인증값이 아직 설정되지 않았습니다.");String key=Integer.toHexString(credential.hashCode())+endpoint+new TreeMap<>(params);var hit=cache.get(key);if(hit!=null&&hit.at().plusSeconds(120).isAfter(Instant.now()))return hit.value();if(!limit.tryAcquire())throw unavailable("외부 조회가 진행 중입니다. 잠시 후 다시 시도해 주세요.");try{
  var uri=UriComponentsBuilder.fromUriString("https://www.law.go.kr/DRF/"+endpoint).queryParam("OC",credential).queryParam("type","JSON");params.forEach(uri::queryParam);URI url=uri.build().encode().toUri();
  String body=client.get().uri(url).exchange((req,res)->{if(!res.getStatusCode().is2xxSuccessful())throw unavailable("법제처 응답에 실패했습니다. 인증·승인 상태와 연결을 확인해 주세요.");try(var in=res.getBody()){byte[] bytes=in.readNBytes(2_000_001);if(bytes.length>2_000_000)throw unavailable("외부 응답이 너무 큽니다. 검색 범위를 좁혀 주세요.");return new String(bytes,StandardCharsets.UTF_8);}});
  JsonNode node=json.readTree(body);if(node==null||!node.isObject())throw unavailable("외부 응답 형식을 확인할 수 없습니다.");synchronized(cache){if(cache.size()>=32)cache.remove(cache.keySet().iterator().next());cache.put(key,new Cached(Instant.now(),node));}return node;
 }catch(ResponseStatusException e){throw e;}catch(Exception e){throw unavailable("외부 조회를 완료하지 못했습니다. OC 인증·이용 승인·네트워크 상태를 확인해 주세요.");}finally{limit.release();}}
 static Search parseSearch(JsonNode root,String kind,int page){var n=root.path(kind.equals("prec")?"PrecSearch":"LawSearch");if(!n.isObject()||!n.has("totalCnt"))throw unavailable("검색 응답을 확인할 수 없습니다. 인증·이용 승인 상태를 확인해 주세요.");int total;try{total=Integer.parseInt(n.path("totalCnt").asText().replace(",",""));if(total<0)throw new NumberFormatException();}catch(Exception e){throw unavailable("검색 결과 수를 확인할 수 없습니다.");}var found=n.path(kind.equals("prec")?"prec":"law");var rows=new ArrayList<JsonNode>();if(found.isArray())found.forEach(rows::add);else if(found.isObject())rows.add(found);if(total>0&&rows.isEmpty())throw unavailable("검색 응답에 자료 목록이 없습니다.");var items=new ArrayList<Item>();for(var row:rows){String id=text(row,kind.equals("prec")?"판례일련번호":"법령일련번호");if(!id.matches("[0-9]{1,12}"))throw unavailable("검색 자료의 식별자가 올바르지 않습니다.");items.add(new Item(id,text(row,kind.equals("prec")?"사건명":"법령명한글"),text(row,kind.equals("prec")?"사건번호":"공포번호"),text(row,kind.equals("prec")?"선고일자":"시행일자"),text(row,kind.equals("prec")?"법원명":"소관부처명"),kind.equals("prec")?"https://www.law.go.kr/LSW/precInfoP.do?precSeq="+id:"https://www.law.go.kr/LSW/lsInfoP.do?lsiSeq="+id));}return new Search(items,total,page);}
 static String text(JsonNode n,String key){String s=n.path(key).asText("");return s.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>","").replaceAll("(?i)<br\\s*/?>","\n").replaceAll("<[^>]+>","").replace("&nbsp;"," ").replace("&lt;","<").replace("&gt;",">").replace("&amp;","&").strip();}
 static ResponseStatusException unavailable(String text){return new ResponseStatusException(HttpStatus.BAD_GATEWAY,text);}
}
