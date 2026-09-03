package io.arcledger.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import io.arcledger.domain.AppUser;
import io.arcledger.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class NarrativeApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void createUser() {
        users.save(new AppUser("author@example.com", passwordEncoder.encode("a-secure-test-password"), "Test Author"));
    }

    @Test void createsStoryAndRejectsInvalidPayload() throws Exception {
        mvc.perform(post("/stories").with(user("author@example.com")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Glass Horizon\",\"description\":\"A test story\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Glass Horizon"));
        mvc.perform(post("/stories").with(user("author@example.com")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }
}
