package kz.govflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RequestStatsTest {
    @Autowired MockMvc mvc;
    @Autowired RequestRepository repository;

    @BeforeEach void reset() { repository.deleteAll(); }

    @Test void emptyDatabaseHasZeroCounts() throws Exception {
        mvc.perform(get("/api/requests/stats")).andExpect(status().isOk())
                .andExpect(content().json("{\"total\":0,\"newCount\":0,\"inProgress\":0,\"resolved\":0}"));
    }

    @Test void aggregatesAllStatusesWithoutPagination() throws Exception {
        repository.save(new ServiceRequest("New", "New request"));
        var active = new ServiceRequest("Active", "Active request");
        active.changeStatus(RequestStatus.IN_PROGRESS);
        repository.save(active);
        var resolved = new ServiceRequest("Resolved", "Done");
        resolved.changeStatus(RequestStatus.IN_PROGRESS);
        resolved.changeStatus(RequestStatus.RESOLVED);
        repository.save(resolved);
        mvc.perform(get("/api/requests/stats")).andExpect(status().isOk())
                .andExpect(content().json("{\"total\":3,\"newCount\":1,\"inProgress\":1,\"resolved\":1}"));
    }

    @Test void countsReflectCommittedStatusChanges() throws Exception {
        var request = repository.save(new ServiceRequest("Test", "Description"));
        mvc.perform(patch("/api/requests/{id}/status", request.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/requests/stats"))
                .andExpect(content().json("{\"total\":1,\"newCount\":0,\"inProgress\":1,\"resolved\":0}"));
    }
}
