package com.example.project.legal;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
final class LocalWebSearch {
    private final IntegrationSettings settings;
    private final ObjectMapper json;
    private final HttpClient client=HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).connectTimeout(Duration.ofSeconds(3)).build();
    LocalWebSearch(IntegrationSettings settings,ObjectMapper json){this.settings=settings;this.json=json;}

    List<LawAssistantService.Source> search(String query) {
        try {
            String token=settings.value("legal.ai.local-token","");
            if(token.isBlank())return List.of();
            var body=json.writeValueAsString(Map.of("query",query));
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:11435/search")).timeout(Duration.ofSeconds(25))
                .header("Content-Type","application/json").header("X-Local-AI-Token",token).POST(HttpRequest.BodyPublishers.ofString(body)).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)return List.of();
            if(response.body().length()>32768)return List.of();
            var rows=json.readTree(response.body()).path("results");if(!rows.isArray())return List.of();var sources=new ArrayList<LawAssistantService.Source>();
            for(var row:rows) {
                String url=row.path("url").asText("");
                if(!officialUrl(url))continue;
                String title=row.path("title").asText("공식 법률 자료");title=title.substring(0,Math.min(title.length(),200)).strip();
                String snippet=row.path("snippet").asText("");snippet=snippet.substring(0,Math.min(snippet.length(),1600)).strip();
                sources.add(new LawAssistantService.Source("web-search-"+(sources.size()+1),title,
                    "실시간 검색 결과 발췌(검색엔진 요약이며 원문 전체가 아닙니다):\n"+snippet,url,LocalDate.now().toString(),List.of()));
                if(sources.size()==5)break;
            }
            return List.copyOf(sources);
        } catch(Exception ignored){return List.of();}
    }

    private static boolean officialUrl(String value) {
        return LegalService.officialUrl(value);
    }
}
