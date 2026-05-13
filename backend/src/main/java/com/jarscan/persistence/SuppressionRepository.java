package com.jarscan.persistence;

import com.jarscan.model.SuppressionType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class SuppressionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SuppressionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SuppressionEntity> findAll() {
        return jdbcTemplate.query("SELECT * FROM suppressions ORDER BY updated_at DESC", rowMapper());
    }

    public List<SuppressionEntity> findActive(Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource("now", now.toString());
        return jdbcTemplate.query("""
                SELECT * FROM suppressions
                WHERE active = 1
                  AND (expires_at IS NULL OR expires_at > :now)
                ORDER BY updated_at DESC
                """, params, rowMapper());
    }

    public Optional<SuppressionEntity> findById(String id) {
        return jdbcTemplate.query("SELECT * FROM suppressions WHERE id = :id",
                        new MapSqlParameterSource("id", id),
                        rowMapper())
                .stream()
                .findFirst();
    }

    public void insert(SuppressionEntity entity) {
        MapSqlParameterSource params = params(entity);
        jdbcTemplate.update("""
                INSERT INTO suppressions (
                    id, type, group_id, artifact_id, version, cve_id, reason,
                    expires_at, active, created_at, updated_at
                ) VALUES (
                    :id, :type, :group_id, :artifact_id, :version, :cve_id, :reason,
                    :expires_at, :active, :created_at, :updated_at
                )
                """, params);
    }

    public Optional<SuppressionEntity> update(SuppressionEntity entity) {
        MapSqlParameterSource params = params(entity);
        int updated = jdbcTemplate.update("""
                UPDATE suppressions
                SET type = :type,
                    group_id = :group_id,
                    artifact_id = :artifact_id,
                    version = :version,
                    cve_id = :cve_id,
                    reason = :reason,
                    expires_at = :expires_at,
                    active = :active,
                    updated_at = :updated_at
                WHERE id = :id
                """, params);
        return updated == 0 ? Optional.empty() : findById(entity.id());
    }

    public boolean deleteById(String id) {
        return jdbcTemplate.update("DELETE FROM suppressions WHERE id = :id",
                new MapSqlParameterSource("id", id)) > 0;
    }

    private MapSqlParameterSource params(SuppressionEntity entity) {
        return new MapSqlParameterSource()
                .addValue("id", entity.id())
                .addValue("type", entity.type().name())
                .addValue("group_id", entity.groupId())
                .addValue("artifact_id", entity.artifactId())
                .addValue("version", entity.version())
                .addValue("cve_id", entity.cveId())
                .addValue("reason", entity.reason())
                .addValue("expires_at", entity.expiresAt() == null ? null : entity.expiresAt().toString())
                .addValue("active", entity.active() ? 1 : 0)
                .addValue("created_at", entity.createdAt().toString())
                .addValue("updated_at", entity.updatedAt().toString());
    }

    private RowMapper<SuppressionEntity> rowMapper() {
        return (rs, rowNum) -> new SuppressionEntity(
                rs.getString("id"),
                SuppressionType.valueOf(rs.getString("type")),
                rs.getString("group_id"),
                rs.getString("artifact_id"),
                rs.getString("version"),
                rs.getString("cve_id"),
                rs.getString("reason"),
                toInstant(rs, "expires_at"),
                rs.getInt("active") != 0,
                toInstant(rs, "created_at"),
                toInstant(rs, "updated_at")
        );
    }

    private Instant toInstant(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}
