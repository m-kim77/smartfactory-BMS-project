package com.evernex.bms.controller;

import com.evernex.bms.db.FactoryScope;
import com.evernex.bms.db.FactoryScope.Clause;
import com.evernex.bms.db.TimeUtil;
import com.evernex.bms.domain.Constants;
import com.evernex.bms.security.AuthContext;
import com.evernex.bms.service.SettingsService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final JdbcTemplate jdbc;
    private final SettingsService settings;

    public DashboardController(JdbcTemplate jdbc, SettingsService settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    private Map<String, Object> currentShift() {
        int durationMin = settings.getInt("shift_duration_min", 30);
        long shiftMs = durationMin * 60_000L;
        long now = System.currentTimeMillis();
        long startMs = (now / shiftMs) * shiftMs;
        long endMs = startMs + shiftMs;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("shiftStart", TimeUtil.fromMillis(startMs));
        out.put("shiftEnd", TimeUtil.fromMillis(endMs));
        out.put("durationMin", durationMin);
        return out;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam(required = false, name = "factory_ids") String factoryIds) {
        var p = AuthContext.require();
        Map<String, Object> shift = currentShift();
        String shiftStart = (String) shift.get("shiftStart");

        List<Long> requested = FactoryScope.parseIdsParam(factoryIds);
        List<Long> effective = FactoryScope.intersect(requested, p.allowedFactoryIds());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.putAll(shift);

        if (effective.isEmpty()) {
            Map<String, Integer> byStatus = new LinkedHashMap<>();
            Constants.ALL_STATUSES.forEach(s -> byStatus.put(s, 0));
            resp.put("total", 0);
            resp.put("byStatus", byStatus);
            resp.put("openAlerts", 0);
            resp.put("recentAlerts", List.of());
            for (String k : new String[]{"inspecting", "anomalies", "arrival", "reInspectionWaiting",
                    "qcComplete", "shipmentWaiting", "shipped"}) resp.put(k, 0);
            for (String k : new String[]{"inspectingCars", "anomalyCars", "arrivalCars",
                    "reInspWaitCars", "qcCompleteCars", "shipWaitingCars"}) resp.put(k, List.of());
            return resp;
        }

        Clause fScope = FactoryScope.clause(effective, "factory_id");
        Object[] fParamsArr = fScope.params().toArray();

        Object[] shiftAndFactory = concat(new Object[]{shiftStart}, fParamsArr);
        Integer total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM cars WHERE created_at >= ?" + fScope.sql(),
            Integer.class, shiftAndFactory);
        List<Map<String, Object>> statusRows = jdbc.queryForList(
            "SELECT current_status AS status, COUNT(*) AS count FROM cars WHERE created_at >= ?" + fScope.sql() + " GROUP BY current_status",
            shiftAndFactory);
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        Constants.ALL_STATUSES.forEach(s -> byStatus.put(s, 0));
        for (Map<String, Object> r : statusRows) {
            byStatus.put((String) r.get("status"), ((Number) r.get("count")).intValue());
        }

        Clause aScope = FactoryScope.clause(effective, "c.factory_id");
        Integer openAlerts = jdbc.queryForObject(
            "SELECT COUNT(*) FROM alerts a JOIN cars c ON c.car_id=a.car_id WHERE a.current_status!='RESOLVED'" + aScope.sql(),
            Integer.class, aScope.params().toArray());

        int shipped = byStatus.getOrDefault(Constants.SHIPMENT_COMPLETE, 0);
        int inspecting = byStatus.getOrDefault(Constants.BATTERY_INSPECTION, 0)
            + byStatus.getOrDefault(Constants.CELL_INSPECTION, 0)
            + byStatus.getOrDefault(Constants.RE_INSPECTION, 0);
        int anomalies = byStatus.getOrDefault(Constants.ANOMALY_DETECTED, 0)
            + byStatus.getOrDefault(Constants.QA_MAINTENANCE, 0);
        int arrival = byStatus.getOrDefault(Constants.ARRIVAL, 0);
        int reInspectionWaiting = byStatus.getOrDefault(Constants.RE_INSPECTION_WAITING, 0);
        int qcComplete = byStatus.getOrDefault(Constants.BATTERY_QC_COMPLETE, 0);
        int shipmentWaiting = byStatus.getOrDefault(Constants.SHIPMENT_WAITING, 0);

        Object[] recentArgs = concat(new Object[]{shiftStart}, aScope.params().toArray());
        List<Map<String, Object>> recentAlerts = jdbc.queryForList(
            "SELECT a.*, c.model_name FROM alerts a LEFT JOIN cars c ON c.car_id=a.car_id WHERE a.occurred_at >= ?" + aScope.sql() +
            " ORDER BY a.occurred_at DESC LIMIT 10", recentArgs);

        String[] inspStatuses = {Constants.BATTERY_INSPECTION, Constants.CELL_INSPECTION, Constants.RE_INSPECTION};
        String[] anomStatuses = {Constants.ANOMALY_DETECTED, Constants.QA_MAINTENANCE};
        String baseCols = "SELECT car_id, model_name, current_status, updated_at FROM cars WHERE created_at >= ?" + fScope.sql() + " AND current_status";

        List<Map<String, Object>> inspectingCars = enrichCars(queryStatuses(baseCols, shiftStart, fParamsArr, inspStatuses));
        List<Map<String, Object>> anomalyCars = enrichCars(queryStatuses(baseCols, shiftStart, fParamsArr, anomStatuses));
        List<Map<String, Object>> arrivalCars = enrichCars(queryStatuses(baseCols, shiftStart, fParamsArr, new String[]{Constants.ARRIVAL}));
        List<Map<String, Object>> reInspWaitCars = enrichCars(queryStatuses(baseCols, shiftStart, fParamsArr, new String[]{Constants.RE_INSPECTION_WAITING}));
        List<Map<String, Object>> qcCompleteCars = enrichCars(queryStatuses(baseCols, shiftStart, fParamsArr, new String[]{Constants.BATTERY_QC_COMPLETE}));
        List<Map<String, Object>> shipWaitingCars = enrichCars(queryStatuses(baseCols, shiftStart, fParamsArr, new String[]{Constants.SHIPMENT_WAITING}));

        resp.put("total", total == null ? 0 : total);
        resp.put("byStatus", byStatus);
        resp.put("openAlerts", openAlerts == null ? 0 : openAlerts);
        resp.put("recentAlerts", recentAlerts);
        resp.put("inspecting", inspecting);
        resp.put("anomalies", anomalies);
        resp.put("arrival", arrival);
        resp.put("reInspectionWaiting", reInspectionWaiting);
        resp.put("qcComplete", qcComplete);
        resp.put("shipmentWaiting", shipmentWaiting);
        resp.put("shipped", shipped);
        resp.put("inspectingCars", inspectingCars);
        resp.put("anomalyCars", anomalyCars);
        resp.put("arrivalCars", arrivalCars);
        resp.put("reInspWaitCars", reInspWaitCars);
        resp.put("qcCompleteCars", qcCompleteCars);
        resp.put("shipWaitingCars", shipWaitingCars);
        return resp;
    }

    private List<Map<String, Object>> queryStatuses(String baseCols, String shiftStart, Object[] fParams, String[] statuses) {
        if (statuses.length == 1) {
            Object[] args = concat(new Object[]{shiftStart}, concat(fParams, new Object[]{statuses[0]}));
            return jdbc.queryForList(baseCols + "=? ORDER BY updated_at DESC LIMIT 30", args);
        }
        String ph = String.join(",", Collections.nCopies(statuses.length, "?"));
        Object[] args = concat(new Object[]{shiftStart}, concat(fParams, statuses));
        return jdbc.queryForList(baseCols + " IN (" + ph + ") ORDER BY updated_at DESC LIMIT 30", args);
    }

    private List<Map<String, Object>> enrichCars(List<Map<String, Object>> cars) {
        if (cars.isEmpty()) return cars;
        List<Object> carIds = new ArrayList<>();
        for (Map<String, Object> c : cars) carIds.add(c.get("car_id"));
        String ph = String.join(",", Collections.nCopies(carIds.size(), "?"));

        List<Map<String, Object>> meas = jdbc.queryForList(
            "SELECT c.car_id, bm.soc, bm.soh, bm.sop, bm.avg_voltage, bm.avg_temperature " +
            "FROM cars c JOIN batteries b ON b.car_id = c.car_id " +
            "JOIN battery_measurements bm ON bm.measurement_id = (" +
            "  SELECT measurement_id FROM battery_measurements WHERE battery_id = b.battery_id " +
            "  ORDER BY inspected_at DESC, measurement_id DESC LIMIT 1" +
            ") WHERE c.car_id IN (" + ph + ")", carIds.toArray());

        List<Object> stepArgs = new ArrayList<>(carIds);
        stepArgs.addAll(carIds);
        List<Map<String, Object>> steps = jdbc.queryForList(
            "SELECT car_id, step_name, step_status FROM process_step_histories " +
            "WHERE car_id IN (" + ph + ") AND process_history_id IN (" +
            "  SELECT MAX(process_history_id) FROM process_step_histories WHERE car_id IN (" + ph + ") GROUP BY car_id, step_name" +
            ")", stepArgs.toArray());

        Map<Object, Map<String, Object>> measMap = new HashMap<>();
        for (Map<String, Object> m : meas) measMap.put(m.get("car_id"), m);
        Map<Object, Map<String, String>> stepMap = new HashMap<>();
        for (Map<String, Object> s : steps) {
            stepMap.computeIfAbsent(s.get("car_id"), k -> new HashMap<>())
                .put((String) s.get("step_name"), (String) s.get("step_status"));
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> c : cars) {
            Map<String, Object> copy = new LinkedHashMap<>(c);
            copy.put("measurement", measMap.get(c.get("car_id")));
            copy.put("steps", stepMap.getOrDefault(c.get("car_id"), Map.of()));
            out.add(copy);
        }
        return out;
    }

    private static Object[] concat(Object[] a, Object[] b) {
        Object[] out = new Object[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
