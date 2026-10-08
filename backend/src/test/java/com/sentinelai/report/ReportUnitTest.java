package com.sentinelai.report;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Schedule arithmetic and the PDF/CSV renderers, without Spring. */
class ReportUnitTest {

    private static final Instant WED_1030 = Instant.parse("2026-10-07T10:30:00Z"); // a Wednesday

    @Test
    void nextRunDailyAndWeekly() {
        assertThat(ReportScheduleService.nextRun(ScheduleFrequency.DAILY, null, 12, WED_1030))
                .isEqualTo(Instant.parse("2026-10-07T12:00:00Z"));
        assertThat(ReportScheduleService.nextRun(ScheduleFrequency.DAILY, null, 9, WED_1030))
                .isEqualTo(Instant.parse("2026-10-08T09:00:00Z"));
        assertThat(ReportScheduleService.nextRun(ScheduleFrequency.WEEKLY, 1, 8, WED_1030)) // Monday
                .isEqualTo(Instant.parse("2026-10-12T08:00:00Z"));
        assertThat(ReportScheduleService.nextRun(ScheduleFrequency.WEEKLY, 3, 10, WED_1030)) // today, already passed
                .isEqualTo(Instant.parse("2026-10-14T10:00:00Z"));
        assertThat(ReportScheduleService.nextRun(ScheduleFrequency.WEEKLY, 3, 11, WED_1030)) // today, later
                .isEqualTo(Instant.parse("2026-10-07T11:00:00Z"));
    }

    private ReportTable table(int rows) {
        List<List<String>> r = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            r.add(List.of(String.valueOf(i), "Brute force → root ×" + i + " — 漢字", "=SUM(A1)", "HIGH"));
        }
        return new ReportTable("Incident Summary", Instant.parse("2026-10-01T00:00:00Z"), WED_1030,
                List.of("Incidents opened: " + rows, "Status New → In Progress"), List.of("ID", "Title", "Note", "Severity"), r, false);
    }

    @Test
    void pdfIsValidPaginatedAndSurvivesNonLatinText() throws Exception {
        byte[] pdf = new PdfReportRenderer(Clock.fixed(WED_1030, ZoneOffset.UTC)).render(table(120));
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("SentinelAI - Incident Summary").contains("Incidents opened: 120")
                    .contains("Brute force -> root x7").contains("page 1 of");
        }
    }

    @Test
    void csvHasSummaryHeaderRowsAndFormulaSafety() {
        String csv = new String(new CsvReportRenderer().render(table(2)), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿# Incident Summary").contains("# Incidents opened: 2")
                .contains("ID,Title,Note,Severity").contains("'=SUM(A1)");
        assertThat(csv.lines().filter(l -> l.startsWith("0,") || l.startsWith("1,")).count()).isEqualTo(2);
    }
}
