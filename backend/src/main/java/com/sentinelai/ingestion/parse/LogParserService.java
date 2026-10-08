package com.sentinelai.ingestion.parse;

import com.sentinelai.common.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Runs the parser for a format over a batch of lines, collecting records, skips and errors. */
@Service
public class LogParserService {

    private static final int MAX_ERROR_SAMPLES = 10;

    private final Map<LogFormat, LogLineParser> parsers;

    public LogParserService(List<LogLineParser> parserBeans) {
        this.parsers = parserBeans.stream().collect(Collectors.toMap(LogLineParser::format, Function.identity()));
    }

    public ParseResult parse(LogFormat format, List<String> lines, Instant now) {
        LogLineParser parser = parsers.get(format);
        if (parser == null) {
            throw new BadRequestException("Unsupported log format: " + format);
        }
        List<ParsedRecord> records = new ArrayList<>();
        List<String> samples = new ArrayList<>();
        List<String> header = List.of();
        int skipped = 0;
        int errors = 0;
        int lineNo = 0;
        for (String raw : lines) {
            lineNo++;
            String line = raw == null ? "" : stripBom(raw).strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (parser.hasHeader() && header.isEmpty()) {
                header = CsvParser.split(line);
                continue;
            }
            try {
                var rec = parser.parse(line, new LogLineParser.Context(now, header));
                if (rec.isPresent()) {
                    records.add(rec.get());
                } else {
                    skipped++;
                }
            } catch (LineParseException e) {
                errors++;
                if (samples.size() < MAX_ERROR_SAMPLES) {
                    samples.add("line " + lineNo + ": " + e.getMessage());
                }
            }
        }
        return new ParseResult(records, skipped, errors, samples);
    }

    private static String stripBom(String s) {
        return !s.isEmpty() && s.charAt(0) == '﻿' ? s.substring(1) : s;
    }
}
