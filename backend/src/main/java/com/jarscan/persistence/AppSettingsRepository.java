package com.jarscan.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class AppSettingsRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AppSettingsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AppSettingRecord> findByKey(String key) {
        return jdbcTemplate.query("""
                        SELECT key, value, encrypted, created_at, updated_at
                        FROM app_settings
                        WHERE key = :key
                        """,
                new MapSqlParameterSource("key", key),
                (resultSet, rowNum) -> new AppSettingRecord(
                        resultSet.getString("key"),
                        resultSet.getString("value"),
                        resultSet.getInt("encrypted") == 1,
                        Instant.parse(resultSet.getString("created_at")),
                        Instant.parse(resultSet.getString("updated_at"))
                )).stream().findFirst();
    }

    public List<AppSettingRecord> findByPrefix(String prefix) {
        return jdbcTemplate.query("""
                        SELECT key, value, encrypted, created_at, updated_at
                        FROM app_settings
                        WHERE key LIKE :prefix
                        ORDER BY key ASC
                        """,
                new MapSqlParameterSource("prefix", prefix + "%"),
                (resultSet, rowNum) -> new AppSettingRecord(
                        resultSet.getString("key"),
                        resultSet.getString("value"),
                        resultSet.getInt("encrypted") == 1,
                        Instant.parse(resultSet.getString("created_at")),
                        Instant.parse(resultSet.getString("updated_at"))
                ));
    }

    public void upsert(String key, String value, boolean encrypted) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO app_settings (key, value, encrypted, created_at, updated_at)
                VALUES (:key, :value, :encrypted, :created_at, :updated_at)
                ON CONFLICT(key) DO UPDATE SET
                    value = excluded.value,
                    encrypted = excluded.encrypted,
                    updated_at = excluded.updated_at
                """, new MapSqlParameterSource()
                .addValue("key", key)
                .addValue("value", value)
                .addValue("encrypted", encrypted ? 1 : 0)
                .addValue("created_at", now.toString())
                .addValue("updated_at", now.toString()));
    }

    public boolean deleteByKey(String key) {
        int deleted = jdbcTemplate.update("DELETE FROM app_settings WHERE key = :key", new MapSqlParameterSource("key", key));
        return deleted > 0;
    }
}
