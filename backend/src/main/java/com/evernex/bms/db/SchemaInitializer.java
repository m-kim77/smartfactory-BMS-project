package com.evernex.bms.db;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Mirrors db/schema.js — creates tables + indexes if they don't exist. */
@Component
public class SchemaInitializer {

    private final JdbcTemplate jdbc;

    public SchemaInitializer(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostConstruct
    public void init() {
        jdbc.execute("PRAGMA journal_mode = WAL");
        jdbc.execute("PRAGMA foreign_keys = ON");
        for (String stmt : SCHEMA_STATEMENTS) jdbc.execute(stmt);
    }

    private static final String[] SCHEMA_STATEMENTS = new String[] {
        """
        CREATE TABLE IF NOT EXISTS users (
          user_id INTEGER PRIMARY KEY AUTOINCREMENT,
          email TEXT NOT NULL UNIQUE,
          password_hash TEXT NOT NULL,
          role TEXT NOT NULL,
          name TEXT NOT NULL,
          created_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS factories (
          factory_id INTEGER PRIMARY KEY AUTOINCREMENT,
          factory_name TEXT NOT NULL,
          region TEXT,
          country TEXT,
          is_active INTEGER DEFAULT 1,
          created_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS countries (
          country_id INTEGER PRIMARY KEY AUTOINCREMENT,
          country_name TEXT NOT NULL,
          country_code TEXT,
          is_allowed INTEGER DEFAULT 1,
          created_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS cars (
          car_id TEXT PRIMARY KEY,
          model_name TEXT NOT NULL,
          production_date TEXT NOT NULL,
          destination_country TEXT NOT NULL,
          factory_id INTEGER,
          current_status TEXT NOT NULL,
          current_status_updated_at TEXT NOT NULL,
          created_at TEXT NOT NULL DEFAULT (datetime('now')),
          updated_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_cars_status ON cars(current_status)",
        """
        CREATE TABLE IF NOT EXISTS batteries (
          battery_id INTEGER PRIMARY KEY AUTOINCREMENT,
          car_id TEXT NOT NULL,
          battery_serial_number TEXT,
          manufacture_date TEXT NOT NULL,
          installed_at TEXT,
          created_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS battery_measurements (
          measurement_id INTEGER PRIMARY KEY AUTOINCREMENT,
          battery_id INTEGER NOT NULL,
          inspected_at TEXT NOT NULL,
          soc REAL NOT NULL,
          soh REAL NOT NULL,
          sop REAL NOT NULL,
          avg_voltage REAL NOT NULL,
          avg_temperature REAL NOT NULL,
          temperature_deviation REAL NOT NULL,
          created_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS battery_cells (
          cell_id INTEGER PRIMARY KEY AUTOINCREMENT,
          battery_id INTEGER NOT NULL,
          cell_number INTEGER NOT NULL,
          created_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS battery_cell_measurements (
          cell_measurement_id INTEGER PRIMARY KEY AUTOINCREMENT,
          cell_id INTEGER NOT NULL,
          measured_at TEXT NOT NULL,
          cell_temperature REAL NOT NULL,
          cell_voltage REAL NOT NULL,
          created_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS car_status_histories (
          car_status_history_id INTEGER PRIMARY KEY AUTOINCREMENT,
          car_id TEXT NOT NULL,
          status TEXT NOT NULL,
          changed_at TEXT NOT NULL,
          changed_by_user_id INTEGER,
          reason TEXT
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS alerts (
          alert_id INTEGER PRIMARY KEY AUTOINCREMENT,
          car_id TEXT NOT NULL,
          alert_type TEXT NOT NULL,
          alert_message TEXT NOT NULL,
          severity TEXT NOT NULL,
          occurred_at TEXT NOT NULL,
          current_status TEXT NOT NULL DEFAULT 'OPEN',
          resolved_at TEXT,
          created_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_alerts_status ON alerts(current_status)",
        """
        CREATE TABLE IF NOT EXISTS alert_status_histories (
          history_id INTEGER PRIMARY KEY AUTOINCREMENT,
          alert_id INTEGER NOT NULL,
          previous_status TEXT,
          new_status TEXT NOT NULL,
          changed_by_user_id INTEGER,
          changed_at TEXT NOT NULL,
          note TEXT
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS process_step_histories (
          process_history_id INTEGER PRIMARY KEY AUTOINCREMENT,
          car_id TEXT NOT NULL,
          step_name TEXT NOT NULL,
          step_order INTEGER NOT NULL,
          step_status TEXT NOT NULL,
          started_at TEXT,
          ended_at TEXT,
          note TEXT,
          created_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS inspection_results (
          result_id INTEGER PRIMARY KEY AUTOINCREMENT,
          car_id TEXT NOT NULL,
          status TEXT NOT NULL,
          reason TEXT NOT NULL,
          performance_status TEXT,
          safety_status TEXT,
          evaluated_at TEXT NOT NULL,
          created_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS admin_settings (
          setting_key TEXT PRIMARY KEY,
          setting_value TEXT NOT NULL,
          setting_type TEXT NOT NULL,
          description TEXT,
          updated_at TEXT,
          updated_by_user_id INTEGER
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS vehicle_generation_logs (
          log_id INTEGER PRIMARY KEY AUTOINCREMENT,
          car_id TEXT,
          generated_at TEXT NOT NULL,
          generation_method TEXT NOT NULL DEFAULT 'AUTO'
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS llm_chat_logs (
          log_id INTEGER PRIMARY KEY AUTOINCREMENT,
          user_id INTEGER,
          session_id TEXT,
          user_message TEXT,
          assistant_message TEXT,
          context_data TEXT,
          created_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS user_factories (
          user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
          factory_id INTEGER NOT NULL REFERENCES factories(factory_id) ON DELETE CASCADE,
          created_at TEXT NOT NULL DEFAULT (datetime('now')),
          PRIMARY KEY (user_id, factory_id)
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_user_factories_user ON user_factories(user_id)",
        """
        CREATE TABLE IF NOT EXISTS reports (
          report_id INTEGER PRIMARY KEY AUTOINCREMENT,
          user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
          title TEXT NOT NULL,
          summary TEXT,
          content TEXT NOT NULL,
          source_session_id TEXT,
          llm_mode TEXT,
          llm_model TEXT,
          message_count INTEGER DEFAULT 0,
          car_ids TEXT,
          created_at TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_reports_user ON reports(user_id, created_at DESC)"
    };
}
