package io.arcledger.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.arcledger.domain.AppUser;
import io.arcledger.repository.AppUserRepository;
import io.arcledger.security.RateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "arcledger.rate-limit.login.limit=2")
@AutoConfigureMockMvc
@Transactional
class ApiHardeningIntegrationTest {
    private static final String EMAIL = "hardening@example.com";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RateLimitService rateLimits;

    @BeforeEach
    void setUp() {
        rateLimits.clear();
        users.save(new AppUser(EMAIL, passwordEncoder.encode("a-secure-test-password"), "Hardening Test"));
    }

    @Test
    void returnsRequestIdsAndSafeValidationErrors() throws Exception {
        String requestId = "client-request-123";
        String privateMarker = "do-not-reflect-this-value";

        mvc.perform(post("/stories").with(user(EMAIL)).with(csrf())
                .header("X-Request-ID", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ApiModels.CreateStoryRequest(
                    privateMarker.repeat(6), "draft"))))
            .andExpect(status().isBadRequest())
            .andExpect(header().string("X-Request-ID", requestId))
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("Request validation failed."))
            .andExpect(jsonPath("$.requestId").value(requestId))
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain(privateMarker));
    }

    @Test
    void addsBrowserSecurityHeaders() throws Exception {
        mvc.perform(get("/auth/csrf").secure(true))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'self'")))
            .andExpect(header().string("Permissions-Policy", org.hamcrest.Matchers.containsString("camera=()")))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(header().string("Cross-Origin-Opener-Policy", "same-origin"))
            .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().exists("Strict-Transport-Security"));
    }

    @Test
    void rejectsOversizedBodiesBeforeJsonBinding() throws Exception {
        String oversized = "{\"title\":\"Large\",\"description\":\"" + "x".repeat(132_000) + "\"}";

        mvc.perform(post("/stories").with(user(EMAIL)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(oversized))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.error").value("PAYLOAD_TOO_LARGE"))
            .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void paginatesCollectionsAndCapsPageSize() throws Exception {
        for (int index = 1; index <= 5; index++) {
            mvc.perform(post("/stories").with(user(EMAIL)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"Story " + index + "\",\"description\":\"Draft\"}"))
                .andExpect(status().isCreated());
        }

        mvc.perform(get("/stories").with(user(EMAIL)).param("page", "1").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.totalItems").value(5))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.hasNext").value(true));

        mvc.perform(get("/stories").with(user(EMAIL)).param("size", "101"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void rateLimitsRepeatedLoginAttemptsWithoutLeakingAccountData() throws Exception {
        String login = "{\"email\":\"" + EMAIL + "\",\"password\":\"wrong-password\"}";

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/auth/login").with(csrf()).with(remoteAddress("203.0.113.10"))
                    .contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("RateLimit-Limit", "2"));
        }

        mvc.perform(post("/auth/login").with(csrf()).with(remoteAddress("203.0.113.10"))
                .contentType(MediaType.APPLICATION_JSON).content(login))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("RateLimit-Remaining", "0"))
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.error").value("RATE_LIMITED"))
            .andExpect(jsonPath("$.message").value("Too many requests. Try again later."));
    }

    @Test
    void makesSceneCreationIdempotentAndRejectsKeyReuseForDifferentContent() throws Exception {
        UUID storyId = idFrom(mvc.perform(post("/stories").with(user(EMAIL)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Idempotent story\",\"description\":\"Draft\"}"))
            .andExpect(status().isCreated()).andReturn());
        UUID chapterId = idFrom(mvc.perform(post("/stories/{storyId}/chapters", storyId)
                .with(user(EMAIL)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":1,\"title\":\"Opening\"}"))
            .andExpect(status().isCreated()).andReturn());

        String key = "scene-request-0001";
        String body = "{\"sequence\":1,\"rawText\":\"Mira enters the observatory.\"}";
        MvcResult first = mvc.perform(post("/stories/{storyId}/chapters/{chapterId}/scenes", storyId, chapterId)
                .with(user(EMAIL)).with(csrf()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Idempotency-Replayed", "false"))
            .andReturn();
        UUID sceneId = idFrom(first);

        mvc.perform(post("/stories/{storyId}/chapters/{chapterId}/scenes", storyId, chapterId)
                .with(user(EMAIL)).with(csrf()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isAccepted())
            .andExpect(header().string("Idempotency-Replayed", "true"))
            .andExpect(jsonPath("$.id").value(sceneId.toString()));

        mvc.perform(post("/stories/{storyId}/chapters/{chapterId}/scenes", storyId, chapterId)
                .with(user(EMAIL)).with(csrf()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sequence\":1,\"rawText\":\"Different content.\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("IDEMPOTENCY_KEY_REUSED"));

        mvc.perform(post("/stories/{storyId}/chapters/{chapterId}/scenes", storyId, chapterId)
                .with(user(EMAIL)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    private UUID idFrom(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(json.get("id").asText());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor remoteAddress(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
