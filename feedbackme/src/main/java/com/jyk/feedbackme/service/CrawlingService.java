package com.jyk.feedbackme.service;

import com.jyk.feedbackme.dto.CrawledJobPosting;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;

@Service
public class CrawlingService {

    public String crawl(String url) throws Exception {
        return crawlPosting(url).content();
    }

    public CrawledJobPosting crawlPosting(String url) throws Exception {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get();

        String title = extractTitle(doc);
        String companyName = extractCompanyName(doc, title, url);

        //불필요한 태그 제거 후 텍스트만 추출
        doc.select("script, style, header, footer, nav").remove();

        return new CrawledJobPosting(url, companyName, title, doc.body().text());
    }

    private String extractTitle(Document doc) {
        List<String> candidates = List.of(
                attr(doc, "meta[property=og:title]", "content"),
                attr(doc, "meta[name=title]", "content"),
                text(doc, "h1"),
                doc.title()
        );

        return candidates.stream()
                .map(this::clean)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse("채용공고");
    }

    private String extractCompanyName(Document doc, String title, String url) {
        List<String> selectors = List.of(
                "[class*=company]",
                "[class*=corp]",
                "[class*=기업]",
                "[class*=회사]"
        );

        for (String selector : selectors) {
            Element element = doc.selectFirst(selector);
            String value = element == null ? "" : clean(element.text());
            if (!value.isBlank() && value.length() <= 60) {
                return value;
            }
        }

        String fromTitle = extractCompanyNameFromTitle(title);
        if (!fromTitle.isBlank()) {
            return fromTitle;
        }

        return extractHost(url);
    }

    private String extractCompanyNameFromTitle(String title) {
        String cleanTitle = clean(title);
        if (cleanTitle.isBlank()) {
            return "";
        }

        if (cleanTitle.startsWith("[") && cleanTitle.contains("]")) {
            return cleanTitle.substring(1, cleanTitle.indexOf("]")).trim();
        }

        String[] delimiters = {" - ", " | ", "ㅣ", "::"};
        for (String delimiter : delimiters) {
            if (cleanTitle.contains(delimiter)) {
                String first = clean(cleanTitle.split(delimiter, 2)[0]);
                if (!first.isBlank() && first.length() <= 40) {
                    return first;
                }
            }
        }

        return "";
    }

    private String extractHost(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "채용공고" : host.replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException e) {
            return "채용공고";
        }
    }

    private String attr(Document doc, String selector, String attr) {
        Element element = doc.selectFirst(selector);
        return element == null ? "" : element.attr(attr);
    }

    private String text(Document doc, String selector) {
        Element element = doc.selectFirst(selector);
        return element == null ? "" : element.text();
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

}
