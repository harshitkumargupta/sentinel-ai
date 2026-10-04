package com.sentinelai.baseline;

/** Welford's online mean/variance. Deterministic and numerically stable. */
public final class WelfordState {

    private long count;
    private double mean;
    private double m2;

    public WelfordState() {
    }

    public WelfordState(long count, double mean, double m2) {
        this.count = count;
        this.mean = mean;
        this.m2 = m2;
    }

    public synchronized void observe(double x) {
        count++;
        double delta = x - mean;
        mean += delta / count;
        m2 += delta * (x - mean);
    }

    public long count() {
        return count;
    }

    public double mean() {
        return mean;
    }

    public double m2() {
        return m2;
    }

    /** Sample standard deviation (needs ≥ 2 observations). */
    public double std() {
        return count < 2 ? 0.0 : Math.sqrt(m2 / (count - 1));
    }

    /** z-score of a value; 0 if std is 0. */
    public double zScore(double x) {
        double s = std();
        return s == 0 ? 0.0 : (x - mean) / s;
    }
}
