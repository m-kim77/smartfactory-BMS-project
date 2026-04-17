-- Seed data (MySQL syntax). Local SQLite seeding is performed programmatically by backend/src/db/init.js
-- This file is a reference for production migration.

INSERT INTO users (email, password_hash, role, name) VALUES
('admin@hyundai-autoever.com', '$2a$10$REPLACE_WITH_BCRYPT_HASH', 'admin', '관리자'),
('operator@hyundai-autoever.com', '$2a$10$REPLACE_WITH_BCRYPT_HASH', 'operator', '운영자');

INSERT INTO factories (factory_name, region, country, is_active) VALUES
('울산공장', '울산', '대한민국', TRUE),
('아산공장', '충남', '대한민국', TRUE),
('광명공장', '경기', '대한민국', TRUE),
('화성공장', '경기', '대한민국', TRUE),
('광주공장', '광주', '대한민국', TRUE);

INSERT INTO countries (country_name, country_code, is_allowed) VALUES
('대한민국','KR',TRUE),('미국','US',TRUE),('독일','DE',TRUE),('영국','GB',TRUE),
('프랑스','FR',TRUE),('일본','JP',TRUE),('중국','CN',TRUE),('인도','IN',TRUE),
('호주','AU',TRUE),('캐나다','CA',TRUE);

INSERT INTO admin_settings (setting_key, setting_value, setting_type, description) VALUES
('vehicle_generation_interval_ms','10000','INTEGER','차량 자동 생성 간격(ms)'),
('inspection_soc_duration_ms','10000','INTEGER','SOC 검사 시간(ms)'),
('inspection_soh_duration_ms','10000','INTEGER','SOH 검사 시간(ms)'),
('inspection_sop_duration_ms','10000','INTEGER','SOP 검사 시간(ms)'),
('inspection_pack_voltage_duration_ms','10000','INTEGER','팩 전압 검사 시간(ms)'),
('inspection_cell_duration_ms','10000','INTEGER','셀 검사 시간(ms)'),
('qa_maintenance_duration_ms','10000','INTEGER','정비(QA) 시간(ms)'),
('re_inspection_duration_ms','10000','INTEGER','재검사 단계 시간(ms)'),
('shipment_waiting_duration_ms','10000','INTEGER','출고 대기 시간(ms)'),
('shipment_complete_delay_ms','10000','INTEGER','출고 완료 지연(ms)'),
('prob_soc_normal','99','INTEGER','SOC 정상 확률(%)'),
('prob_soh_normal','99','INTEGER','SOH 정상 확률(%)'),
('prob_sop_normal','99','INTEGER','SOP 정상 확률(%)'),
('prob_pack_voltage_normal','99','INTEGER','팩 전압 정상 확률(%)'),
('prob_cell_temperature_normal','99','INTEGER','셀 온도 정상 확률(%)'),
('prob_cell_voltage_normal','99','INTEGER','셀 전압 정상 확률(%)');

-- Sample cars across all states (illustrative only — real seed runs in JS)
INSERT INTO cars (car_id, model_name, production_date, destination_country, factory_id, current_status, current_status_updated_at, created_at, updated_at) VALUES
('VH-20260414-0001','IONIQ 5','2026-04-14','미국',1,'ARRIVAL',NOW(),NOW(),NOW()),
('VH-20260414-0002','EV6','2026-04-14','독일',4,'BATTERY_INSPECTION',NOW(),NOW(),NOW()),
('VH-20260414-0003','IONIQ 6','2026-04-14','일본',2,'CELL_INSPECTION',NOW(),NOW(),NOW()),
('VH-20260414-0004','Kona Electric','2026-04-14','영국',1,'ANOMALY_DETECTED',NOW(),NOW(),NOW()),
('VH-20260414-0005','EV9','2026-04-14','호주',5,'QA_MAINTENANCE',NOW(),NOW(),NOW()),
('VH-20260414-0006','EV3','2026-04-14','캐나다',4,'RE_INSPECTION_WAITING',NOW(),NOW(),NOW()),
('VH-20260414-0007','Niro EV','2026-04-14','프랑스',5,'RE_INSPECTION',NOW(),NOW(),NOW()),
('VH-20260414-0008','IONIQ 5','2026-04-14','미국',1,'BATTERY_QC_COMPLETE',NOW(),NOW(),NOW()),
('VH-20260414-0009','EV6','2026-04-14','중국',4,'SHIPMENT_WAITING',NOW(),NOW(),NOW()),
('VH-20260414-0010','IONIQ 6','2026-04-14','인도',2,'SHIPMENT_COMPLETE',NOW(),NOW(),NOW());
