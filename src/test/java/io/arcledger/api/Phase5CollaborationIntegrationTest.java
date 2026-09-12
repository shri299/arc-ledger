package io.arcledger.api;

import com.fasterxml.jackson.databind.*;
import io.arcledger.security.AccountMailer;
import io.arcledger.domain.AccountRole;
import io.arcledger.repository.AppUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {io.arcledger.ArcLedgerApplication.class, Phase5CollaborationIntegrationTest.MailTestConfig.class},
    properties = "arcledger.security.require-email-verification=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase5CollaborationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired CaptureMailer mailer;
    @Autowired AppUserRepository users;

    @Test
    void enforcesViewerEditorAndOwnerPermissions() throws Exception {
        Cookie owner = signup("owner5@example.com", "Owner sufficiently long password", "Owner");
        Cookie collaborator = signup("collaborator5@example.com", "Collaborator long password", "Collaborator");
        Cookie stranger = signup("stranger5@example.com", "Stranger sufficiently long password", "Stranger");

        mvc.perform(post("/stories").cookie(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Blocked before verification\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("EMAIL_VERIFICATION_REQUIRED"));
        verify(owner, "owner5@example.com");
        verify(collaborator, "collaborator5@example.com");
        verify(stranger, "stranger5@example.com");

        MvcResult storyResult = mvc.perform(post("/stories").cookie(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Shared constellation\",\"description\":\"Collaboration test\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("OWNER"))
            .andReturn();
        String storyId = mapper.readTree(storyResult.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/stories/{storyId}/chapters", storyId).cookie(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":1,\"title\":\"Shared opening\"}"))
            .andExpect(status().isCreated());

        mvc.perform(post("/stories/{storyId}/invitations", storyId).cookie(owner).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"collaborator5@example.com\",\"role\":\"VIEWER\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("VIEWER"));

        mvc.perform(post("/invitations/accept").cookie(collaborator).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ApiModels.TokenRequest(
                    mailer.invitation("collaborator5@example.com")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("VIEWER"));

        mvc.perform(get("/stories").cookie(collaborator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(storyId))
            .andExpect(jsonPath("$.items[0].role").value("VIEWER"));
        mvc.perform(get("/stories/{storyId}/entities", storyId).cookie(collaborator))
            .andExpect(status().isOk());
        mvc.perform(get("/stories/{storyId}/chapters", storyId).cookie(collaborator))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].title").value("Shared opening"));
        mvc.perform(get("/stories/{storyId}/scenes", storyId).cookie(collaborator))
            .andExpect(status().isOk());
        mvc.perform(post("/stories/{storyId}/chapters", storyId).cookie(collaborator).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":1,\"title\":\"Viewer cannot write\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/stories/{storyId}/collaborators", storyId).cookie(collaborator))
            .andExpect(status().isNotFound());

        JsonNode collaborators = mapper.readTree(mvc.perform(get("/stories/{storyId}/collaborators", storyId).cookie(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members.length()").value(2))
            .andReturn().getResponse().getContentAsString());
        String collaboratorId = findUserId(collaborators.get("members"), "collaborator5@example.com");
        mvc.perform(post("/stories/{storyId}/collaborators/{userId}/role", storyId, collaboratorId)
                .cookie(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"EDITOR\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("EDITOR"));
        mvc.perform(post("/stories/{storyId}/chapters", storyId).cookie(collaborator).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":2,\"title\":\"Editor can write\"}"))
            .andExpect(status().isCreated());

        mvc.perform(get("/stories/{storyId}/entities", storyId).cookie(stranger)).andExpect(status().isNotFound());
        mvc.perform(post("/stories/{storyId}/invitations", storyId).cookie(stranger).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\",\"role\":\"VIEWER\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void restrictsAbuseControlsToAdminsAndRevokesSuspendedSessions() throws Exception {
        Cookie initialAdmin = signup("admin5@example.com", "Admin sufficiently long password", "Administrator");
        Cookie member = signup("member5@example.com", "Member sufficiently long password", "Member");
        var adminUser = users.findByEmail("admin5@example.com").orElseThrow();
        adminUser.changeAccountRole(AccountRole.ADMIN, Instant.now());
        users.saveAndFlush(adminUser);
        Cookie admin = login("admin5@example.com", "Admin sufficiently long password");
        String memberId = users.findByEmail("member5@example.com").orElseThrow().getId().toString();

        mvc.perform(get("/admin/accounts").cookie(initialAdmin)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/accounts").cookie(admin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalItems").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
        mvc.perform(post("/admin/accounts/{userId}/suspend", memberId).cookie(admin).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(get("/auth/me").cookie(member)).andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/accounts/{userId}/restore", memberId).cookie(admin).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true));
        login("member5@example.com", "Member sufficiently long password");
        mvc.perform(get("/admin/audit-events").cookie(admin)).andExpect(status().isOk());
    }

    private Cookie signup(String email, String password, String displayName) throws Exception {
        MvcResult result = mvc.perform(post("/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ApiModels.SignupRequest(displayName, email, password))))
            .andExpect(status().isCreated()).andReturn();
        Cookie session = result.getResponse().getCookie("ARCLEDGER_SESSION");
        assertThat(session).isNotNull();
        return session;
    }

    private Cookie login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ApiModels.LoginRequest(email, password))))
            .andExpect(status().isOk()).andReturn();
        Cookie session = result.getResponse().getCookie("ARCLEDGER_SESSION");
        assertThat(session).isNotNull();
        return session;
    }

    private void verify(Cookie session, String email) throws Exception {
        mvc.perform(post("/auth/verify").cookie(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ApiModels.TokenRequest(mailer.verification(email)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emailVerified").value(true));
    }

    private static String findUserId(JsonNode members, String email) {
        for (JsonNode member : members) if (email.equals(member.get("email").asText())) return member.get("userId").asText();
        throw new AssertionError("Collaborator not found");
    }

    @TestConfiguration
    static class MailTestConfig {
        @Bean @Primary CaptureMailer captureMailer() { return new CaptureMailer(); }
    }

    static class CaptureMailer implements AccountMailer {
        private final ConcurrentHashMap<String, String> invitations = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> verifications = new ConcurrentHashMap<>();
        @Override public boolean isConfigured() { return true; }
        @Override public void sendVerification(String recipient, String token) { verifications.put(recipient, token); }
        @Override public void sendPasswordReset(String recipient, String token) {}
        @Override public void sendStoryInvitation(String recipient, String inviterName, String storyTitle, String token) {
            invitations.put(recipient, token);
        }
        String invitation(String email) { return Objects.requireNonNull(invitations.get(email)); }
        String verification(String email) { return Objects.requireNonNull(verifications.get(email)); }
    }
}
