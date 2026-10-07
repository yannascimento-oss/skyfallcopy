package org.empresajr.chatjr.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.List;

/** Gera o PDF do plano com texto simples: títulos, parágrafos e quebra de página. Sem dependências além do PDFBox. */
public final class PdfExporter {

    public record Section(String title, String text) {
    }

    private static final float MARGIN = 56;
    private static final CharsetEncoder LATIN = Charset.forName("windows-1252").newEncoder();

    private PdfExporter() {
    }

    public static byte[] render(String heading, String subtitle, List<Section> sections) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Writer writer = new Writer(document);
            writer.line(heading, writer.bold, 20);
            writer.line(subtitle, writer.regular, 10);
            for (Section section : sections) {
                writer.gap(14);
                writer.line(section.title(), writer.bold, 14);
                writer.gap(4);
                for (String paragraph : section.text().split("\n")) {
                    if (paragraph.isBlank()) {
                        writer.gap(6);
                    } else {
                        writer.line(paragraph, writer.regular, 11);
                    }
                }
            }
            writer.close();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível gerar o PDF.", e);
        }
    }

    /** Troca o que a fonte padrão do PDF não sabe desenhar (emojis, setas) por "?" para não quebrar a geração. */
    static String clean(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c == '\t') {
                sb.append(' ');
            } else if (Character.isISOControl(c)) {
                continue;
            } else {
                synchronized (LATIN) {
                    sb.append(LATIN.canEncode(c) ? c : '?');
                }
            }
        }
        return sb.toString();
    }

    private static final class Writer {
        final PDDocument document;
        final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        final float width = PDRectangle.A4.getWidth() - 2 * MARGIN;
        PDPageContentStream stream;
        float y;

        Writer(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
        }

        void gap(float points) throws IOException {
            y -= points;
            if (y < MARGIN) {
                newPage();
            }
        }

        void line(String raw, PDFont font, float size) throws IOException {
            for (String text : wrap(clean(raw), font, size)) {
                float leading = size * 1.4f;
                if (y - leading < MARGIN) {
                    newPage();
                }
                y -= leading;
                stream.beginText();
                stream.setFont(font, size);
                stream.newLineAtOffset(MARGIN, y);
                stream.showText(text);
                stream.endText();
            }
        }

        List<String> wrap(String text, PDFont font, float size) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : text.split(" ")) {
                String piece = word;
                while (textWidth(piece, font, size) > width) {
                    int cut = piece.length() - 1;
                    while (cut > 1 && textWidth(piece.substring(0, cut), font, size) > width) {
                        cut--;
                    }
                    if (current.length() > 0) {
                        lines.add(current.toString());
                        current.setLength(0);
                    }
                    lines.add(piece.substring(0, cut));
                    piece = piece.substring(cut);
                }
                String candidate = current.length() == 0 ? piece : current + " " + piece;
                if (textWidth(candidate, font, size) > width && current.length() > 0) {
                    lines.add(current.toString());
                    current = new StringBuilder(piece);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            if (current.length() > 0) {
                lines.add(current.toString());
            }
            return lines;
        }

        float textWidth(String text, PDFont font, float size) throws IOException {
            return font.getStringWidth(text) / 1000f * size;
        }

        void close() throws IOException {
            stream.close();
        }
    }
}
