package io.arcledger.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.arcledger.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void protectsApisAndRequiresCsrfForMutations() throws Exception {
        mvc.perform(get("/stories"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(signupJson("author@example.com", "A sufficiently long password", "Author")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("INVALID_CSRF_TOKEN"));
    }

    @Test
    void signsUpLogsInAndReturnsOnlyOwnedStories() throws Exception {
        MockHttpSession authorSession = signup("author@example.com", "A sufficiently long password", "Author");

        MvcResult created = mvc.perform(post("/stories").session(authorSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Owned story\",\"description\":\"Private draft\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        UUID storyId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

        mvc.perform(get("/auth/me").session(authorSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("author@example.com"))
            .andExpect(jsonPath("$.displayName").value("Author"));
        mvc.perform(get("/stories").session(authorSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(storyId.toString()));

        MockHttpSession strangerSession = signup("stranger@example.com", "Another sufficiently long password", "Stranger");
        mvc.perform(get("/stories/{storyId}/entities", storyId).session(strangerSession))
            .andExpect(status().isNotFound());
        mvc.perform(get("/stories/{storyId}/entities/{entityId}", storyId, UUID.randomUUID()).session(strangerSession))
            .andExpect(status().isNotFound());
        mvc.perform(get("/stories/{storyId}/entities/{entityId}/history", storyId, UUID.randomUUID()).session(strangerSession))
            .andExpect(status().isNotFound());
        mvc.perform(get("/stories/{storyId}/scenes/{sceneId}/consistency", storyId, UUID.randomUUID()).session(strangerSession))
            .andExpect(status().isNotFound());
        mvc.perform(get("/stories/{storyId}/ask", storyId).param("query", "What happens?").session(strangerSession))
            .andExpect(status().isNotFound());
        mvc.perform(post("/stories/{storyId}/chapters", storyId).session(strangerSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":1,\"title\":\"Private chapter\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/stories/{storyId}/chapters/{chapterId}/scenes", storyId, UUID.randomUUID())
                .session(strangerSession).with(csrf()).header("Idempotency-Key", "stranger-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sequence\":1,\"rawText\":\"Private scene.\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/stories").session(strangerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void normalizesEmailRejectsDuplicatesAndHidesLoginFailures() throws Exception {
        signup(" Author@Example.COM ", "A sufficiently long password", "Author");

        mvc.perform(post("/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(signupJson("author@example.com", "A different long password", "Other")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_REGISTERED"));

        mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"author@example.com\",\"password\":\"wrong-password\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void exposesACsrfBootstrapToken() throws Exception {
        mvc.perform(get("/auth/csrf"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty());
    }

    private MockHttpSession signup(String email, String password, String displayName) throws Exception {
        MvcResult result = mvc.perform(post("/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(signupJson(email, password, displayName)))
            .andExpect(status().isCreated())
            .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private String signupJson(String email, String password, String displayName) throws Exception {
        return objectMapper.writeValueAsString(new ApiModels.SignupRequest(displayName, email, password));
    }
}
