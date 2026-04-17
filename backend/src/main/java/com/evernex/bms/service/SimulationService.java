package com.evernex.bms.service;

import com.evernex.bms.db.TimeUtil;
import com.evernex.bms.domain.Constants;
import com.evernex.bms.domain.Constants.InspectionStep;
import com.evernex.bms.service.VehicleFactoryService.CreateOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Port of services/simulation.js. State machine runs on a 500ms scheduled tick. */
@Service
@Order(2)
public class SimulationService {

    /** Per-car timer state. */
    static class CarState {
        String phase;
        Integer stepIdx;
        long nextAt;
        List<Integer> failedStepIdxs = new ArrayList<>();
        List<Integer> repairQueue = null;
        List<Integer> remainingStepQueue = null;
    }

    private final JdbcTemplate jdbc;
    private final SettingsService settings;
    private final RandomMetricService random;
    private final VehicleFactoryService factory;

    private final Map<String, CarState> carTimers = new ConcurrentHashMap<>();
    private volatile long lastGenAt = 0;
    private volatile boolean started = false;

    public SimulationService(JdbcTemplate jdbc, SettingsService settings,
                             RandomMetricService random, VehicleFactoryService factory) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.random = random;
        this.factory = factory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (started) return;
        factory.initSeqFromDb();
        List<Map<String, Object>> cars = jdbc.queryForList(
            "SELECT car_id, current_status FROM cars WHERE current_status != 'SHIPMENT_COMPLETE'");
        for (Map<String, Object> c : cars) {
            resumeCar((String) c.get("car_id"), (String) c.get("current_status"));
        }
        started = true;
    }

    @Scheduled(fixedDelay = 500)
    public void loop() {
        if (!started) return;
        for (String carId : new ArrayList<>(carTimers.keySet())) {
            try { tickCar(carId); } catch (Exception e) {
                // never let one car kill the loop
                System.err.println("[SIM] tick error for " + carId + ": " + e.getMessage());
            }
        }
        int interval = settings.getInt("vehicle_generation_interval_ms", 10000);
        if (System.currentTimeMillis() - lastGenAt >= interval) {
            try {
                VehicleFactoryService.CreatedVehicle v = factory.create(new CreateOptions(
                    Constants.ARRIVAL, false, null, null, null, null, null));
                resumeCar(v.carId(), Constants.ARRIVAL);
            } catch (Exception e) {
                System.err.println("[SIM] auto-gen error: " + e.getMessage());
            }
            lastGenAt = System.currentTimeMillis();
        }
    }

    private void updateStatus(String carId, String status, String reason) {
        String now = TimeUtil.nowISO();
        jdbc.update("UPDATE cars SET current_status=?, current_status_updated_at=?, updated_at=? WHERE car_id=?",
            status, now, now, carId);
        jdbc.update("INSERT INTO car_status_histories (car_id,status,changed_at,reason) VALUES (?,?,?,?)",
            carId, status, now, reason);
    }

    private Map<String, Object> latestMeasurement(String carId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT bm.* FROM battery_measurements bm " +
            "JOIN batteries b ON b.battery_id=bm.battery_id " +
            "WHERE b.car_id=? ORDER BY bm.inspected_at DESC LIMIT 1", carId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<Map<String, Object>> latestCells(String carId) {
        return jdbc.queryForList(
            "SELECT bcm.*, bc.cell_number FROM battery_cell_measurements bcm " +
            "JOIN battery_cells bc ON bc.cell_id=bcm.cell_id " +
            "JOIN batteries b ON b.battery_id=bc.battery_id " +
            "WHERE b.car_id=? AND bcm.cell_measurement_id IN (" +
            "  SELECT MAX(cell_measurement_id) FROM battery_cell_measurements GROUP BY cell_id" +
            ") ORDER BY bc.cell_number", carId);
    }

    private void insertStepHistory(String carId, InspectionStep step, String status,
                                    String startedAt, String endedAt, String note) {
        jdbc.update(
            "INSERT INTO process_step_histories (car_id,step_name,step_order,step_status,started_at,ended_at,note) VALUES (?,?,?,?,?,?,?)",
            carId, step.name(), step.order(), status, startedAt, endedAt, note);
    }

    private long createAlert(String carId, String type, String message, String severity) {
        String now = TimeUtil.nowISO();
        jdbc.update(
            "INSERT INTO alerts (car_id,alert_type,alert_message,severity,occurred_at,current_status) VALUES (?,?,?,?,?,?)",
            carId, type, message, severity, now, "OPEN");
        Long alertId = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        jdbc.update(
            "INSERT INTO alert_status_histories (alert_id,previous_status,new_status,changed_at,note) VALUES (?,?,?,?,?)",
            alertId, null, "OPEN", now, "자동 발생");
        return alertId == null ? 0 : alertId;
    }

    private static double num(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }

    private record EvalResult(boolean passed, String failNote, String severity) {}

    private EvalResult evalStep(String carId, InspectionStep step) {
        Map<String, Object> meas = latestMeasurement(carId);
        List<Map<String, Object>> cells = latestCells(carId);
        boolean passed = true;
        String failNote = null;
        String kind = null;
        double value = 0;

        switch (step.name()) {
            case "SOC_CHECK" -> {
                double v = num(meas.get("soc"));
                if (!random.isNormal("soc", v)) {
                    passed = false; failNote = "SOC 이상치 " + v + "%"; kind = "soc"; value = v;
                }
            }
            case "SOH_CHECK" -> {
                double v = num(meas.get("soh"));
                if (!random.isNormal("soh", v)) {
                    passed = false; failNote = "SOH 이상치 " + v + "%"; kind = "soh"; value = v;
                }
            }
            case "SOP_CHECK" -> {
                double v = num(meas.get("sop"));
                if (!random.isNormal("sop", v)) {
                    passed = false; failNote = "SOP 이상치 " + v + "%"; kind = "sop"; value = v;
                }
            }
            case "PACK_VOLTAGE_CHECK" -> {
                double v = num(meas.get("avg_voltage"));
                if (!random.isNormal("pack_voltage", v)) {
                    passed = false; failNote = "팩 전압 이상치 " + v + "V"; kind = "pack_voltage"; value = v;
                }
            }
            case "CELL_TEMPERATURE_CHECK" -> {
                Constants.Range r = Constants.RANGES.get("cell_temperature");
                int badCount = 0;
                Object worstNum = null;
                double worstDev = -1, worstV = 0;
                for (Map<String, Object> c : cells) {
                    double v = num(c.get("cell_temperature"));
                    if (v < r.min() || v > r.max()) {
                        badCount++;
                        double dev = v < r.min() ? (r.min() - v) : (v - r.max());
                        if (dev > worstDev) { worstDev = dev; worstNum = c.get("cell_number"); worstV = v; }
                    }
                }
                if (badCount > 0) {
                    passed = false;
                    failNote = badCount + "개 셀 온도 이상 · 최악 셀 #" + worstNum + " (" + String.format("%.2f", worstV) + "℃)";
                    kind = "cell_temperature"; value = worstV;
                }
            }
            case "CELL_VOLTAGE_CHECK" -> {
                Constants.Range r = Constants.RANGES.get("cell_voltage");
                int badCount = 0;
                Object worstNum = null;
                double worstDev = -1, worstV = 0;
                for (Map<String, Object> c : cells) {
                    double v = num(c.get("cell_voltage"));
                    if (v < r.min() || v > r.max()) {
                        badCount++;
                        double dev = v < r.min() ? (r.min() - v) : (v - r.max());
                        if (dev > worstDev) { worstDev = dev; worstNum = c.get("cell_number"); worstV = v; }
                    }
                }
                if (badCount > 0) {
                    passed = false;
                    failNote = badCount + "개 셀 전압 이상 · 최악 셀 #" + worstNum + " (" + String.format("%.3f", worstV) + "V)";
                    kind = "cell_voltage"; value = worstV;
                }
            }
            default -> {}
        }
        String severity = passed ? null : java.util.Optional.ofNullable(random.classifySeverity(kind, value)).orElse("HIGH");
        return new EvalResult(passed, failNote, severity);
    }

    private void startStep(String carId, int stepIdx) {
        InspectionStep step = Constants.INSPECTION_STEPS.get(stepIdx);
        String now = TimeUtil.nowISO();
        String targetStatus = stepIdx <= 3 ? Constants.BATTERY_INSPECTION : Constants.CELL_INSPECTION;
        List<String> curRows = jdbc.queryForList(
            "SELECT current_status FROM cars WHERE car_id=?", String.class, carId);
        String cur = curRows.isEmpty() ? null : curRows.get(0);
        if (cur != null && cur.equals(Constants.RE_INSPECTION)) {
            // keep
        } else if (!targetStatus.equals(cur)) {
            updateStatus(carId, targetStatus, step.labelKR() + " 시작");
        }
        insertStepHistory(carId, step, "IN_PROGRESS", now, null, null);
        int dur = settings.getInt(step.durationKey(), 10000);
        CarState st = carTimers.computeIfAbsent(carId, k -> new CarState());
        st.phase = "STEP";
        st.stepIdx = stepIdx;
        st.nextAt = System.currentTimeMillis() + dur;
    }

    private void finishStep(String carId, int stepIdx) {
        InspectionStep step = Constants.INSPECTION_STEPS.get(stepIdx);
        String now = TimeUtil.nowISO();
        EvalResult r = evalStep(carId, step);
        insertStepHistory(carId, step, r.passed ? "PASS" : "FAIL", null, now, r.failNote);
        if (!r.passed) {
            createAlert(carId, step.name(), step.labelKR() + " 실패: " + r.failNote, r.severity);
            jdbc.update(
                "INSERT INTO inspection_results (car_id,status,reason,performance_status,safety_status,evaluated_at) VALUES (?,?,?,?,?,?)",
                carId, "FAIL", r.failNote, "FAIL", "FAIL", now);
            CarState st = carTimers.computeIfAbsent(carId, k -> new CarState());
            st.failedStepIdxs.add(stepIdx);
        }
    }

    private double repairMultiplier() {
        double m = settings.getDouble("repair_duration_multiplier", 2);
        return m > 0 ? m : 2;
    }

    private long repairDurationFor(int stepIdx) {
        InspectionStep step = Constants.INSPECTION_STEPS.get(stepIdx);
        return (long) (settings.getInt(step.durationKey(), 10000) * repairMultiplier());
    }

    private void enterQaFromFailures(String carId) {
        CarState st = carTimers.getOrDefault(carId, new CarState());
        List<Integer> failed = new ArrayList<>(st.failedStepIdxs);
        updateStatus(carId, Constants.ANOMALY_DETECTED, "검사 실패 항목 발견");
        CarState trans = new CarState();
        trans.phase = "QA_TRANSITION";
        trans.failedStepIdxs = failed;
        trans.nextAt = System.currentTimeMillis() + 60_000;
        carTimers.put(carId, trans);
        // async transition
        new Thread(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            updateStatus(carId, Constants.QA_MAINTENANCE, "자동 QA 진입");
            CarState ns = new CarState();
            ns.failedStepIdxs = failed;
            if (failed.isEmpty()) {
                int dur = settings.getInt("qa_maintenance_duration_ms", 10000);
                ns.phase = "QA_WAIT_RESOLVE";
                ns.nextAt = System.currentTimeMillis() + dur;
            } else {
                ns.phase = "QA_REPAIR_STEP";
                ns.repairQueue = new ArrayList<>(failed);
                ns.nextAt = System.currentTimeMillis() + repairDurationFor(failed.get(0));
            }
            carTimers.put(carId, ns);
        }, "sim-qa-" + carId).start();
    }

    private void repairOneStep(String carId, int stepIdx) {
        String now = TimeUtil.nowISO();
        List<Long> bRows = jdbc.queryForList("SELECT battery_id FROM batteries WHERE car_id=?", Long.class, carId);
        if (bRows.isEmpty()) return;
        long batteryId = bRows.get(0);
        Map<String, Object> latest = latestMeasurement(carId);
        if (stepIdx <= 3 && latest != null) {
            double newSoc = stepIdx == 0 ? random.clampRange("soc", random.genMetric("soc", false)) : num(latest.get("soc"));
            double newSoh = stepIdx == 1 ? random.clampRange("soh", random.genMetric("soh", false)) : num(latest.get("soh"));
            double newSop = stepIdx == 2 ? random.clampRange("sop", random.genMetric("sop", false)) : num(latest.get("sop"));
            double newPackV = stepIdx == 3
                ? random.clampRange("pack_voltage", random.genMetric("pack_voltage", false))
                : num(latest.get("avg_voltage"));
            jdbc.update(
                "INSERT INTO battery_measurements (battery_id,inspected_at,soc,soh,sop,avg_voltage,avg_temperature,temperature_deviation) VALUES (?,?,?,?,?,?,?,?)",
                batteryId, now, newSoc, newSoh, newSop, newPackV,
                num(latest.get("avg_temperature")), num(latest.get("temperature_deviation")));
        } else if (stepIdx == 4 || stepIdx == 5) {
            List<Map<String, Object>> cellRows = latestCells(carId);
            for (Map<String, Object> c : cellRows) {
                double newTemp = stepIdx == 4
                    ? random.clampRange("cell_temperature", random.genMetric("cell_temperature", false))
                    : num(c.get("cell_temperature"));
                double newVolt = stepIdx == 5
                    ? random.clampRange("cell_voltage", random.genMetric("cell_voltage", false))
                    : num(c.get("cell_voltage"));
                jdbc.update(
                    "INSERT INTO battery_cell_measurements (cell_id,measured_at,cell_temperature,cell_voltage) VALUES (?,?,?,?)",
                    c.get("cell_id"), now, newTemp, newVolt);
            }
        }
    }

    private void finalizeRepairAndStartReWait(String carId, List<Integer> failedStepIdxs, Long userId) {
        String now = TimeUtil.nowISO();
        List<Map<String, Object>> open = jdbc.queryForList(
            "SELECT alert_id, current_status FROM alerts WHERE car_id=? AND current_status!='RESOLVED'", carId);
        for (Map<String, Object> a : open) {
            jdbc.update("UPDATE alerts SET current_status='RESOLVED', resolved_at=? WHERE alert_id=?",
                now, a.get("alert_id"));
            jdbc.update(
                "INSERT INTO alert_status_histories (alert_id,previous_status,new_status,changed_by_user_id,changed_at,note) VALUES (?,?,?,?,?,?)",
                a.get("alert_id"), a.get("current_status"), "RESOLVED", userId, now,
                userId != null ? "운영자 해결" : "자동 수리 완료");
        }
        updateStatus(carId, Constants.RE_INSPECTION_WAITING, "수리 완료 — 재검사 대기");
        int dur = settings.getInt("re_inspection_duration_ms", 10000);
        CarState ns = new CarState();
        ns.phase = "RE_WAIT";
        ns.failedStepIdxs = new ArrayList<>(failedStepIdxs);
        ns.nextAt = System.currentTimeMillis() + dur;
        carTimers.put(carId, ns);
    }

    /** Clamps all measurement + cell values into the normal range. Called right before each re-inspection. */
    private void regenerateAllMeasurementsNormal(String carId) {
        List<Long> bRows = jdbc.queryForList("SELECT battery_id FROM batteries WHERE car_id=?", Long.class, carId);
        if (bRows.isEmpty()) return;
        long batteryId = bRows.get(0);
        String now = TimeUtil.nowISO();
        jdbc.update(
            "INSERT INTO battery_measurements (battery_id,inspected_at,soc,soh,sop,avg_voltage,avg_temperature,temperature_deviation) VALUES (?,?,?,?,?,?,?,?)",
            batteryId, now,
            random.clampRange("soc", random.genMetric("soc", false)),
            random.clampRange("soh", random.genMetric("soh", false)),
            random.clampRange("sop", random.genMetric("sop", false)),
            random.clampRange("pack_voltage", random.genMetric("pack_voltage", false)),
            random.clampRange("cell_temperature", random.genMetric("cell_temperature", false)),
            0.5);
        List<Long> cells = jdbc.queryForList(
            "SELECT cell_id FROM battery_cells WHERE battery_id=?", Long.class, batteryId);
        for (Long cid : cells) {
            jdbc.update(
                "INSERT INTO battery_cell_measurements (cell_id,measured_at,cell_temperature,cell_voltage) VALUES (?,?,?,?)",
                cid, now,
                random.clampRange("cell_temperature", random.genMetric("cell_temperature", false)),
                random.clampRange("cell_voltage", random.genMetric("cell_voltage", false)));
        }
    }

    /** Re-inspection always normalizes every value first, then runs all 6 steps from the start. */
    private void startReInspectionChain(String carId) {
        regenerateAllMeasurementsNormal(carId);
        List<Integer> queue = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
        updateStatus(carId, Constants.RE_INSPECTION, "모든 값 정상화 후 재검사 시작");
        CarState ns = new CarState();
        ns.phase = "STEP";
        ns.stepIdx = queue.get(0);
        ns.remainingStepQueue = queue;
        ns.failedStepIdxs = new ArrayList<>();
        ns.nextAt = System.currentTimeMillis();
        carTimers.put(carId, ns);
        startStep(carId, queue.get(0));
    }

    private void startShipmentWaiting(String carId) {
        updateStatus(carId, Constants.SHIPMENT_WAITING, "출고 대기");
        int dur = settings.getInt("shipment_waiting_duration_ms", 10000);
        CarState ns = new CarState();
        ns.phase = "SHIPMENT_WAIT";
        ns.nextAt = System.currentTimeMillis() + dur;
        carTimers.put(carId, ns);
    }

    private void markShipped(String carId) {
        updateStatus(carId, Constants.SHIPMENT_COMPLETE, "출고 완료");
        carTimers.remove(carId);
    }

    private void beginNewVehicle(String carId) {
        updateStatus(carId, Constants.BATTERY_INSPECTION, "검사 시작");
        CarState ns = new CarState();
        carTimers.put(carId, ns);
        startStep(carId, 0);
    }

    public boolean resolveAlertAndReinspect(String carId, Long userId) {
        List<Long> bRows = jdbc.queryForList("SELECT battery_id FROM batteries WHERE car_id=?", Long.class, carId);
        if (bRows.isEmpty()) return false;
        long batteryId = bRows.get(0);
        CarState st = carTimers.getOrDefault(carId, new CarState());
        List<Integer> failed = st.failedStepIdxs == null ? new ArrayList<>() : new ArrayList<>(st.failedStepIdxs);
        List<Integer> remaining = st.repairQueue != null ? new ArrayList<>(st.repairQueue) : new ArrayList<>(failed);

        if (remaining.isEmpty() && failed.isEmpty()) {
            String now = TimeUtil.nowISO();
            jdbc.update(
                "INSERT INTO battery_measurements (battery_id,inspected_at,soc,soh,sop,avg_voltage,avg_temperature,temperature_deviation) VALUES (?,?,?,?,?,?,?,?)",
                batteryId, now,
                random.clampRange("soc", random.genMetric("soc", false)),
                random.clampRange("soh", random.genMetric("soh", false)),
                random.clampRange("sop", random.genMetric("sop", false)),
                random.clampRange("pack_voltage", random.genMetric("pack_voltage", false)),
                random.clampRange("cell_temperature", random.genMetric("cell_temperature", false)),
                0.5);
            List<Long> cells = jdbc.queryForList(
                "SELECT cell_id FROM battery_cells WHERE battery_id=?", Long.class, batteryId);
            for (Long cid : cells) {
                jdbc.update(
                    "INSERT INTO battery_cell_measurements (cell_id,measured_at,cell_temperature,cell_voltage) VALUES (?,?,?,?)",
                    cid, now,
                    random.clampRange("cell_temperature", random.genMetric("cell_temperature", false)),
                    random.clampRange("cell_voltage", random.genMetric("cell_voltage", false)));
            }
        } else {
            for (Integer idx : remaining) repairOneStep(carId, idx);
        }

        finalizeRepairAndStartReWait(carId, failed, userId);
        return true;
    }

    private void resumeCar(String carId, String status) {
        long now = System.currentTimeMillis();
        long small = now + 1500;
        CarState ns = new CarState();
        switch (status) {
            case Constants.ARRIVAL -> { ns.phase = "ARRIVAL"; ns.nextAt = small; carTimers.put(carId, ns); }
            case Constants.BATTERY_INSPECTION, Constants.CELL_INSPECTION -> { carTimers.put(carId, ns); startStep(carId, 0); }
            case Constants.ANOMALY_DETECTED -> { ns.phase = "ANOMALY_WAIT"; ns.nextAt = small; carTimers.put(carId, ns); }
            case Constants.QA_MAINTENANCE -> {
                int dur = settings.getInt("qa_maintenance_duration_ms", 10000);
                ns.phase = "QA_WAIT_RESOLVE"; ns.nextAt = now + dur; carTimers.put(carId, ns);
            }
            case Constants.RE_INSPECTION_WAITING -> {
                int dur = settings.getInt("re_inspection_duration_ms", 10000);
                ns.phase = "RE_WAIT"; ns.nextAt = now + dur; carTimers.put(carId, ns);
            }
            case Constants.RE_INSPECTION -> { carTimers.put(carId, ns); startStep(carId, 0); }
            case Constants.BATTERY_QC_COMPLETE -> { ns.phase = "BQC_DONE"; ns.nextAt = small; carTimers.put(carId, ns); }
            case Constants.SHIPMENT_WAITING -> {
                int dur = settings.getInt("shipment_waiting_duration_ms", 10000);
                ns.phase = "SHIPMENT_WAIT"; ns.nextAt = now + dur; carTimers.put(carId, ns);
            }
            default -> {}
        }
    }

    private void tickCar(String carId) {
        CarState state = carTimers.get(carId);
        if (state == null || System.currentTimeMillis() < state.nextAt) return;
        List<String> carRows = jdbc.queryForList(
            "SELECT current_status FROM cars WHERE car_id=?", String.class, carId);
        if (carRows.isEmpty()) { carTimers.remove(carId); return; }

        String phase = state.phase;
        if (phase == null) return;

        switch (phase) {
            case "ARRIVAL" -> beginNewVehicle(carId);
            case "STEP" -> {
                int justFinished = state.stepIdx == null ? 0 : state.stepIdx;
                finishStep(carId, justFinished);
                CarState updated = carTimers.getOrDefault(carId, state);
                List<Integer> queue = updated.remainingStepQueue;
                if (queue != null) {
                    List<Integer> nextQueue = new ArrayList<>();
                    for (Integer i : queue) if (i != justFinished) nextQueue.add(i);
                    if (!nextQueue.isEmpty()) {
                        updated.remainingStepQueue = nextQueue;
                        carTimers.put(carId, updated);
                        startStep(carId, nextQueue.get(0));
                    } else {
                        List<Integer> roundFails = updated.failedStepIdxs == null ? Collections.emptyList() : updated.failedStepIdxs;
                        if (!roundFails.isEmpty()) {
                            updated.remainingStepQueue = null;
                            carTimers.put(carId, updated);
                            enterQaFromFailures(carId);
                        } else {
                            updateStatus(carId, Constants.BATTERY_QC_COMPLETE, "재검사 통과");
                            CarState ns = new CarState();
                            ns.phase = "BQC_DONE";
                            ns.nextAt = System.currentTimeMillis() + 1500;
                            carTimers.put(carId, ns);
                        }
                    }
                } else {
                    int nextIdx = justFinished + 1;
                    if (nextIdx >= Constants.INSPECTION_STEPS.size()) {
                        List<Integer> fails = updated.failedStepIdxs == null ? Collections.emptyList() : updated.failedStepIdxs;
                        if (!fails.isEmpty()) {
                            enterQaFromFailures(carId);
                        } else {
                            updateStatus(carId, Constants.BATTERY_QC_COMPLETE, "모든 검사 통과");
                            CarState ns = new CarState();
                            ns.phase = "BQC_DONE";
                            ns.nextAt = System.currentTimeMillis() + 1500;
                            carTimers.put(carId, ns);
                        }
                    } else {
                        startStep(carId, nextIdx);
                    }
                }
            }
            case "QA_REPAIR_STEP" -> {
                List<Integer> queue = state.repairQueue != null ? state.repairQueue : Collections.emptyList();
                if (queue.isEmpty()) {
                    finalizeRepairAndStartReWait(carId, state.failedStepIdxs == null ? Collections.emptyList() : state.failedStepIdxs, null);
                    return;
                }
                int currentIdx = queue.get(0);
                List<Integer> rest = queue.size() > 1 ? new ArrayList<>(queue.subList(1, queue.size())) : new ArrayList<>();
                repairOneStep(carId, currentIdx);
                if (!rest.isEmpty()) {
                    state.repairQueue = rest;
                    state.nextAt = System.currentTimeMillis() + repairDurationFor(rest.get(0));
                } else {
                    finalizeRepairAndStartReWait(carId, state.failedStepIdxs == null ? Collections.emptyList() : state.failedStepIdxs, null);
                }
            }
            case "QA_WAIT_RESOLVE" -> resolveAlertAndReinspect(carId, null);
            case "RE_WAIT" -> startReInspectionChain(carId);
            case "BQC_DONE" -> startShipmentWaiting(carId);
            case "SHIPMENT_WAIT" -> markShipped(carId);
            case "ANOMALY_WAIT" -> {
                updateStatus(carId, Constants.QA_MAINTENANCE, "자동 QA 진입");
                int dur = settings.getInt("qa_maintenance_duration_ms", 10000);
                CarState ns = new CarState();
                ns.phase = "QA_WAIT_RESOLVE";
                ns.nextAt = System.currentTimeMillis() + dur;
                carTimers.put(carId, ns);
            }
            default -> {}
        }
    }

    /** Called externally after createVehicle from the admin UI, to kick off its lifecycle. */
    public void registerNewVehicle(String carId) {
        resumeCar(carId, Constants.ARRIVAL);
    }
}
