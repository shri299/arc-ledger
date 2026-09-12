package io.arcledger.api;

import com.fasterxml.jackson.databind.*;
import io.arcledger.repository.*;
import io.arcledger.security.AccountMailer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.*;

import jakarta.servlet.http.Cookie;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {io.arcledger.ArcLedgerApplication.class, Phase5LifecycleIntegrationTest.MailTestConfig.class})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Phase5LifecycleIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired CaptureMailer mailer;
    @Autowired AppUserRepository users;
    @Autowired StoryRepository stories;

    @Test
    void verifiesEmailResetsPasswordAndConsumesRecoveryCodes() throws Exception {
        String email = "lifecycle@example.com";
        Cookie session = signup(email, "Original long password!9", "Lifecycle Author");

        mvc.perform(post("/auth/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ApiModels.TokenRequest(mailer.verification(email)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emailVerified").value(true));

        mvc.perform(post("/auth/password/forgot").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"lifecycle@example.com\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/auth/password/forgot").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"missing@example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("If the account exists, password reset instructions have been sent."));

        mvc.perform(post("/auth/password/reset").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ApiModels.NewPasswordRequest(
                    mailer.passwordReset(email), "Replacement long password!9"))))
            .andExpect(status().isOk());
        mvc.perform(get("/auth/me").cookie(session)).andExpect(status().isUnauthorized());

        Cookie replacementSession = login(email, "Replacement long password!9");
        MvcResult recoveryResult = mvc.perform(post("/account/recovery-codes").cookie(replacementSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Replacement long password!9\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recoveryCodes.length()").value(8))
            .andReturn();
        String code = mapper.readTree(recoveryResult.getResponse().getContentAsString()).get("recoveryCodes").get(0).asText();

        String recoveryBody = mapper.writeValueAsString(new ApiModels.RecoveryResetRequest(
            email, code, "Recovered long password!9"));
        mvc.perform(post("/auth/recovery/reset").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(recoveryBody))
            .andExpect(status().isOk());
        mvc.perform(post("/auth/recovery/reset").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(recoveryBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("ACCOUNT_ACTION_REJECTED"));
        login(email, "Recovered long password!9");
    }

    @Test
    void listsAndRevokesOtherDeviceSessions() throws Exception {
        String email = "sessions@example.com";
        Cookie first = signup(email, "A first sufficiently long password", "Session Author");
        Cookie second = login(email, "A first sufficiently long password");

        mvc.perform(get("/account/sessions").cookie(second))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(post("/account/sessions/revoke-others").cookie(second).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("1 other session(s) revoked."));
        mvc.perform(get("/auth/me").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/me").cookie(second)).andExpect(status().isOk());
    }

    @Test
    void exportsAndPermanentlyDeletesOnlyTheAuthenticatedAccount() throws Exception {
        String email = "deletion@example.com";
        Cookie session = signup(email, "Deletion sufficiently long password", "Deletion Author");
        mvc.perform(post("/stories").cookie(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Exported story\",\"description\":\"Private draft\"}"))
            .andExpect(status().isCreated());

        mvc.perform(get("/account/export").cookie(session))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=arcledger-account-export.json"))
            .andExpect(jsonPath("$.format").value("arcledger-account-export-v1"))
            .andExpect(jsonPath("$.stories[0].title").value("Exported story"));

        mvc.perform(post("/account/delete").cookie(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong password value\",\"confirmation\":\"DELETE MY ACCOUNT\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/account/delete").cookie(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Deletion sufficiently long password\",\"confirmation\":\"DELETE MY ACCOUNT\"}"))
            .andExpect(status().isNoContent());

        assertThat(users.findByEmail(email)).isEmpty();
        assertThat(stories.findAll()).noneMatch(story -> "Exported story".equals(story.getTitle()));
        mvc.perform(get("/auth/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    private Cookie signup(String email, String password, String displayName) throws Exception {
        MvcResult result = mvc.perform(post("/auth/signup").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ApiModels.SignupRequest(displayName, email, password))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.emailDeliveryConfigured").value(true))
            .andReturn();
        return requiredSession(result);
    }

    private Cookie login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new ApiModels.LoginRequest(email, password))))
            .andExpect(status().isOk()).andReturn();
        return requiredSession(result);
    }

    private static Cookie requiredSession(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("ARCLEDGER_SESSION");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    @TestConfiguration
    static class MailTestConfig {
        @Bean @Primary CaptureMailer captureMailer() { return new CaptureMailer(); }
    }

    static class CaptureMailer implements AccountMailer {
        private final ConcurrentHashMap<String, String> verifications = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, String> resets = new ConcurrentHashMap<>();
        @Override public boolean isConfigured() { return true; }
        @Override public void sendVerification(String recipient, String token) { verifications.put(recipient, token); }
        @Override public void sendPasswordReset(String recipient, String token) { resets.put(recipient, token); }
        @Override public void sendStoryInvitation(String recipient, String inviterName, String storyTitle, String token) {}
        String verification(String email) { return Objects.requireNonNull(verifications.get(email)); }
        String passwordReset(String email) { return Objects.requireNonNull(resets.get(email)); }
    }
}
