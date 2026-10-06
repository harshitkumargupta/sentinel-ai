package com.sentinelai.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Per-endpoint-group token-bucket limits (capacity = burst; refill = sustained/sec). */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "sentinel.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    private Bucket login = new Bucket(5, 0.1);        // strict: ~5 burst, 6/min
    private Bucket ingest = new Bucket(2000, 200.0);  // generous
    private Bucket defaults = new Bucket(120, 2.0);

    @Getter
    @Setter
    public static class Bucket {
        private int capacity;
        private double refillPerSecond;

        public Bucket() {
        }

        public Bucket(int capacity, double refillPerSecond) {
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
        }
    }
}
