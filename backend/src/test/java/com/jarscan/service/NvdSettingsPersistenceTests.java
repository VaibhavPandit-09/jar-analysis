package com.jarscan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarscan.dto.NvdSettingsRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NvdSettingsPersistenceTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private NvdApiKeyStore apiKeyStore;

    @Autowired
    private NvdSettingsService nvdSettingsService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute("DELETE FROM app_settings");
        Files.deleteIfExists(apiKeyStore.apiKeyPath());
        Files.createDirectories(apiKeyStore.apiKeyPath().getParent());
    }

    @Test
    void savesAndReturnsMaskedNvdKeyStatus() throws Exception {
        mockMvc.perform(post("/api/settings/nvd")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new NvdSettingsRequest("abcd1234-xyz9"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.maskedKey").value("****xyz9"));

        assertThat(Files.readString(apiKeyStore.apiKeyPath())).isEqualTo("abcd1234-xyz9");

        mockMvc.perform(get("/api/settings/nvd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.maskedKey").value("****xyz9"))
                .andExpect(jsonPath("$.maskedKey").value(org.hamcrest.Matchers.not("abcd1234-xyz9")));
    }

    @Test
    void deletesConfiguredNvdKey() throws Exception {
        nvdSettingsService.save(new NvdSettingsRequest("abcd1234-xyz9"));

        mockMvc.perform(delete("/api/settings/nvd"))
                .andExpect(status().isNoContent());

        assertThat(Files.exists(apiKeyStore.apiKeyPath())).isFalse();
        mockMvc.perform(get("/api/settings/nvd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.maskedKey").doesNotExist());
    }

    @Test
    void testsStoredKeyWithoutReturningRawValue() throws Exception {
        nvdSettingsService.save(new NvdSettingsRequest("abcd1234-xyz9"));

        String body = mockMvc.perform(post("/api/settings/nvd/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.valid").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("abcd1234-xyz9");
    }
}
