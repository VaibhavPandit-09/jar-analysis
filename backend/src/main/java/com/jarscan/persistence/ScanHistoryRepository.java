package com.jarscan.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarscan.dto.AnalysisResult;
import com.jarscan.model.InputType;
import com.jarscan.model.JobStatus;
import com.jarscan.model.Severity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ScanHistoryRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ScanHistoryRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void upsert(PersistedScanUpsert scan) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", scan.id())
                .addValue("job_id", scan.jobId())
                .addValue("input_type", scan.inputType().name())
                .addValue("input_name", scan.inputName())
                .addValue("input_hash", scan.inputHash())
                .addValue("status", scan.status().name())
                .addValue("started_at", toText(scan.startedAt()))
                .addValue("completed_at", toText(scan.completedAt()))
                .addValue("duration_ms", scan.durationMs())
                .addValue("total_artifacts", scan.totalArtifacts())
                .addValue("total_dependencies", scan.totalDependencies())
                .addValue("total_vulnerabilities", scan.totalVulnerabilities())
                .addValue("critical_count", scan.criticalCount())
                .addValue("high_count", scan.highCount())
                .addValue("medium_count", scan.mediumCount())
                .addValue("low_count", scan.lowCount())
                .addValue("info_count", scan.infoCount())
                .addValue("unknown_count", scan.unknownCount())
                .addValue("highest_cvss", scan.highestCvss(), Types.DOUBLE)
                .addValue("required_java_version", scan.requiredJavaVersion())
                .addValue("created_app_version", scan.createdAppVersion())
                .addValue("notes", scan.notes())
                .addValue("tags", serializeTags(scan.tags()))
                .addValue("result_json", scan.resultJson())
                .addValue("created_at", toText(scan.createdAt()))
                .addValue("updated_at", toText(scan.updatedAt()));

        jdbcTemplate.update("""
                INSERT INTO scans (
                    id, job_id, input_type, input_name, input_hash, status,
                    started_at, completed_at, duration_ms,
                    total_artifacts, total_dependencies, total_vulnerabilities,
                    critical_count, high_count, medium_count, low_count, info_count, unknown_count,
                    highest_cvss, required_java_version, created_app_version,
                    notes, tags, result_json, created_at, updated_at
                ) VALUES (
                    :id, :job_id, :input_type, :input_name, :input_hash, :status,
                    :started_at, :completed_at, :duration_ms,
                    :total_artifacts, :total_dependencies, :total_vulnerabilities,
                    :critical_count, :high_count, :medium_count, :low_count, :info_count, :unknown_count,
                    :highest_cvss, :required_java_version, :created_app_version,
                    :notes, :tags, :result_json, :created_at, :updated_at
                )
                ON CONFLICT(job_id) DO UPDATE SET
                    id = excluded.id,
                    input_type = excluded.input_type,
                    input_name = excluded.input_name,
                    input_hash = excluded.input_hash,
                    status = excluded.status,
                    started_at = excluded.started_at,
                    completed_at = excluded.completed_at,
                    duration_ms = excluded.duration_ms,
                    total_artifacts = excluded.total_artifacts,
                    total_dependencies = excluded.total_dependencies,
                    total_vulnerabilities = excluded.total_vulnerabilities,
                    critical_count = excluded.critical_count,
                    high_count = excluded.high_count,
                    medium_count = excluded.medium_count,
                    low_count = excluded.low_count,
                    info_count = excluded.info_count,
                    unknown_count = excluded.unknown_count,
                    highest_cvss = excluded.highest_cvss,
                    required_java_version = excluded.required_java_version,
                    created_app_version = excluded.created_app_version,
                    notes = excluded.notes,
                    tags = excluded.tags,
                    result_json = excluded.result_json,
                    updated_at = excluded.updated_at
                """, params);
    }

    public Optional<PersistedScanRecord> findById(String scanId) {
        return jdbcTemplate.query("SELECT * FROM scans WHERE id = :id",
                        new MapSqlParameterSource("id", scanId),
                        rowMapper())
                .stream()
                .findFirst();
    }

    public Optional<PersistedScanRecord> findByJobId(String jobId) {
        return jdbcTemplate.query("SELECT * FROM scans WHERE job_id = :job_id",
                        new MapSqlParameterSource("job_id", jobId),
                        rowMapper())
                .stream()
                .findFirst();
    }

    public List<PersistedScanRecord> findAll(ScanSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder("SELECT * FROM scans");
        List<String> conditions = new ArrayList<>();
        MapSqlParameterSource params = new MapSqlParameterSource();

        if (criteria.query() != null && !criteria.query().isBlank()) {
            conditions.add("(LOWER(input_name) LIKE :query OR LOWER(COALESCE(notes, '')) LIKE :query OR LOWER(COALESCE(tags, '')) LIKE :query OR LOWER(job_id) LIKE :query)");
            params.addValue("query", "%" + criteria.query().toLowerCase() + "%");
        }
        if (criteria.inputType() != null) {
            conditions.add("input_type = :input_type");
            params.addValue("input_type", criteria.inputType().name());
        }
        if (criteria.status() != null) {
            conditions.add("status = :status");
            params.addValue("status", criteria.status().name());
        }
        if (criteria.severity() != null) {
            conditions.add(severityClause(criteria.severity()));
        }
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }

        sql.append(" ORDER BY ").append(sortColumn(criteria.sort())).append(" ").append(sortDirection(criteria.direction()));
        sql.append(" LIMIT :limit OFFSET :offset");
        params.addValue("limit", criteria.limit() == null ? 50 : Math.max(1, criteria.limit()));
        params.addValue("offset", criteria.offset() == null ? 0 : Math.max(0, criteria.offset()));

        return jdbcTemplate.query(sql.toString(), params, rowMapper());
    }

    public boolean deleteById(String scanId) {
        int deleted = jdbcTemplate.update("DELETE FROM scans WHERE id = :id", new MapSqlParameterSource("id", scanId));
        return deleted > 0;
    }

    public Optional<PersistedScanRecord> updateMetadata(String scanId, StoredScanMetadataUpdate update) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", scanId)
                .addValue("notes", update.notes())
                .addValue("tags", serializeTags(update.tags()))
                .addValue("updated_at", Instant.now().toString());

        int count = jdbcTemplate.update("""
                UPDATE scans
                SET notes = :notes,
                    tags = :tags,
                    updated_at = :updated_at
                WHERE id = :id
                """, params);
        if (count == 0) {
            return Optional.empty();
        }
        return findById(scanId);
    }

    private RowMapper<PersistedScanRecord> rowMapper() {
        return (resultSet, rowNum) -> new PersistedScanRecord(
                resultSet.getString("id"),
                resultSet.getString("job_id"),
                InputType.valueOf(resultSet.getString("input_type")),
                resultSet.getString("input_name"),
                resultSet.getString("input_hash"),
                JobStatus.valueOf(resultSet.getString("status")),
                toInstant(resultSet.getString("started_at")),
                toInstant(resultSet.getString("completed_at")),
                nullableLong(resultSet, "duration_ms"),
                resultSet.getInt("total_artifacts"),
                resultSet.getInt("total_dependencies"),
                resultSet.getInt("total_vulnerabilities"),
                resultSet.getInt("critical_count"),
                resultSet.getInt("high_count"),
                resultSet.getInt("medium_count"),
                resultSet.getInt("low_count"),
                resultSet.getInt("info_count"),
                resultSet.getInt("unknown_count"),
                nullableDouble(resultSet, "highest_cvss"),
                resultSet.getString("required_java_version"),
                resultSet.getString("created_app_version"),
                resultSet.getString("notes"),
                deserializeTags(resultSet.getString("tags")),
                resultSet.getString("result_json"),
                deserializeResult(resultSet.getString("result_json")),
                toInstant(resultSet.getString("created_at")),
                toInstant(resultSet.getString("updated_at"))
        );
    }

    private String severityClause(Severity severity) {
        return switch (severity) {
            case CRITICAL -> "critical_count > 0";
            case HIGH -> "high_count > 0";
            case MEDIUM -> "medium_count > 0";
            case LOW -> "low_count > 0";
            case INFO -> "info_count > 0";
            case UNKNOWN -> "unknown_count > 0";
        };
    }

    private String sortColumn(String sort) {
        if (sort == null || sort.isBlank()) {
            return "created_at";
        }
        Map<String, String> allowed = new LinkedHashMap<>();
        allowed.put("createdAt", "created_at");
        allowed.put("completedAt", "completed_at");
        allowed.put("startedAt", "started_at");
        allowed.put("inputName", "input_name");
        allowed.put("status", "status");
        allowed.put("totalVulnerabilities", "total_vulnerabilities");
        allowed.put("highestCvss", "highest_cvss");
        return allowed.getOrDefault(sort, "created_at");
    }

    private String sortDirection(String direction) {
        return "asc".equalsIgnoreCase(direction) ? "ASC" : "DESC";
    }

    private String serializeTags(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags == null ? List.of() : tags);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize tags", ex);
        }
    }

    private List<String> deserializeTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(tagsJson, STRING_LIST);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private AnalysisResult deserializeResult(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(resultJson, AnalysisResult.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private Instant toInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private String toText(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }
}
