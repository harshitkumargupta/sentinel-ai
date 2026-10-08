package com.sentinelai.report;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * A4-landscape PDF with Apache PDFBox and the built-in Helvetica fonts (no font files, works offline):
 * title, range, summary, then a paginated table whose header repeats on every page. Cell text is
 * clipped to its column. Characters outside WinAnsi are replaced, since the base fonts can't draw them.
 */
@Component
public class PdfReportRenderer {

    private static final float MARGIN = 36;
    private static final float ROW_H = 14;
    private static final float FONT_SIZE = 8;

    private final Clock clock;

    public PdfReportRenderer(Clock clock) {
        this.clock = clock;
    }

    public byte[] render(ReportTable t) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDRectangle size = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            float width = size.getWidth() - 2 * MARGIN;
            float[] cols = columnWidths(t, regular, width);

            Writer w = new Writer(doc, size, regular, bold);
            w.text(bold, 16, safe("SentinelAI - " + t.title()));
            w.gap(4);
            w.text(regular, 9, safe("Range (UTC): " + t.from() + "  to  " + t.to()
                    + "    Generated: " + clock.instant()));
            w.gap(8);
            w.text(bold, 11, "Summary");
            for (String s : t.summary()) {
                for (String line : wrap(safe("- " + s), regular, 10, width)) {
                    w.text(regular, 10, line);
                }
            }
            w.gap(10);
            if (t.rows().isEmpty()) {
                w.text(regular, 10, "No rows for this period.");
            } else {
                w.header = t.columns();
                w.cols = cols;
                w.tableHeader();
                for (List<String> row : t.rows()) {
                    w.row(row);
                }
            }
            w.close();
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                try (PDPageContentStream cs = new PDPageContentStream(doc, doc.getPage(i),
                        PDPageContentStream.AppendMode.APPEND, true)) {
                    cs.beginText();
                    cs.setFont(regular, 7);
                    cs.newLineAtOffset(MARGIN, 18);
                    cs.showText("SentinelAI report - page " + (i + 1) + " of " + doc.getNumberOfPages()
                            + " - confidential, generated offline");
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("PDF rendering failed", e);
        }
    }

    /** Proportional to the longest cell in each column (sampled), within the page width. */
    private static float[] columnWidths(ReportTable t, PDType1Font font, float total) {
        int n = t.columns().size();
        float[] want = new float[n];
        for (int c = 0; c < n; c++) {
            float m = measure(font, safe(t.columns().get(c)), FONT_SIZE) + 6;
            for (int r = 0; r < Math.min(200, t.rows().size()); r++) {
                m = Math.max(m, Math.min(260, measure(font, safe(t.rows().get(r).get(c)), FONT_SIZE) + 6));
            }
            want[c] = m;
        }
        float sum = 0;
        for (float v : want) {
            sum += v;
        }
        for (int c = 0; c < n; c++) {
            want[c] = want[c] / sum * total;
        }
        return want;
    }

    /** Replace characters the base-14 fonts can't encode, and control characters. */
    static String safe(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            if (ch == '→') {
                sb.append("->");
            } else if (ch == '×') {
                sb.append('x');
            } else if (ch == '…') {
                sb.append("...");
            } else if (ch < 32 || ch > 255) {
                sb.append(ch == '\n' || ch == '\t' ? ' ' : '?');
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static float measure(PDType1Font font, String s, float size) {
        try {
            return font.getStringWidth(s) / 1000 * size;
        } catch (IOException | IllegalArgumentException e) {
            return s.length() * size * 0.5f;
        }
    }

    private static String clip(PDType1Font font, String s, float width) {
        if (measure(font, s, FONT_SIZE) <= width) {
            return s;
        }
        String t = s;
        while (!t.isEmpty() && measure(font, t + "...", FONT_SIZE) > width) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "...";
    }

    private static List<String> wrap(String s, PDType1Font font, float size, float width) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = cur.isEmpty() ? word : cur + " " + word;
            if (measure(font, next, size) > width && !cur.isEmpty()) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(next);
            }
        }
        if (!cur.isEmpty()) {
            lines.add(cur.toString());
        }
        return lines;
    }

    /** Cursor over pages: starts a new page when the next line wouldn't fit. */
    private static final class Writer {
        private final PDDocument doc;
        private final PDRectangle size;
        private final PDType1Font regular;
        private final PDType1Font bold;
        private PDPageContentStream cs;
        private float y;
        List<String> header;
        float[] cols;

        Writer(PDDocument doc, PDRectangle size, PDType1Font regular, PDType1Font bold) throws IOException {
            this.doc = doc;
            this.size = size;
            this.regular = regular;
            this.bold = bold;
            newPage();
        }

        void newPage() throws IOException {
            if (cs != null) {
                cs.close();
            }
            PDPage page = new PDPage(size);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = size.getHeight() - MARGIN;
        }

        void ensure(float h) throws IOException {
            if (y - h < MARGIN + 14) {
                newPage();
            }
        }

        void text(PDType1Font font, float fontSize, String s) throws IOException {
            ensure(fontSize + 4);
            y -= fontSize + 4;
            cs.beginText();
            cs.setFont(font, fontSize);
            cs.newLineAtOffset(MARGIN, y);
            cs.showText(s);
            cs.endText();
        }

        void gap(float h) {
            y -= h;
        }

        void tableHeader() throws IOException {
            if (y - 2 * ROW_H < MARGIN + 14) {
                newPage(); // keep the header with at least one row
            }
            drawHeader();
        }

        private void drawHeader() throws IOException {
            cs.setNonStrokingColor(0.88f, 0.9f, 0.94f);
            cs.addRect(MARGIN, y - ROW_H + 3, sum(), ROW_H);
            cs.fill();
            cs.setNonStrokingColor(0f, 0f, 0f);
            cells(bold, header);
        }

        void row(List<String> row) throws IOException {
            ensureRow();
            cells(regular, row);
        }

        private void ensureRow() throws IOException {
            if (y - ROW_H < MARGIN + 14) {
                newPage();
                drawHeader(); // repeat the header on every page
            }
        }

        private void cells(PDType1Font font, List<String> values) throws IOException {
            y -= ROW_H;
            float x = MARGIN;
            for (int c = 0; c < cols.length; c++) {
                String v = c < values.size() ? clip(font, safe(values.get(c)), cols[c] - 4) : "";
                cs.beginText();
                cs.setFont(font, FONT_SIZE);
                cs.newLineAtOffset(x + 2, y + 4);
                cs.showText(v);
                cs.endText();
                x += cols[c];
            }
        }

        private float sum() {
            float s = 0;
            for (float c : cols) {
                s += c;
            }
            return s;
        }

        void close() throws IOException {
            cs.close();
        }
    }
}
