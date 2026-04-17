package com.evernex.bms.service;

import com.evernex.bms.db.TimeUtil;
import com.evernex.bms.domain.Constants;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/** Port of services/vehicleFactory.js. */
@Service
public class VehicleFactoryService {

    private final JdbcTemplate jdbc;
    private final RandomMetricService random;
    private final AtomicInteger seq = new AtomicInteger(0);

    public VehicleFactoryService(JdbcTemplate jdbc, RandomMetricService random) {
        this.jdbc = jdbc;
        this.random = random;
    }

    public record CreateOptions(
        String initialStatus,
        boolean forceAbnormal,
        Integer seqOverride,
        String modelName,
        String destinationCountry,
        Long factoryIdOverride,
        Map<String, Boolean> failMetrics
    ) {}

    public record CreatedVehicle(String carId, long batteryId) {}

    private static String carIdForToday(int n) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("VH-%s-%04d", date, n);
    }

    private Map<String, Object> pickFactory() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT factory_id, factory_name FROM factories WHERE is_active=1");
        if (rows.isEmpty()) return null;
        return rows.get(ThreadLocalRandom.current().nextInt(rows.size()));
    }

    private String pickCountry() {
        List<String> rows = jdbc.queryForList(
            "SELECT country_name FROM countries WHERE is_allowed=1", String.class);
        if (rows.isEmpty()) return "에버랜드";
        return rows.get(ThreadLocalRandom.current().nextInt(rows.size()));
    }

    private String pickModel(String factoryName) {
        List<String> list = factoryName != null && Constants.FACTORY_MODELS.containsKey(factoryName)
            ? Constants.FACTORY_MODELS.get(factoryName)
            : Constants.MODELS;
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    public CreatedVehicle create(CreateOptions opts) {
        String now = TimeUtil.nowISO();
        int seqN = opts.seqOverride != null ? opts.seqOverride : seq.incrementAndGet();
        String carId = carIdForToday(seqN);
        String country = opts.destinationCountry != null && !opts.destinationCountry.isBlank()
            ? opts.destinationCountry : pickCountry();

        Map<String, Object> factory;
        if (opts.factoryIdOverride != null) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT factory_id, factory_name FROM factories WHERE factory_id=? AND is_active=1",
                opts.factoryIdOverride);
            factory = rows.isEmpty() ? null : rows.get(0);
        } else {
            factory = pickFactory();
        }
        Long factoryId = factory == null ? null : ((Number) factory.get("factory_id")).longValue();
        String factoryName = factory == null ? null : String.valueOf(factory.get("factory_name"));
        String model = opts.modelName != null && !opts.modelName.isBlank()
            ? opts.modelName : pickModel(factoryName);
        String today = now.substring(0, 10);
        String initialStatus = opts.initialStatus != null ? opts.initialStatus : Constants.ARRIVAL;

        jdbc.update(
            "INSERT INTO cars (car_id,model_name,production_date,destination_country,factory_id,current_status,current_status_updated_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
            carId, model, today, country, factoryId, initialStatus, now, now, now);

        jdbc.update(
            "INSERT INTO car_status_histories (car_id,status,changed_at,reason) VALUES (?,?,?,?)",
            carId, initialStatus, now, "최초 입고");

        KeyHolder bkey = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO batteries (car_id,battery_serial_number,manufacture_date,installed_at) VALUES (?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, carId);
            ps.setString(2, "BSN-" + carId);
            ps.setString(3, today);
            ps.setString(4, now);
            return ps;
        }, bkey);
        long batteryId = bkey.getKey().longValue();

        long[] cellIds = new long[100];
        for (int i = 1; i <= 100; i++) {
            final int ci = i;
            KeyHolder ck = new GeneratedKeyHolder();
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO battery_cells (battery_id,cell_number) VALUES (?,?)",
                    Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, batteryId);
                ps.setInt(2, ci);
                return ps;
            }, ck);
            cellIds[i - 1] = ck.getKey().longValue();
        }

        Map<String, Boolean> fm = opts.failMetrics == null ? new HashMap<>() : opts.failMetrics;
        double soc = random.genMetric("soc", Boolean.TRUE.equals(fm.get("soc")) || opts.forceAbnormal);
        double soh = random.genMetric("soh", Boolean.TRUE.equals(fm.get("soh")));
        double sop = random.genMetric("sop", Boolean.TRUE.equals(fm.get("sop")));
        double packV = random.genMetric("pack_voltage", Boolean.TRUE.equals(fm.get("pack_voltage")));
        double avgT = random.genMetric("cell_temperature", Boolean.TRUE.equals(fm.get("avg_temperature")));
        double tempDev = Math.round(ThreadLocalRandom.current().nextDouble() * 2 * 100.0) / 100.0;

        jdbc.update(
            "INSERT INTO battery_measurements (battery_id,inspected_at,soc,soh,sop,avg_voltage,avg_temperature,temperature_deviation) VALUES (?,?,?,?,?,?,?,?)",
            batteryId, now, soc, soh, sop, packV, avgT, tempDev);

        boolean cellFailAny = Boolean.TRUE.equals(fm.get("cell_temperature")) || Boolean.TRUE.equals(fm.get("cell_voltage"));
        int abnormalIdx = (cellFailAny || opts.forceAbnormal) ? ThreadLocalRandom.current().nextInt(100) : -1;
        for (int i = 0; i < 100; i++) {
            boolean bad = i == abnormalIdx;
            boolean failT = bad && (Boolean.TRUE.equals(fm.get("cell_temperature"))
                || (opts.forceAbnormal && ThreadLocalRandom.current().nextDouble() < 0.5));
            boolean failV = bad && (Boolean.TRUE.equals(fm.get("cell_voltage")) || opts.forceAbnormal);
            double t = random.genMetric("cell_temperature", failT);
            double v = random.genMetric("cell_voltage", failV);
            jdbc.update(
                "INSERT INTO battery_cell_measurements (cell_id,measured_at,cell_temperature,cell_voltage) VALUES (?,?,?,?)",
                cellIds[i], now, t, v);
        }

        boolean manual = opts.modelName != null || opts.destinationCountry != null
            || opts.factoryIdOverride != null || !fm.isEmpty();
        String method = manual ? "MANUAL" : "AUTO";
        jdbc.update("INSERT INTO vehicle_generation_logs (car_id,generated_at,generation_method) VALUES (?,?,?)",
            carId, now, method);

        return new CreatedVehicle(carId, batteryId);
    }

    public void initSeqFromDb() {
        String prefix = "VH-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        List<String> rows = jdbc.queryForList(
            "SELECT car_id FROM cars WHERE car_id LIKE ? ORDER BY car_id DESC LIMIT 1",
            String.class, prefix + "%");
        if (!rows.isEmpty()) {
            String tail = rows.get(0).substring(rows.get(0).lastIndexOf('-') + 1);
            try { seq.set(Integer.parseInt(tail)); } catch (Exception ignored) {}
        }
    }
}
