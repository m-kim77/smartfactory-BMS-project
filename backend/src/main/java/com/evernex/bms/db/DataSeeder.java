package com.evernex.bms.db;

import com.evernex.bms.domain.Constants;
import com.evernex.bms.service.VehicleFactoryService;
import com.evernex.bms.service.VehicleFactoryService.CreateOptions;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Port of db/init.js — seedIfEmpty. Runs after ApplicationReadyEvent so SchemaInitializer ran first. */
@Component
@Order(1)
public class DataSeeder {

    private static final Object[][] DEFAULT_SETTINGS = {
        {"vehicle_generation_interval_ms", "10000", "INTEGER", "차량 자동 생성 간격"},
        {"inspection_soc_duration_ms", "10000", "INTEGER", "SOC 검사 시간"},
        {"inspection_soh_duration_ms", "10000", "INTEGER", "SOH 검사 시간"},
        {"inspection_sop_duration_ms", "10000", "INTEGER", "SOP 검사 시간"},
        {"inspection_pack_voltage_duration_ms", "10000", "INTEGER", "팩 전압 검사 시간"},
        {"inspection_cell_duration_ms", "10000", "INTEGER", "셀 검사 시간"},
        {"qa_maintenance_duration_ms", "10000", "INTEGER", "정비(QA) 시간"},
        {"re_inspection_duration_ms", "10000", "INTEGER", "재검사 단계 시간"},
        {"shipment_waiting_duration_ms", "10000", "INTEGER", "출고 대기 시간"},
        {"shipment_complete_delay_ms", "10000", "INTEGER", "출고 완료 지연"},
        {"prob_soc_normal", "99", "INTEGER", "SOC 정상 확률(%)"},
        {"prob_soh_normal", "99", "INTEGER", "SOH 정상 확률(%)"},
        {"prob_sop_normal", "99", "INTEGER", "SOP 정상 확률(%)"},
        {"prob_pack_voltage_normal", "99", "INTEGER", "팩 전압 정상 확률(%)"},
        {"prob_cell_temperature_normal", "99", "INTEGER", "셀 온도 정상 확률(%)"},
        {"prob_cell_voltage_normal", "99", "INTEGER", "셀 전압 정상 확률(%)"},
        {"llm_provider", "lm_studio", "STRING", "LLM 제공자 (lm_studio: 로컬 / openai / gemini)"},
        {"llm_base_url", "", "STRING", "LM Studio 서버 주소 (비워두면 기본값 사용)"},
        {"llm_openai_api_key", "", "STRING", "OpenAI API 키 (sk-...)"},
        {"llm_gemini_api_key", "", "STRING", "Google Gemini API 키"},
        {"llm_model", "local-model", "STRING", "LLM 모델 (제공자에 따라 선택 가능 목록 달라짐)"},
        {"llm_mode", "rag_lite", "STRING", "LLM 응답 방식 (rag_lite: 키워드 기반 / text_to_sql: SQL 생성)"},
        {"llm_max_tokens", "3000", "INTEGER", "LLM 응답 최대 토큰 수 (생성될 답변 길이 한도)"},
        {"llm_context_alerts", "8", "INTEGER", "RAG-lite 컨텍스트에 포함할 최근 경보 건수"},
        {"llm_context_cars", "20", "INTEGER", "RAG-lite 컨텍스트에 포함할 키워드 매칭 차량 건수"},
        {"llm_alert_msg_max", "80", "INTEGER", "경보 메시지 자르기 글자수 (0이면 자르지 않음)"},
        {"shift_duration_min", "30", "INTEGER", "교대조 주기 (분)"},
        {"repair_duration_multiplier", "2", "FLOAT", "수리 시간 배수 (검사 시간 × 배수)"}
    };

    private static final Object[][] FACTORIES = {
        {"청림공장", "청림", "에버랜드", "노바"},
        {"은하공장", "은하", "에버랜드", "노바"},
        {"백운공장", "백운", "에버랜드", "노바"},
        {"단풍공장", "단풍", "에버랜드", "벡터"},
        {"태양공장", "태양", "에버랜드", "벡터"},
        {"한빛공장", "한빛", "에버랜드", "벡터"}
    };

    private static final Object[][] COUNTRIES = {
        {"에버랜드", "KR"}, {"미국", "US"}, {"독일", "DE"}, {"영국", "GB"},
        {"프랑스", "FR"}, {"일본", "JP"}, {"중국", "CN"}, {"인도", "IN"},
        {"호주", "AU"}, {"캐나다", "CA"}
    };

    private final JdbcTemplate jdbc;
    private final VehicleFactoryService factory;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(10);

