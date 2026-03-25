package com.architect.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointExtractorServiceTest {

    private final EndpointExtractorService service = new EndpointExtractorService();

    @Test
    void extractsSpringMappingsFromController() {
        String src = """
                @RestController
                @RequestMapping("/api/users")
                class UserController {
                    @GetMapping("/profile")
                    String profile() { return "ok"; }
                }
                """;

        List<EndpointExtractorService.ExtractedEndpoint> out = service.extract(src, "UserController.java");
        assertEquals(1, out.size());
        assertEquals("GET", out.get(0).getHttpMethod());
        assertEquals("/api/users/profile", out.get(0).getPath());
    }

    @Test
    void extractsExpressRouteFromNodeFile() {
        String src = """
                const app = express();
                app.post('/api/bookings', handler);
                """;

        List<EndpointExtractorService.ExtractedEndpoint> out = service.extract(src, "server.js");
        assertEquals(1, out.size());
        assertEquals("POST", out.get(0).getHttpMethod());
        assertTrue(out.get(0).getPath().startsWith("/api/bookings"));
    }
}

