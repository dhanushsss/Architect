package com.architect;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SecurityConfigIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicVersion_returns200WithoutAuth() throws Exception {
        mockMvc.perform(get("/api/public/version").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void versionedRepos_returns401WithoutJwt() throws Exception {
        mockMvc.perform(get("/api/v1/repos").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