    public DataSeeder(JdbcTemplate jdbc, VehicleFactoryService factory) {
        this.jdbc = jdbc;
        this.factory = factory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() {
        // brand 컬럼 마이그레이션 (이미 있으면 예외 무시)
        try { jdbc.execute("ALTER TABLE factories ADD COLUMN brand TEXT"); } catch (Exception ignored) {}

        Integer userCount = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        if (userCount != null && userCount == 0) {
            String hashAdmin = bcrypt.encode("admin1234");
            String hashOp = bcrypt.encode("operator1234");
            jdbc.update("INSERT INTO users (email,password_hash,role,name) VALUES (?,?,?,?)",
                "admin@evernex.com", hashAdmin, "admin", "관리자");
            jdbc.update("INSERT INTO users (email,password_hash,role,name) VALUES (?,?,?,?)",
                "operator@evernex.com", hashOp, "operator", "운영자");
        }

        Integer factoryCount = jdbc.queryForObject("SELECT COUNT(*) FROM factories", Integer.class);
        if (factoryCount != null && factoryCount == 0) {
            for (Object[] f : FACTORIES) {
                jdbc.update("INSERT INTO factories (factory_name,region,country,brand,is_active) VALUES (?,?,?,?,1)",
                    f[0], f[1], f[2], f[3]);
            }
        } else {
            Map<String, String> brandByName = new java.util.HashMap<>();
            for (Object[] f : FACTORIES) brandByName.put((String) f[0], (String) f[3]);
            List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT factory_id, factory_name, brand FROM factories");
            for (Map<String, Object> r : existing) {
                String name = (String) r.get("factory_name");
                String brand = (String) r.get("brand");
                String wantBrand = brandByName.get(name);
                if (wantBrand != null && !wantBrand.equals(brand)) {
                    jdbc.update("UPDATE factories SET brand=? WHERE factory_id=?",
                        wantBrand, r.get("factory_id"));
                }
            }
            Integer bKey = jdbc.queryForObject(
                "SELECT COUNT(*) FROM factories WHERE factory_name='백운공장'", Integer.class);
            if (bKey != null && bKey == 0) {
                jdbc.update("INSERT INTO factories (factory_name,region,country,brand,is_active) VALUES (?,?,?,?,1)",
                    "백운공장", "백운", "에버랜드", "노바");
            }
        }

        Integer countryCount = jdbc.queryForObject("SELECT COUNT(*) FROM countries", Integer.class);
        if (countryCount != null && countryCount == 0) {
            for (Object[] c : COUNTRIES) {
                jdbc.update("INSERT INTO countries (country_name,country_code,is_allowed) VALUES (?,?,1)", c[0], c[1]);
            }
        }

        String now = TimeUtil.nowISO();
        for (Object[] s : DEFAULT_SETTINGS) {
            jdbc.update(
                "INSERT OR IGNORE INTO admin_settings (setting_key,setting_value,setting_type,description,updated_at) VALUES (?,?,?,?,?)",
                s[0], s[1], s[2], s[3], now);
        }

        List<Long> seedOp = jdbc.queryForList(
            "SELECT user_id FROM users WHERE email='operator@evernex.com'", Long.class);
        if (!seedOp.isEmpty()) {
            Long opId = seedOp.get(0);
            Integer mappings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_factories WHERE user_id=?", Integer.class, opId);
            if (mappings != null && mappings == 0) {
                List<Long> allFids = jdbc.queryForList(
                    "SELECT factory_id FROM factories WHERE is_active=1", Long.class);
                for (Long fid : allFids) {
                    jdbc.update("INSERT OR IGNORE INTO user_factories (user_id, factory_id) VALUES (?, ?)", opId, fid);
                }
            }
        }

        Integer carCount = jdbc.queryForObject("SELECT COUNT(*) FROM cars", Integer.class);
        if (carCount != null && carCount == 0) {
            String[] sampleStatuses = {
                Constants.ARRIVAL, Constants.BATTERY_INSPECTION, Constants.CELL_INSPECTION,
                Constants.ANOMALY_DETECTED, Constants.QA_MAINTENANCE, Constants.RE_INSPECTION_WAITING,
                Constants.RE_INSPECTION, Constants.BATTERY_QC_COMPLETE, Constants.SHIPMENT_WAITING,
                Constants.SHIPMENT_COMPLETE,
                Constants.BATTERY_INSPECTION, Constants.CELL_INSPECTION,
                Constants.ANOMALY_DETECTED, Constants.SHIPMENT_WAITING, Constants.ARRIVAL
            };
            for (int i = 0; i < sampleStatuses.length; i++) {
                String st = sampleStatuses[i];
                boolean forceAbnormal = st.equals(Constants.ANOMALY_DETECTED)
                    || st.equals(Constants.QA_MAINTENANCE)
                    || st.equals(Constants.RE_INSPECTION_WAITING)
                    || st.equals(Constants.RE_INSPECTION);
                factory.create(new CreateOptions(st, forceAbnormal, i + 1, null, null, null, null));
            }
        }
    }
}
