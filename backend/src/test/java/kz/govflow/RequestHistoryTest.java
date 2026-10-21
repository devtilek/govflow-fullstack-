package kz.govflow;

import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RequestHistoryTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RequestRepository repository;
    @Autowired RequestHistoryRepository historyRepository;

    @BeforeEach void reset() { repository.deleteAll(); }

    private long create() throws Exception {
        var response = mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", "Light", "description", "Broken"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private void change(long id, String next, int expected) throws Exception {
        mvc.perform(patch("/api/requests/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("status", next))))
                .andExpect(status().is(expected));
    }

    @Test void creationRecordsInitialStatusAndTimestamp() throws Exception {
        long id = create();
        mvc.perform(get("/api/requests/{id}/history", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action").value("CREATED"))
                .andExpect(jsonPath("$[0].fromStatus").isEmpty())
                .andExpect(jsonPath("$[0].toStatus").value("NEW"))
                .andExpect(jsonPath("$[0].occurredAt").isNotEmpty());
        assertThat(historyRepository.findByRequestIdOrderByOccurredAtAscIdAsc(id).get(0).getOccurredAt())
                .isEqualTo(repository.findById(id).orElseThrow().getCreatedAt());
    }

    @Test void recordsBothTransitionsInChronologicalOrder() throws Exception {
        long id = create();
        change(id, "IN_PROGRESS", 200);
        change(id, "RESOLVED", 200);
        mvc.perform(get("/api/requests/{id}/history", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].action").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[1].fromStatus").value("NEW"))
                .andExpect(jsonPath("$[1].toStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[2].fromStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[2].toStatus").value("RESOLVED"));
        var events = historyRepository.findByRequestIdOrderByOccurredAtAscIdAsc(id);
        assertThat(events).extracting(RequestHistory::getOccurredAt).isSorted();
    }

    @Test void repeatedStatusDoesNotDuplicateHistory() throws Exception {
        long id = create();
        change(id, "NEW", 200);
        change(id, "IN_PROGRESS", 200);
        change(id, "IN_PROGRESS", 200);
        assertThat(historyRepository.findByRequestIdOrderByOccurredAtAscIdAsc(id)).hasSize(2);
    }

    @Test void rejectedTransitionLeavesHistoryAndStateUnchanged() throws Exception {
        long id = create();
        change(id, "RESOLVED", 409);
        assertThat(historyRepository.findByRequestIdOrderByOccurredAtAscIdAsc(id)).hasSize(1);
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(RequestStatus.NEW);
    }

    @Test void historyIsIsolatedByRequestAndMissingRequestsReturn404() throws Exception {
        long first = create();
        long second = create();
        change(first, "IN_PROGRESS", 200);
        mvc.perform(get("/api/requests/{id}/history", second))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/requests/999999/history")).andExpect(status().isNotFound());
        mvc.perform(get("/api/requests/invalid/history")).andExpect(status().isBadRequest());
    }
}
