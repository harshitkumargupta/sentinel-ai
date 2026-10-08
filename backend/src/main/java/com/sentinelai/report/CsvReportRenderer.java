package com.sentinelai.report;

import com.sentinelai.search.CsvWriter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** RFC 4180 CSV (formula-safe cells): a commented header block with the summary, then the table. */
@Component
public class CsvReportRenderer {

    public byte[] render(ReportTable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvWriter.cell("# " + t.title())).append("\r\n");
        sb.append(CsvWriter.cell("# Range (UTC): " + t.from() + " to " + t.to())).append("\r\n");
        for (String s : t.summary()) {
            sb.append(CsvWriter.cell("# " + s)).append("\r\n");
        }
        sb.append(String.join(",", t.columns().stream().map(CsvWriter::cell).toList())).append("\r\n");
        for (var row : t.rows()) {
            sb.append(String.join(",", row.stream().map(CsvWriter::cell).toList())).append("\r\n");
        }
        // UTF-8 BOM so spreadsheet apps detect the encoding.
        return ("﻿" + sb).getBytes(StandardCharsets.UTF_8);
    }
}
