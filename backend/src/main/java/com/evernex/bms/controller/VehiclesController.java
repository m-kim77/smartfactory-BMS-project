package com.evernex.bms.controller;

import com.evernex.bms.db.FactoryScope;
import com.evernex.bms.db.FactoryScope.Clause;
import com.evernex.bms.db.TimeUtil;
import com.evernex.bms.domain.Constants;
import com.evernex.bms.security.ApiException;
import com.evernex.bms.security.AuthContext;
import com.evernex.bms.service.SimulationService;
import com.evernex.bms.service.VehicleFactoryService;
import com.evernex.bms.service.VehicleFactoryService.CreateOptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehiclesController {

    private static final Map<String, String> DATE_FIELD_MAP = Map.of(
        "updated_at", "c.current_status_updated_at",
        "production_date", "c.production_date",
        "created_at", "c.created_at"
    );

    private static final List<String> FAIL_KEYS = List.of(
        "soc", "soh", "sop", "pack_voltage", "avg_temperature", "cell_temperature", "cell_voltage"
    );

    private final JdbcTemplate jdbc;
    private final VehicleFactoryService factory;
    private final SimulationService sim;

    public VehiclesController(JdbcTemplate jdbc, VehicleFactoryService factory, SimulationService sim) {
        this.jdbc = jdbc;
        this.factory = factory;
        this.sim = sim;
    }

    @GetMapping("/facets")
    public Map<String, Object> facets() {
        var allowed = AuthContext.require().allowedFactoryIds();
        Clause scope = FactoryScope.clause(allowed, "factory_id");
        String baseWhere = "WHERE 1=1" + scope.sql();
        List<String> models = jdbc.queryForList(
            "SELECT DISTINCT model_name FROM cars " + baseWhere + " AND model_name IS NOT NULL ORDER BY model_name",
            String.class, scope.params().toArray());
        List<String> countries = jdbc.queryForList(
            "SELECT DISTINCT destination_country FROM cars " + baseWhere + " AND destination_country IS NOT NULL ORDER BY destination_country",
            String.class, scope.params().toArray());
        return Map.of("models", models, "countries", countries);
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam Map<String, String> q) {
        var p = AuthContext.require();

        List<Long> requested = FactoryScope.parseIdsParam(q.get("factory_ids"));
        if (q.get("factory_id") != null) {
            try {
                long single = Long.parseLong(q.get("factory_id"));
                if (requested == null) requested = new ArrayList<>();
                else requested = new ArrayList<>(requested);
                requested.add(single);
            } catch (NumberFormatException ignored) {}
        }
        List<Long> effective = FactoryScope.intersect(requested, p.allowedFactoryIds());
        if (effective.isEmpty()) return Map.of("items", List.of());

        Clause fScope = FactoryScope.clause(effective, "c.factory_id");
        StringBuilder sql = new StringBuilder(
            "SELECT c.*, f.factory_name FROM cars c LEFT JOIN factories f ON f.factory_id=c.factory_id WHERE 1=1" + fScope.sql());
        List<Object> params = new ArrayList<>(fScope.params());

        List<String> parts = new ArrayList<>();
        List<Object> cParams = new ArrayList<>();

        if (q.get("car_id") != null && !q.get("car_id").isBlank()) {
            parts.add("c.car_id LIKE ?"); cParams.add("%" + q.get("car_id") + "%");
        }
        if (q.get("q") != null && !q.get("q").isBlank()) {
            parts.add("(c.car_id LIKE ? OR c.model_name LIKE ?)");
            cParams.add("%" + q.get("q") + "%"); cParams.add("%" + q.get("q") + "%");
        }
        addIn(parts, cParams, "c.current_status", FactoryScope.parseCsv(q.get("status")));
        addIn(parts, cParams, "c.destination_country", FactoryScope.parseCsv(q.get("country")));
        addIn(parts, cParams, "c.model_name", FactoryScope.parseCsv(q.get("model")));

        String dateCol = DATE_FIELD_MAP.getOrDefault(q.get("date_field"), DATE_FIELD_MAP.get("updated_at"));
        if (q.get("date_from") != null && !q.get("date_from").isBlank()) {
            String s = q.get("date_from").replace('T', ' ');
            parts.add(dateCol + " >= ?");
            cParams.add(s.split(":").length == 3 ? s : s + ":00");
        }
        if (q.get("date_to") != null && !q.get("date_to").isBlank()) {
            String s = q.get("date_to").replace('T', ' ');
            parts.add(dateCol + " <= ?");
            cParams.add(s.split(":").length == 3 ? s : s + ":59");
        }

        if (!parts.isEmpty()) {
            String op = "or".equals(q.get("match_mode")) ? " OR " : " AND ";
            sql.append(" AND (").append(String.join(op, parts)).append(")");
            params.addAll(cParams);
        }

        sql.append(" ORDER BY c.updated_at DESC LIMIT 500");
        return Map.of("items", jdbc.queryForList(sql.toString(), params.toArray()));
    }

    private static void addIn(List<String> parts, List<Object> params, String col, List<String> values) {
        if (values.isEmpty()) return;
        String ph = String.join(",", Collections.nCopies(values.size(), "?"));
        parts.add(col + " IN (" + ph + ")");
        params.addAll(values);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        var p = AuthContext.require();
        if (!p.isAdmin()) throw new ApiException(403, "관리자 권한이 필요합니다.");

        Object fid = body.get("factory_id");
        Long factoryId;
        try { factoryId = fid == null ? null : Long.parseLong(String.valueOf(fid)); }
        catch (NumberFormatException e) { throw new ApiException(400, "공장을 선택하세요."); }
        if (factoryId == null) throw new ApiException(400, "공장을 선택하세요.");

        Integer exists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM factories WHERE factory_id=? AND is_active=1", Integer.class, factoryId);
        if (exists == null || exists == 0) throw new ApiException(400, "유효한 공장이 아닙니다.");

        Map<String, Boolean> failMetrics = new HashMap<>();
        Object fm = body.get("fail_metrics");
        if (fm instanceof Map<?, ?> fmMap) {
            for (String k : FAIL_KEYS) {
                Object v = fmMap.get(k);
                if (Boolean.TRUE.equals(v) || "true".equals(String.valueOf(v))) failMetrics.put(k, true);
            }
        }

        String modelName = trimOrNull(body.get("model_name"));
        String country = trimOrNull(body.get("destination_country"));
        var v = factory.create(new CreateOptions(
            Constants.ARRIVAL, false, null, modelName, country, factoryId, failMetrics));
        sim.registerNewVehicle(v.carId());
        return Map.of("car_id", v.carId());
    }

    private static String trimOrNull(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    @PutMapping("/{carId}")
    public Map<String, Object> update(@PathVariable String carId, @RequestBody Map<String, Object> body) {
        var p = AuthContext.require();
        if (!p.isAdmin()) throw new ApiException(403, "관리자 권한이 필요합니다.");
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM cars WHERE car_id=?", Integer.class, carId);
        if (exists == null || exists == 0) throw new ApiException(404, "차량을 찾을 수 없습니다.");

        List<String> updates = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (body.containsKey("model_name")) {
            String v = String.valueOf(body.get("model_name")).trim();
            if (v.isEmpty()) throw new ApiException(400, "모델명은 비울 수 없습니다.");
            updates.add("model_name=?"); params.add(v);
        }
        if (body.containsKey("destination_country")) {
            String v = String.valueOf(body.get("destination_country")).trim();
            if (v.isEmpty()) throw new ApiException(400, "수출국은 비울 수 없습니다.");
            updates.add("destination_country=?"); params.add(v);
        }
        if (body.containsKey("factory_id")) {
            long fid;
            try { fid = Long.parseLong(String.valueOf(body.get("factory_id"))); }
            catch (NumberFormatException e) { throw new ApiException(400, "유효한 공장이 아닙니다."); }
            Integer fex = jdbc.queryForObject(
                "SELECT COUNT(*) FROM factories WHERE factory_id=? AND is_active=1", Integer.class, fid);
            if (fex == null || fex == 0) throw new ApiException(400, "유효한 공장이 아닙니다.");
            updates.add("factory_id=?"); params.add(fid);
        }
        if (updates.isEmpty()) return Map.of("ok", true);
        updates.add("updated_at=?"); params.add(TimeUtil.nowISO());
        params.add(carId);
        jdbc.update("UPDATE cars SET " + String.join(", ", updates) + " WHERE car_id=?", params.toArray());
        return Map.of("ok", true);
    }

    @DeleteMapping("/{carId}")
    @Transactional
    public Map<String, Object> delete(@PathVariable String carId) {
        var p = AuthContext.require();
        if (!p.isAdmin()) throw new ApiException(403, "관리자 권한이 필요합니다.");
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM cars WHERE car_id=?", Integer.class, carId);
        if (exists == null || exists == 0) throw new ApiException(404, "차량을 찾을 수 없습니다.");

        jdbc.update("DELETE FROM alert_status_histories WHERE alert_id IN (SELECT alert_id FROM alerts WHERE car_id=?)", carId);
        jdbc.update("DELETE FROM alerts WHERE car_id=?", carId);
        List<Long> bIds = jdbc.queryForList("SELECT battery_id FROM batteries WHERE car_id=?", Long.class, carId);
        for (Long bid : bIds) {
            jdbc.update("DELETE FROM battery_cell_measurements WHERE cell_id IN (SELECT cell_id FROM battery_cells WHERE battery_id=?)", bid);
            jdbc.update("DELETE FROM battery_cells WHERE battery_id=?", bid);
            jdbc.update("DELETE FROM battery_measurements WHERE battery_id=?", bid);
            jdbc.update("DELETE FROM batteries WHERE battery_id=?", bid);
        }
        jdbc.update("DELETE FROM inspection_results WHERE car_id=?", carId);
        jdbc.update("DELETE FROM process_step_histories WHERE car_id=?", carId);
        jdbc.update("DELETE FROM car_status_histories WHERE car_id=?", carId);
        jdbc.update("DELETE FROM vehicle_generation_logs WHERE car_id=?", carId);
        jdbc.update("DELETE FROM cars WHERE car_id=?", carId);
        return Map.of("ok", true);
    }

    @GetMapping("/{carId}")
    public Map<String, Object> detail(@PathVariable String carId) {
        var p = AuthContext.require();
        List<Map<String, Object>> carRows = jdbc.queryForList(
            "SELECT c.*, f.factory_name FROM cars c LEFT JOIN factories f ON f.factory_id=c.factory_id WHERE c.car_id=?", carId);
        if (carRows.isEmpty()) throw new ApiException(404, "차량을 찾을 수 없습니다.");
        Map<String, Object> car = carRows.get(0);
        Object fid = car.get("factory_id");
        if (fid == null || !p.allowedFactoryIds().contains(((Number) fid).longValue())) {
            throw new ApiException(404, "차량을 찾을 수 없습니다.");
        }

        List<Map<String, Object>> bRows = jdbc.queryForList("SELECT * FROM batteries WHERE car_id=?", carId);
        Map<String, Object> battery = bRows.isEmpty() ? null : bRows.get(0);
        Map<String, Object> measurement = null;
        List<Map<String, Object>> cells = Collections.emptyList();
        if (battery != null) {
            Long bid = ((Number) battery.get("battery_id")).longValue();
            List<Map<String, Object>> mRows = jdbc.queryForList(
                "SELECT * FROM battery_measurements WHERE battery_id=? ORDER BY inspected_at DESC LIMIT 1", bid);
            if (!mRows.isEmpty()) measurement = mRows.get(0);
            cells = jdbc.queryForList(
                "SELECT bc.cell_id, bc.cell_number, bcm.cell_temperature, bcm.cell_voltage, bcm.measured_at " +
                "FROM battery_cells bc LEFT JOIN battery_cell_measurements bcm ON bcm.cell_measurement_id = (" +
                "  SELECT MAX(cell_measurement_id) FROM battery_cell_measurements WHERE cell_id=bc.cell_id" +
                ") WHERE bc.battery_id=? ORDER BY bc.cell_number", bid);
        }
        List<Map<String, Object>> steps = jdbc.queryForList(
            "SELECT * FROM process_step_histories WHERE car_id=? ORDER BY process_history_id", carId);
        List<Map<String, Object>> statusHistory = jdbc.queryForList(
            "SELECT * FROM car_status_histories WHERE car_id=? ORDER BY car_status_history_id DESC LIMIT 30", carId);
        List<Map<String, Object>> alerts = jdbc.queryForList(
            "SELECT * FROM alerts WHERE car_id=? ORDER BY occurred_at DESC", carId);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("car", car);
        resp.put("battery", battery);
        resp.put("measurement", measurement);
        resp.put("cells", cells);
        resp.put("steps", steps);
        resp.put("statusHistory", statusHistory);
        resp.put("alerts", alerts);
        return resp;
    }

    @PostMapping("/{carId}/resolve")
    public Map<String, Object> resolve(@PathVariable String carId) {
        var p = AuthContext.require();
        List<Long> rows = jdbc.queryForList(
            "SELECT factory_id FROM cars WHERE car_id=?", Long.class, carId);
        if (rows.isEmpty() || !p.allowedFactoryIds().contains(rows.get(0))) {
            throw new ApiException(404, "차량을 찾을 수 없습니다.");
        }
        boolean ok = sim.resolveAlertAndReinspect(carId, p.uid());
        if (!ok) throw new ApiException(404, "차량을 찾을 수 없습니다.");
        return Map.of("ok", true);
    }
}
