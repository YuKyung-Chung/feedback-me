package com.jyk.feedbackme.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FileExtractService {

    public String extract(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename();

        if (filename == null) throw new IllegalArgumentException("파일명이 없습니다.");

        if (filename.endsWith(".pdf")) {
            return extractPdf(file.getInputStream());
        } else if (filename.endsWith(".docx")) {
            return extractDocx(file.getInputStream());
        } else {
            throw new IllegalArgumentException("PDF 또는 DOCX 파일만 지원합니다.");
        }
    }

    private String extractPdf(InputStream is) throws Exception {
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(is.readAllBytes())) {
            String text = new PDFTextStripper().getText(doc);
            System.out.println("=== PDF 페이지 수: " + doc.getNumberOfPages());
            System.out.println("=== 추출된 글자 수: " + text.length());
            System.out.println("=== 앞 500자: " + text.substring(0, Math.min(500, text.length())));
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