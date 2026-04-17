package com.evernex.bms.service;

import com.evernex.bms.domain.Constants;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Port of services/random.js. */
@Service
public class RandomMetricService {

    private static final Map<String, double[]> SEVERITY_THRESHOLDS = Map.of(
        "soc", new double[]{2, 5, 10},
        "soh", new double[]{2, 5, 10},
        "sop", new double[]{3, 8, 15},
        "pack_voltage", new double[]{5, 15, 30},
        "cell_temperature", new double[]{2, 5, 10},
        "cell_voltage", new double[]{0.05, 0.15, 0.30}
    );

    private final SettingsService settings;

    public RandomMetricService(SettingsService settings) { this.settings = settings; }

    private static double uniform(double min, double max) {
        return ThreadLocalRandom.current().nextDouble() * (max - min) + min;
    }

    private static double gaussianInRange(double min, double max) {
        double u, v;
        do { u = ThreadLocalRandom.current().nextDouble(); } while (u == 0);
        do { v = ThreadLocalRandom.current().nextDouble(); } while (v == 0);
        double z = Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
        double mean = (min + max) / 2;
        double std = (max - min) / 6;
        double val = mean + z * std;
        if (val < min) val = min + ThreadLocalRandom.current().nextDouble() * (max - min) * 0.1;
        if (val > max) val = max - ThreadLocalRandom.current().nextDouble() * (max - min) * 0.1;
        return val;
    }

    private static double abnormalValue(double min, double max) {
        boolean low = ThreadLocalRandom.current().nextDouble() < 0.5;
        double span = max - min;
        if (low) return round3(min - uniform(0.05 * span, 0.3 * span));
        return round3(max + uniform(0.05 * span, 0.3 * span));
    }

    private static double round3(double v) { return Math.round(v * 1000.0) / 1000.0; }

    private boolean roll(String probKey) {
        int prob = settings.getInt(probKey, 99);
        return ThreadLocalRandom.current().nextDouble() * 100 <= prob;
    }

    public double genMetric(String kind, boolean forceAbnormal) {
        Constants.Range r = Constants.RANGES.get(kind);
        String probKey = "prob_" + kind + "_normal";
        boolean normal = !forceAbnormal && roll(probKey);
        if (normal) return round3(gaussianInRange(r.min(), r.max()));
        return abnormalValue(r.min(), r.max());
    }

    public boolean isNormal(String kind, double value) {
        Constants.Range r = Constants.RANGES.get(kind);
        return value >= r.min() && value <= r.max();
    }

    /** Returns null if value is within range. */
    public String classifySeverity(String kind, double value) {
        Constants.Range r = Constants.RANGES.get(kind);
        if (r == null) return "HIGH";
        double delta;
        if (value < r.min()) delta = r.min() - value;
        else if (value > r.max()) delta = value - r.max();
        else return null;
        double[] t = SEVERITY_THRESHOLDS.get(kind);
        if (t == null) return "HIGH";

        boolean overLimit = value > r.max();
        boolean escalate = overLimit && ("cell_temperature".equals(kind) || "cell_voltage".equals(kind));

        String sev;
        if (delta <= t[0]) sev = "LOW";
        else if (delta <= t[1]) sev = "MEDIUM";
        else if (delta <= t[2]) sev = "HIGH";
        else sev = "CRITICAL";

        if (escalate) {
            sev = switch (sev) {
                case "LOW" -> "MEDIUM";
                case "MEDIUM" -> "HIGH";
                default -> "CRITICAL";
            };
        }
        return sev;
    }

    public double clampRange(String kind, double v) {
        Constants.Range r = Constants.RANGES.get(kind);
        return Math.min(Math.max(v, r.min() + 0.1), r.max() - 0.1);
    }
}
