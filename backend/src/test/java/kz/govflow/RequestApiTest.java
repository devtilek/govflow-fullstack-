package kz.govflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RequestApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RequestRepository repository;

    @BeforeEach void reset() { repository.deleteAll(); }

    private long createRequest() throws Exception {
        var response = mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "  Street light  ", "description", "  Needs repair  "))))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.title").value("Street light"))
                .andExpect(jsonPath("$.description").value("Needs repair"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    @Test void createsAndPersistsRequest() throws Exception {
        long id = createRequest();
        assertThat(repository.findById(id)).isPresent();
        mvc.perform(get("/api/requests")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(id));
    }

    @Test void createdResourceCanBeFetchedFromLocation() throws Exception {
        long id = createRequest();
        mvc.perform(get("/api/requests/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Street light"));
        mvc.perform(get("/api/requests/999999")).andExpect(status().isNotFound());
    }

    @Test void emptyListIsAnArray() throws Exception {
        mvc.perform(get("/api/requests")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void rejectsWhitespaceAndMissingDescription() throws Exception {
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.title").exists())
                .andExpect(jsonPath("$.fields.description").exists());
        assertThat(repository.count()).isZero();
    }

    @Test void rejectsOversizedTitleAndDescription() throws Exception {
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "a".repeat(121), "description", "b".repeat(2001)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.title").exists())
                .andExpect(jsonPath("$.fields.description").exists());
    }

    @Test void acceptsMaximumFieldLengths() throws Exception {
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "a".repeat(120), "description", "b".repeat(2000)))))
                .andExpect(status().isCreated());
    }

    @Test void followsLifecycleAndFiltersByStatus() throws Exception {
        long id = createRequest();
        mvc.perform(patch("/api/requests/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(get("/api/requests?status=NEW")).andExpect(content().json("[]"));
        mvc.perform(get("/api/requests?status=IN_PROGRESS")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(patch("/api/requests/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(RequestStatus.RESOLVED);
    }

    @Test void rejectsSkippedTransitionWithoutChangingStoredStatus() throws Exception {
        long id = createRequest();
        mvc.perform(patch("/api/requests/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(RequestStatus.NEW);
    }

    @Test void repeatingCurrentStatusIsIdempotent() throws Exception {
        long id = createRequest();
        mvc.perform(patch("/api/requests/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NEW\"}")).andExpect(status().isOk());
    }

    @Test void unknownRequestReturns404() throws Exception {
        mvc.perform(patch("/api/requests/999999/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Request not found"));
    }

    @Test void invalidStatusAndIdentifierReturn400() throws Exception {
        mvc.perform(get("/api/requests?status=INVALID")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/requests/not-a-number/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"NEW\"}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/requests/1/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":null}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/requests/1/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"INVALID\"}")).andExpect(status().isBadRequest());
    }

    @Test void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
    }

    @Test void clientCannotOverrideStatusOrIdentifier() throws Exception {
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Test\",\"description\":\"Test\",\"status\":\"RESOLVED\",\"id\":1}"))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }
}
