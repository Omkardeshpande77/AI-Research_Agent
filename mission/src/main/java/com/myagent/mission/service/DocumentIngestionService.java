package com.myagent.mission.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentIngestionService {

    private static final List<Character> DEFAULT_PUNCTUATION_MARKS = List.of('.', '?', '!', '\n');

    private final VectorStore vectorStore;
    @Value("classpath:sample-research.pdf")
    private Resource pdfResource;

    public DocumentIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void ingestPDF(String filePath) {
        // 1. Load the file from the provided path
        Resource pdfResource = new FileSystemResource(filePath);

        if (!pdfResource.exists()) {
            throw new IllegalArgumentException("Could not find file at path: " + filePath);
        }
        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageExtractedTextFormatter(ExtractedTextFormatter
                        .builder()
                        .withNumberOfBottomTextLinesToDelete(0)
                        .build())
                .build();

        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(pdfResource, config);
        List<Document> documents = pdfReader.get();

        TokenTextSplitter textSplitter = new TokenTextSplitter(1000, 400, 10, 10000, true, DEFAULT_PUNCTUATION_MARKS);
        List<Document> splitDocuments = textSplitter.apply(documents);

        vectorStore.add(splitDocuments);
    }
}
