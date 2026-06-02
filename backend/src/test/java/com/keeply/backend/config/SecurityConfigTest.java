package com.keeply.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.keeply.backend.security.JwtAuthenticationFilter;
import com.keeply.backend.security.JwtService;
import com.keeply.backend.security.TraceIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
        classes = SecurityConfigTest.TestApplication.class,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
class SecurityConfigTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void permitsHealthWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void permitsPrometheusWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    void blocksProtectedRouteWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/private"))
                .andExpect(status().isUnauthorized());
    }

    @EnableAutoConfiguration
    @Import(SecurityConfig.class)
    static class TestApplication {
        @Bean
        JwtService jwtService() {
            return new JwtService("12345678901234567890123456789012", 120, 30);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
            return new JwtAuthenticationFilter(jwtService);
        }

        @Bean
        TraceIdFilter traceIdFilter() {
            return new TraceIdFilter();
        }

        @RestController
        static class TestController {
            @GetMapping(path = "/actuator/health", produces = MediaType.APPLICATION_JSON_VALUE)
            String health() {
                return "{\"status\":\"UP\"}";
            }

            @GetMapping(path = "/actuator/prometheus", produces = MediaType.TEXT_PLAIN_VALUE)
            String prometheus() {
                return "# test_metric 1\n";
            }

            @GetMapping(path = "/api/private", produces = MediaType.TEXT_PLAIN_VALUE)
            String privateRoute() {
                return "ok";
            }
        }
    }
}
