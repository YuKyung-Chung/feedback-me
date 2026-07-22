package com.jyk.feedbackme.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
/**
 * FeedbackMe 백엔드의 FileExtractService 구성 요소입니다.
 * 이 파일은 com.jyk.feedbackme.service 계층의 책임을 담당합니다.
 */
public class FileExtractService {

    private static final Logger log = LoggerFactory.getLogger(FileExtractService.class);

    public String extract(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();

        if (filename == null) throw new IllegalArgumentException("File name is missing.");

        String lowerFilename = filename.toLowerCase(Locale.ROOT);
        if (lowerFilename.endsWith(".pdf")) {
            return extractPdf(file.getInputStream());
        } else if (lowerFilename.endsWith(".docx")) {
            return extractDocx(file.getInputStream());
        } else {
            throw new IllegalArgumentException("Only PDF and DOCX files are supported.");
        }
    }

    private String extractPdf(InputStream is) throws Exception {
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(is.readAllBytes())) {
            String text = new PDFTextStripper().getText(doc);
            log.info("Extracted PDF text. pages={}, characters={}", doc.getNumberOfPages(), text.length());
            return text;
        }
    }

    public List<String> pdfToBase64Images(MultipartFile file) throws Exception {
        List<String> base64Images = new ArrayList<>();
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 150);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "PNG", baos);
                base64Images.add(Base64.getEncoder().encodeToString(baos.toByteArray()));
            }
        }
        return base64Images;
    }

    private String extractDocx(InputStream is) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(is)) {
            return doc.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.joining("\n"));
        }
    }
}
