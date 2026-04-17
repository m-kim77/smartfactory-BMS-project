package com.evernex.bms.domain;

import java.util.List;
import java.util.Map;

/** 1:1 port of services/constants.js. */
public final class Constants {
    private Constants() {}

    public static final String ARRIVAL = "ARRIVAL";
    public static final String BATTERY_INSPECTION = "BATTERY_INSPECTION";
    public static final String CELL_INSPECTION = "CELL_INSPECTION";
    public static final String ANOMALY_DETECTED = "ANOMALY_DETECTED";
    public static final String QA_MAINTENANCE = "QA_MAINTENANCE";
    public static final String RE_INSPECTION_WAITING = "RE_INSPECTION_WAITING";
    public static final String RE_INSPECTION = "RE_INSPECTION";
    public static final String BATTERY_QC_COMPLETE = "BATTERY_QC_COMPLETE";
    public static final String SHIPMENT_WAITING = "SHIPMENT_WAITING";
    public static final String SHIPMENT_COMPLETE = "SHIPMENT_COMPLETE";

    public static final List<String> ALL_STATUSES = List.of(
        ARRIVAL, BATTERY_INSPECTION, CELL_INSPECTION, ANOMALY_DETECTED,
        QA_MAINTENANCE, RE_INSPECTION_WAITING, RE_INSPECTION,
        BATTERY_QC_COMPLETE, SHIPMENT_WAITING, SHIPMENT_COMPLETE
    );

    public static final List<String> MODELS = List.of(
        "볼트 S", "노바 X5", "노바 X9", "노바 GT60", "노바 GT70e",
        "시티버스 E", "벡터 E3", "벡터 E4", "벡터 E6", "벡터 E9", "벡터 V5", "벡터 밴 EV"
    );

    public static final Map<String, List<String>> FACTORY_MODELS = Map.of(
        "청림공장", List.of("볼트 S", "노바 X5", "노바 GT60", "노바 GT70e"),
        "은하공장", List.of("노바 X9"),
        "백운공장", List.of("시티버스 E"),
        "단풍공장", List.of("벡터 E9", "벡터 E3", "벡터 E4"),
        "태양공장", List.of("벡터 E6", "벡터 V5"),
        "한빛공장", List.of("벡터 밴 EV")
    );

    public record InspectionStep(String name, int order, String labelKR, String durationKey) {}

    public static final List<InspectionStep> INSPECTION_STEPS = List.of(
        new InspectionStep("SOC_CHECK", 1, "SOC 검사", "inspection_soc_duration_ms"),
        new InspectionStep("SOH_CHECK", 2, "SOH 검사", "inspection_soh_duration_ms"),
        new InspectionStep("SOP_CHECK", 3, "SOP 검사", "inspection_sop_duration_ms"),
        new InspectionStep("PACK_VOLTAGE_CHECK", 4, "팩 전압 검사", "inspection_pack_voltage_duration_ms"),
        new InspectionStep("CELL_TEMPERATURE_CHECK", 5, "셀 온도 검사", "inspection_cell_duration_ms"),
        new InspectionStep("CELL_VOLTAGE_CHECK", 6, "셀 전압 검사", "inspection_cell_duration_ms")
    );

    public record Range(double min, double max) {}

    public static final Map<String, Range> RANGES = Map.of(
        "soc", new Range(90, 100),
        "soh", new Range(95, 100),
        "sop", new Range(90, 100),
        "pack_voltage", new Range(350, 400),
        "cell_temperature", new Range(5, 32),
        "cell_voltage", new Range(3.6, 4.2)
    );
}
