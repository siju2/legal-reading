package com.example.project.legal;

import java.util.*;
import java.util.regex.Pattern;

/** In-memory BM25 index with Hangul bigrams for Korean morphology variation. */
final class LegalBm25Index {
    record Hit(int document, double score, int matchedWords) {}
    private static final Pattern WORDS = Pattern.compile("[\\p{IsHangul}\\p{L}\\p{N}]+");
    private final Map<String, PostingList> postings = new HashMap<>();
    private final int[] lengths;
    private final double averageLength;

    LegalBm25Index(List<LawAssistantService.Source> sources) {
        lengths = new int[sources.size()];
        long totalLength = 0;
        for (int document = 0; document < sources.size(); document++) {
            final int documentId = document;
            var source = sources.get(document);
            String text = source.title() + " " + String.join(" ", source.keywords()) + " " + source.text();
            var frequencies = terms(text);
            lengths[document] = frequencies.values().stream().mapToInt(Integer::intValue).sum();
            totalLength += lengths[document];
            frequencies.forEach((term, frequency) -> postings.computeIfAbsent(term, ignored -> new PostingList()).add(documentId, frequency));
        }
        averageLength = sources.isEmpty() ? 1 : Math.max(1, (double) totalLength / sources.size());
    }

    List<Hit> search(String query, int limit) {
        var queryTerms = terms(query).keySet();
        if (queryTerms.isEmpty() || lengths.length == 0) return List.of();
        var scores = new HashMap<Integer, Double>();
        var matchedWords = new HashMap<Integer, Integer>();
        for (String term : queryTerms) {
            PostingList list = postings.get(term);
            if (list == null) continue;
            double idf = Math.log(1 + (lengths.length - list.size + 0.5) / (list.size + 0.5));
            for (int i = 0; i < list.size; i++) {
                int document = list.documents[i], frequency = list.frequencies[i];
                double norm = frequency + 1.2 * (1 - 0.75 + 0.75 * lengths[document] / averageLength);
                scores.merge(document, idf * frequency * 2.2 / norm, Double::sum);
                if (term.startsWith("w:")) matchedWords.merge(document, 1, Integer::sum);
            }
        }
        return scores.entrySet().stream().map(e -> new Hit(e.getKey(), e.getValue(), matchedWords.getOrDefault(e.getKey(), 0)))
            .sorted(Comparator.comparingDouble(Hit::score).reversed()).limit(limit).toList();
    }

    String excerpt(String text, String query, int limit) {
        if (text.length() <= limit) return text;
        var queryTerms = terms(query).keySet();
        var passages = new ArrayList<Passage>();
        for (int start = 0; start < text.length(); start += 700) {
            int end = Math.min(text.length(), start + 900);
            String passage = text.substring(start, end);
            int score = terms(passage).keySet().stream().filter(queryTerms::contains)
                .mapToInt(term -> term.startsWith("w:") ? 3 : 1).sum();
            if (score > 0) passages.add(new Passage(start, end, score));
        }
        var selected = new ArrayList<Passage>();
        int size = 0;
        for (var passage : passages.stream().sorted(Comparator.comparingInt(Passage::score).reversed()).toList()) {
            if (selected.stream().anyMatch(other -> passage.start() < other.end() && other.start() < passage.end())) continue;
            if (size + passage.end() - passage.start() > limit) continue;
            selected.add(passage); size += passage.end() - passage.start();
        }
        if (selected.isEmpty()) return text.substring(0, limit);
        return selected.stream().sorted(Comparator.comparingInt(Passage::start))
            .map(passage -> text.substring(passage.start(), passage.end()))
            .collect(java.util.stream.Collectors.joining("\n…\n"));
    }

    private record Passage(int start, int end, int score) {}

    private static Map<String, Integer> terms(String text) {
        var result = new HashMap<String, Integer>();
        var matcher = WORDS.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            if (word.codePointCount(0, word.length()) >= 2) result.merge("w:" + word, 1, Integer::sum);
            int[] points = word.codePoints().toArray();
            for (int i = 1; i < points.length; i++) {
                if (isHangul(points[i - 1]) && isHangul(points[i]))
                    result.merge("b:" + new String(points, i - 1, 2), 1, Integer::sum);
            }
        }
        return result;
    }

    private static boolean isHangul(int codePoint) {
        return (codePoint >= 0xAC00 && codePoint <= 0xD7A3) || (codePoint >= 0x1100 && codePoint <= 0x11FF)
            || (codePoint >= 0x3130 && codePoint <= 0x318F);
    }

    private static final class PostingList {
        private int[] documents = new int[8], frequencies = new int[8];
        private int size;
        void add(int document, int frequency) {
            if (size == documents.length) {
                documents = Arrays.copyOf(documents, size * 2);
                frequencies = Arrays.copyOf(frequencies, size * 2);
            }
            documents[size] = document;
            frequencies[size++] = frequency;
        }
    }
}
