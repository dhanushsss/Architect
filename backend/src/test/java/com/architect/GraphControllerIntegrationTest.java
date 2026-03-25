package com.architect;

import com.architect.model.ApiCall;
import com.architect.model.ApiEndpoint;
import com.architect.model.Repo;
import com.architect.model.User;
import com.architect.repository.ApiCallRepository;
import com.architect.repository.ApiEndpointRepository;
import com.architect.repository.RepoRepository;
import com.architect.repository.UserRepository;
import com.architect.security.JwtTokenProvider;
import com.architect.service.ApiCallUrlNormalizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class GraphControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RepoRepository repoRepository;

    @Autowired
    private ApiEndpointRepository apiEndpointRepository;

    @Autowired
    private ApiCallRepository apiCallRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private com.architect.service.GitHubService gitHubService;

    @Test
    void getGraph_returnsNodeAndEdgeCounts() throws Exception {
        User user = userRepository.save(User.builder()
                .githubId(42_001L)
                .login("graph-test-user")
                .name("GT")
                .accessToken("gh-token")
                .build());

        Repo r1 = repoRepository.save(Repo.builder()
                .user(user)
                .githubId(101L)
                .name("svc-a")
                .fullName("o/svc-a")
                .scanStatus(Repo.ScanStatus.COMPLETE)
                .build());

        Repo r2 = repoRepository.save(Repo.builder()
                .user(user)
                .githubId(102L)
                .name("svc-b")
                .fullName("o/svc-b")
                .scanStatus(Repo.ScanStatus.COMPLETE)
                .build());

        ApiEndpoint ep = apiEndpointRepository.save(ApiEndpoint.builder()
                .repo(r1)
                .path("/api/x")
                .httpMethod("GET")
                .filePath("X.java")
                .framework("spring")
                .language("java")
                .build());

        apiCallRepository.save(ApiCall.builder()
                .callerRepo(r2)
                .endpoint(ep)
                .urlPattern("/api/x")
                .filePath("client.ts")
                .lineNumber(1)
                .callType("axios")
                .httpMethod("GET")
                .targetKind(ApiCallUrlNormalizer.KIND_INTERNAL)
                .build());

        String jwt = jwtTokenProvider.generateToken(user.getId(), user.getLogin());

        mockMvc.perform(get("/api/v1/graph")
                        .header("Authorization", "Bearer " + jwt)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stats.totalRepos").value(2))
                .andExpect(jsonPath("$.stats.totalEndpoints").exists())
                .andExpect(jsonPath("$.stats.totalEdges").exists());
    }
}
