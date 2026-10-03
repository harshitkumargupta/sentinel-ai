package com.sentinelai.evaluation.dto;

public record Metrics(double precision, double recall, double f1, long tp, long fp, long fn) {

    public static Metrics of(long tp, long fp, long fn) {
        double precision = tp + fp == 0 ? 0.0 : (double) tp / (tp + fp);
        double recall = tp + fn == 0 ? 0.0 : (double) tp / (tp + fn);
        double f1 = precision + recall == 0 ? 0.0 : 2 * precision * recall / (precision + recall);
        return new Metrics(round(precision), round(recall), round(f1), tp, fp, fn);
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
