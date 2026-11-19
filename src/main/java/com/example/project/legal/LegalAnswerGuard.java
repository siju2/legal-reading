package com.example.project.legal;

import java.util.List;
import java.util.regex.Pattern;

/** Validates model citations and statute references before returning legal answers. */
final class LegalAnswerGuard {
    private static final Pattern CITATION = Pattern.compile("\\[(\\d+)\\]");
    private static final Pattern ARTICLE = Pattern.compile("제\\s*\\d+\\s*조(?:\\s*의\\s*\\d+)?");
    private static final Pattern STATUTE = Pattern.compile("[가-힣]{2,20}법");
    private static final Pattern DURATION = Pattern.compile("(?<!\\d)\\d+\\s*(?:일|주|개월|달|년|시간|분)(?![가-힣])");
    private static final Pattern LEGAL_CLAIM = Pattern.compile("지연손해금|손해배상|사기죄|불법행위|채무불이행|임차권등기명령|대항력|우선변제권|소멸시효|가압류|강제집행|형사고소|형사처벌|법정\\s*기한|노동청|고용노동부|신고|처벌|보상");
    private static final Pattern UNSUPPORTED_ARTICLE_NOTE = Pattern.compile("(?s).*(?:존재하지|확인되지|자료에 없는|허구|가짜).*");

    private LegalAnswerGuard() {}

    static String cleanCitations(String answer, int sourceCount) {
        var matcher = CITATION.matcher(answer);
        var cleaned = new StringBuffer();
        while (matcher.find()) {
            boolean valid;
            try {
                int number = Integer.parseInt(matcher.group(1));
                valid = number > 0 && number <= sourceCount;
            } catch (NumberFormatException invalid) {
                valid = false;
            }
            matcher.appendReplacement(cleaned, valid
                ? java.util.regex.Matcher.quoteReplacement(matcher.group()) : "");
        }
        matcher.appendTail(cleaned);
        return cleaned.toString();
    }

    static boolean hasUnsupportedLegalReference(String answer, List<LawAssistantService.Source> sources) {
        String sourceEvidence = sources.stream().map(source -> source.title() + " " + source.text())
            .collect(java.util.stream.Collectors.joining(" ")).replaceAll("\\s+", "");
        var statutes = STATUTE.matcher(answer);
        while (statutes.find()) {
            String statute = statutes.group();
            if (!statute.equals("방법") && !statute.equals("불법") && !sourceEvidence.contains(statute)) return true;
        }
        var claims = LEGAL_CLAIM.matcher(answer);
        while (claims.find()) if (!sourceEvidence.contains(claims.group().replaceAll("\\s+", ""))) return true;
        var durations = DURATION.matcher(answer);
        while (durations.find()) if (!sourceEvidence.contains(durations.group().replaceAll("\\s+", ""))) return true;
        var articles = ARTICLE.matcher(answer);
        while (articles.find()) {
            String article = articles.group().replaceAll("\\s+", "");
            int start = 0, end = answer.length();
            for (char boundary : new char[]{'\n', '.', '!', '?', '。', '！', '？'}) {
                start = Math.max(start, answer.lastIndexOf(boundary, articles.start() - 1) + 1);
                int next = answer.indexOf(boundary, articles.end());
                if (next >= 0) end = Math.min(end, next);
            }
            String clause = answer.substring(start, end);
            var citations = CITATION.matcher(clause);
            boolean cited = false, grounded = false;
            while (citations.find()) {
                cited = true;
                int index = Integer.parseInt(citations.group(1)) - 1;
                if (index >= 0 && index < sources.size() && containsArticle(sources.get(index), article)) grounded = true;
            }
            if (cited && !grounded) return true;
            if (!cited && sources.stream().noneMatch(source -> containsArticle(source, article))) {
                String nearby = answer.substring(articles.start(), Math.min(answer.length(), articles.end() + 24));
                if (!UNSUPPORTED_ARTICLE_NOTE.matcher(nearby).matches()) return true;
            }
        }
        return false;
    }

    private static boolean containsArticle(LawAssistantService.Source source, String article) {
        String evidence = (source.title() + "\n" + source.text()).replaceAll("\\s+", "");
        for (int start = evidence.indexOf(article); start >= 0; start = evidence.indexOf(article, start + 1)) {
            int end = start + article.length();
            if (end + 1 >= evidence.length() || evidence.charAt(end) != '의'
                    || !Character.isDigit(evidence.charAt(end + 1))) return true;
        }
        return false;
    }
}
