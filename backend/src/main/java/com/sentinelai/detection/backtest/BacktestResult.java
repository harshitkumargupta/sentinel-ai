package com.sentinelai.detection.backtest;

import java.util.List;

public record BacktestResult(
        long eventsScanned,
        long alertsFired,
        List<String> sampleAlerts) {
}
