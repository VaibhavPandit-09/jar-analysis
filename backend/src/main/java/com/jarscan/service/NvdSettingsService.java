package com.jarscan.service;

import com.jarscan.dto.NvdSettingsRequest;
import com.jarscan.dto.NvdSettingsStatusResponse;
import com.jarscan.dto.NvdSettingsTestResponse;
import com.jarscan.persistence.AppSettingRecord;
import com.jarscan.persistence.AppSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class NvdSettingsService {

    static final String KEY_SUFFIX_SETTING = "nvd.apiKey.maskedSuffix";
    private static final Pattern API_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9\\-]{8,128}$");

    private final NvdApiKeyStore apiKeyStore;
    private final AppSettingsRepository appSettingsRepository;

    public NvdSettingsService(NvdApiKeyStore apiKeyStore, AppSettingsRepository appSettingsRepository) {
        this.apiKeyStore = apiKeyStore;
        this.appSettingsRepository = appSettingsRepository;
    }

    public NvdSettingsStatusResponse getStatus() {
        Optional<String> apiKey = apiKeyStore.read();
        Optional<AppSettingRecord> suffixSetting = appSettingsRepository.findByKey(KEY_SUFFIX_SETTING);

        if (apiKey.isEmpty()) {
            return new NvdSettingsStatusResponse(false, null, apiKeyStore.storageMode(), suffixSetting.map(AppSettingRecord::updatedAt).orElse(null));
        }

        String suffix = suffixSetting.map(AppSettingRecord::value).orElseGet(() -> suffix(apiKey.get()));
        Instant updatedAt = suffixSetting.map(AppSettingRecord::updatedAt)
                .or(() -> apiKeyStore.lastModifiedAt())
                .orElse(null);
        return new NvdSettingsStatusResponse(true, masked(suffix), apiKeyStore.storageMode(), updatedAt);
    }

    public NvdSettingsStatusResponse save(NvdSettingsRequest request) {
        String apiKey = normalizeAndValidate(request.apiKey());
        apiKeyStore.save(apiKey);
        appSettingsRepository.upsert(KEY_SUFFIX_SETTING, suffix(apiKey), false);
        return getStatus();
    }

    public void delete() {
        apiKeyStore.delete();
        appSettingsRepository.deleteByKey(KEY_SUFFIX_SETTING);
    }

    public NvdSettingsTestResponse testConfiguredKey() {
        Optional<String> apiKey = apiKeyStore.read();
        if (apiKey.isEmpty()) {
            return new NvdSettingsTestResponse(false, false, "No NVD API key is currently configured");
        }
        boolean valid = API_KEY_PATTERN.matcher(apiKey.get()).matches();
        return new NvdSettingsTestResponse(true, valid, valid
                ? "Stored NVD API key format looks valid"
                : "Stored NVD API key is present, but its format looks suspicious");
    }

    public Optional<String> currentApiKey() {
        return apiKeyStore.read();
    }

    private String normalizeAndValidate(String apiKey) {
        String normalized = apiKey == null ? "" : apiKey.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NVD API key is required");
        }
        if (!API_KEY_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "NVD API key format looks invalid. Expected 8-128 letters, digits, or hyphens.");
        }
        return normalized;
    }

    private String suffix(String apiKey) {
        return apiKey.length() <= 4 ? apiKey : apiKey.substring(apiKey.length() - 4);
    }

    private String masked(String suffix) {
        return "****" + suffix;
    }
}
