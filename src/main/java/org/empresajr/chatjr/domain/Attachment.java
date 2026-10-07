package org.empresajr.chatjr.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;

/** Material de origem de uma aba: o PDF enviado (texto extraído) e/ou o link dos slides. */
@Entity
@Table(name = "attachment")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tab_id", nullable = false)
    private Long tabId;

    @Column(name = "pdf_name")
    private String pdfName;

    @Column(name = "pdf_text")
    private String pdfText;

    @Column(name = "pdf_chars", nullable = false)
    private int pdfChars;

    @Column(name = "pdf_pages", nullable = false)
    private int pdfPages;

    @Column(name = "stored_path")
    private String storedPath;

    @Column(name = "stored_bytes", nullable = false)
    private long storedBytes;

    @Column(name = "slide_link")
    private String slideLink;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AttachmentState state = AttachmentState.IDLE;

    @Column(nullable = false)
    private boolean processed;

    @Column(name = "extracted_sections", nullable = false)
    private int extractedSections;

    private String sections;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Attachment() {
    }

    public Attachment(Long tabId, Instant now) {
        this.tabId = tabId;
        this.updatedAt = now;
    }

    public boolean hasPdf() {
        return pdfText != null && !pdfText.isBlank();
    }

    public void replacePdf(String name, String text, int pages, String path, long bytes, Instant now) {
        this.pdfName = name;
        this.pdfText = text;
        this.pdfChars = text.length();
        this.pdfPages = pages;
        this.storedPath = path;
        this.storedBytes = bytes;
        this.state = AttachmentState.IDLE;
        this.processed = false;
        this.extractedSections = 0;
        this.sections = null;
        this.processedAt = null;
        this.errorMessage = null;
        this.updatedAt = now;
    }

    public void clearPdf(Instant now) {
        this.pdfName = null;
        this.pdfText = null;
        this.pdfChars = 0;
        this.pdfPages = 0;
        this.storedPath = null;
        this.storedBytes = 0;
        this.state = AttachmentState.IDLE;
        this.processed = false;
        this.extractedSections = 0;
        this.sections = null;
        this.processedAt = null;
        this.errorMessage = null;
        this.updatedAt = now;
    }

    public void markProcessing(Instant now) {
        this.state = AttachmentState.PROCESSING;
        this.errorMessage = null;
        this.updatedAt = now;
    }

    public void markDone(List<String> foundSections, String note, Instant now) {
        this.state = AttachmentState.DONE;
        this.processed = true;
        this.extractedSections = foundSections.size();
        this.sections = foundSections.isEmpty() ? null : String.join("\n", foundSections);
        this.processedAt = now;
        this.errorMessage = note;
        this.updatedAt = now;
    }

    public void markFailed(String message, Instant now) {
        this.state = AttachmentState.FAILED;
        this.errorMessage = message;
        this.updatedAt = now;
    }

    public void setSlideLink(String slideLink, Instant now) {
        this.slideLink = slideLink;
        this.updatedAt = now;
    }

    public boolean isEmpty() {
        return !hasPdf() && (slideLink == null || slideLink.isBlank());
    }

    public Long getId() { return id; }
    public Long getTabId() { return tabId; }
    public String getPdfName() { return pdfName; }
    public String getPdfText() { return pdfText; }
    public int getPdfChars() { return pdfChars; }
    public int getPdfPages() { return pdfPages; }
    public String getStoredPath() { return storedPath; }
    public long getStoredBytes() { return storedBytes; }
    public String getSlideLink() { return slideLink; }
    public AttachmentState getState() { return state; }
    public boolean isProcessed() { return processed; }
    public int getExtractedSections() { return extractedSections; }
    public String getSections() { return sections; }
    public Instant getProcessedAt() { return processedAt; }
    public String getErrorMessage() { return errorMessage; }
}
