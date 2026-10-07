package org.empresajr.chatjr.web.dto;

import org.empresajr.chatjr.domain.Attachment;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public record AttachmentView(Long tabId, boolean hasPdf, String pdfName, int pdfPages, int pdfChars, long storedBytes,
                             String slideLink, String state, boolean processed, int extractedSections,
                             List<String> sections, Instant processedAt, String message) {

    public static AttachmentView of(Attachment a) {
        List<String> sections = a.getSections() == null ? List.of() : Arrays.asList(a.getSections().split("\n"));
        return new AttachmentView(a.getTabId(), a.hasPdf(), a.getPdfName(), a.getPdfPages(), a.getPdfChars(),
                a.getStoredBytes(), a.getSlideLink(), a.getState().name(), a.isProcessed(),
                a.getExtractedSections(), sections, a.getProcessedAt(), a.getErrorMessage());
    }

    public static AttachmentView empty(Long tabId) {
        return new AttachmentView(tabId, false, null, 0, 0, 0, null, "IDLE", false, 0, List.of(), null, null);
    }
}
