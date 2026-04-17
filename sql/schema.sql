-- BMS Smart Factory Quality Inspection - MySQL DDL
-- For production migration. The local prototype uses an equivalent SQLite schema.

CREATE TABLE users (
  user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(30) NOT NULL,
  name VARCHAR(100) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE factories (
  factory_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  factory_name VARCHAR(100) NOT NULL,
  region VARCHAR(100),
  country VARCHAR(100),
  is_active BOOLEAN DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE countries (
  country_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  country_name VARCHAR(100) NOT NULL,
  country_code VARCHAR(10),
  is_allowed BOOLEAN DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE cars (
  car_id VARCHAR(30) PRIMARY KEY,
  model_name VARCHAR(100) NOT NULL,
  production_date DATE NOT NULL,
  destination_country VARCHAR(100) NOT NULL,
  factory_id BIGINT,
  current_status VARCHAR(30) NOT NULL,
  current_status_updated_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (factory_id) REFERENCES factories(factory_id)
);
CREATE INDEX idx_cars_status ON cars(current_status);

CREATE TABLE batteries (
  battery_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  car_id VARCHAR(30) NOT NULL,
  battery_serial_number VARCHAR(100),
  manufacture_date DATE NOT NULL,
  installed_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (car_id) REFERENCES cars(car_id)
);

CREATE TABLE battery_measurements (
  measurement_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  battery_id BIGINT NOT NULL,
  inspected_at DATETIME NOT NULL,
  soc DECIMAL(5,2) NOT NULL,
  soh DECIMAL(5,2) NOT NULL,
  sop DECIMAL(5,2) NOT NULL,
  avg_voltage DECIMAL(8,2) NOT NULL,
  avg_temperature DECIMAL(5,2) NOT NULL,
  temperature_deviation DECIMAL(5,2) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (battery_id) REFERENCES batteries(battery_id)
);

CREATE TABLE battery_cells (
  cell_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  battery_id BIGINT NOT NULL,
  cell_number INT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (battery_id) REFERENCES batteries(battery_id)
);

CREATE TABLE battery_cell_measurements (
  cell_measurement_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  cell_id BIGINT NOT NULL,
  measured_at DATETIME NOT NULL,
  cell_temperature DECIMAL(5,2) NOT NULL,
  cell_voltage DECIMAL(8,3) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (cell_id) REFERENCES battery_cells(cell_id)
);

CREATE TABLE car_status_histories (
  car_status_history_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  car_id VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL,
  changed_at DATETIME NOT NULL,
  changed_by_user_id BIGINT NULL,
  reason VARCHAR(255),
  FOREIGN KEY (car_id) REFERENCES cars(car_id),
  FOREIGN KEY (changed_by_user_id) REFERENCES users(user_id)
);

CREATE TABLE alerts (
  alert_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  car_id VARCHAR(30) NOT NULL,
  alert_type VARCHAR(100) NOT NULL,
  alert_message VARCHAR(255) NOT NULL,
  severity VARCHAR(20) NOT NULL,
  occurred_at DATETIME NOT NULL,
  current_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  resolved_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (car_id) REFERENCES cars(car_id)
);
CREATE INDEX idx_alerts_status ON alerts(current_status);

CREATE TABLE alert_status_histories (
  history_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  alert_id BIGINT NOT NULL,
  previous_status VARCHAR(20),
  new_status VARCHAR(20) NOT NULL,
  changed_by_user_id BIGINT NULL,
  changed_at DATETIME NOT NULL,
  note VARCHAR(255),
  FOREIGN KEY (alert_id) REFERENCES alerts(alert_id),
  FOREIGN KEY (changed_by_user_id) REFERENCES users(user_id)
);

CREATE TABLE process_step_histories (
  process_history_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  car_id VARCHAR(30) NOT NULL,
  step_name VARCHAR(50) NOT NULL,
  step_order INT NOT NULL,
  step_status VARCHAR(20) NOT NULL,
  started_at DATETIME,
  ended_at DATETIME,
  note VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (car_id) REFERENCES cars(car_id)
);

CREATE TABLE inspection_results (
  result_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  car_id VARCHAR(30) NOT NULL,
  status VARCHAR(10) NOT NULL,
  reason VARCHAR(100) NOT NULL,
  performance_status VARCHAR(20),
  safety_status VARCHAR(20),
  evaluated_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (car_id) REFERENCES cars(car_id)
);

CREATE TABLE admin_settings (
  setting_key VARCHAR(100) PRIMARY KEY,
  setting_value VARCHAR(500) NOT NULL,
  setting_type VARCHAR(20) NOT NULL,
  description VARCHAR(255),
  updated_at DATETIME,
  updated_by_user_id BIGINT NULL,
  FOREIGN KEY (updated_by_user_id) REFERENCES users(user_id)
);

CREATE TABLE vehicle_generation_logs (
  log_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  car_id VARCHAR(30),
  generated_at DATETIME NOT NULL,
  generation_method VARCHAR(20) NOT NULL DEFAULT 'AUTO',
  FOREIGN KEY (car_id) REFERENCES cars(car_id)
);

CREATE TABLE llm_chat_logs (
  log_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT,
  session_id VARCHAR(100),
  user_message TEXT,
  assistant_message TEXT,
  context_data TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(user_id)
);
