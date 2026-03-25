package com.architect;

import com.architect.interceptor.RateLimitInterceptor;
import com.architect.model.User;
import com.architect.repository.UserRepository;
import com.architect.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RateLimitInterceptorIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String bearer;

    @BeforeEach
    void setUp() {
        User u = userRepository.save(User.builder()
                .githubId(999_001L)
                .login("ratelimit-test-user")
                .name("RL")
                .accessToken("token")
                .build());
        bearer = "Bearer " + jwtTokenProvider.generateToken(u.getId(), u.getLogin());
    }

    @Test
    void returns429AfterLimitPerMinute() throws Exception {
        int limit = RateLimitInterceptor.LIMIT;
        for (int i = 0; i < limit; i++) {
            mockMvc.perform(get("/api/v1/repos")
                            .header("Authorization", bearer)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/repos")
                        .header("Authorization", bearer)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
