package com.jyk.feedbackme.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

@Service
public class CrawlingService {

    public String crawl(String url) throws Exception {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .get();

        //불필요한 태그 제거 후 텍스트만 추출
        doc.select("script, style, header, footer, nav").remove();

        return doc.body().text();
    }

}
