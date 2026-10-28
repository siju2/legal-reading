package com.example.project.legal;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;
@Component("termExplainer") public class TermExplainer {
 public record Part(String text,String explanation){}
 private final Map<String,String> terms=new LinkedHashMap<>();private final Pattern pattern;
 public TermExplainer(){for(var t:LegalController.TERMS)for(String name:t.name().split("·"))terms.put(name,t.explanation());pattern=Pattern.compile(terms.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed()).map(name->(name.equals("피고")||name.equals("원고")?"(?<![가-힣A-Za-z0-9])":"")+Pattern.quote(name)+(name.equals("피고")?"(?!인)":"")).reduce((a,b)->a+"|"+b).orElse("(?!)"));}
 public List<Part> split(String text){if(text==null||text.isEmpty())return List.of();var out=new ArrayList<Part>();var m=pattern.matcher(text);int end=0;while(m.find()){if(m.start()>end)out.add(new Part(text.substring(end,m.start()),null));out.add(new Part(m.group(),terms.get(m.group())));end=m.end();}if(end<text.length())out.add(new Part(text.substring(end),null));return out;}
}
