package com.jarscan.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarscan.model.PolicySeverity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PolicyRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PolicyRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<PolicyEntity> findAll() {
        return jdbcTemplate.query("SELECT * FROM policies ORDER BY name ASC", rowMapper());
    }

    public List<PolicyEntity> findEnabled() {
        return jdbcTemplate.query("SELECT * FROM policies WHERE enabled = 1 ORDER BY name ASC", rowMapper());
    }

    public Optional<PolicyEntity> findById(String id) {
        return jdbcTemplate.query("SELECT * FROM policies WHERE id = :id",
                        new MapSqlParameterSource("id", id),
                        rowMapper())
                .stream()
                .findFirst();
    }

    public void upsert(PolicyEntity entity) {
        MapSqlParameterSource params = params(entity);
        jdbcTemplate.update("""
                INSERT INTO policies (
                    id, name, description, rule_type, severity, enabled, config_json, created_at, updated_at
                ) VALUES (
                    :id, :name, :description, :rule_type, :severity, :enabled, :config_json, :created_at, :updated_at
                )
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    description = excluded.description,
                    rule_type = excluded.rule_type,
                    severity = excluded.severity,
                    enabled = excluded.enabled,
                    config_json = excluded.config_json,
                    updated_at = excluded.updated_at
                """, params);
    }

    public Optional<PolicyEntity> update(PolicyEntity entity) {
        upsert(entity);
        return findById(entity.id());
    }

    public boolean deleteById(String id) {
        return jdbcTemplate.update("DELETE FROM policies WHERE id = :id",
                new MapSqlParameterSource("id", id)) > 0;
    }

    private MapSqlParameterSource params(PolicyEntity entity) {
        return new MapSqlParameterSource()
                .addValue("id", entity.id())
                .addValue("name", entity.name())
                .addValue("description", entity.description())
                .addValue("rule_type", entity.ruleType())
                .addValue("severity", entity.severity().name())
                .addValue("enabled", entity.enabled() ? 1 : 0)
                .addValue("config_json", serializeConfig(entity.config()))
                .addValue("created_at", entity.createdAt().toString())
                .addValue("updated_at", entity.updatedAt().toString());
    }

    private RowMapper<PolicyEntity> rowMapper() {
        return (rs, rowNum) -> new PolicyEntity(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("rule_type"),
                PolicySeverity.valueOf(rs.getString("severity")),
                rs.getInt("enabled") != 0,
                deserializeConfig(rs.getString("config_json")),
                toInstant(rs, "created_at"),
                toInstant(rs, "updated_at")
        );
    }

    private String serializeConfig(Map<String, Object> config) {
        try {
            return objectMapper.writeValueAsString(config == null ? Map.of() : config);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize policy config", ex);
        }
    }

    private Map<String, Object> deserializeConfig(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to deserialize policy config", ex);
        }
    }

    private Instant toInstant(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}
