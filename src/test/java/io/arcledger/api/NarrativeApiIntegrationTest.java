package io.arcledger.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class NarrativeApiIntegrationTest {
    @Autowired MockMvc mvc;

    @Test void createsStoryAndRejectsInvalidPayload() throws Exception {
        mvc.perform(post("/stories").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Glass Horizon\",\"description\":\"A test story\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Glass Horizon"));
        mvc.perform(post("/stories").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }
}
