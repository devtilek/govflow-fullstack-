package kz.govflow;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RequestPageTest {
    @Autowired MockMvc mvc;
    @Autowired RequestRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void reset() { repository.deleteAll(); }

    private ServiceRequest add(String title, String description) {
        return repository.save(new ServiceRequest(title, description));
    }

    @Test void defaultsAndEmptyPage() throws Exception {
        mvc.perform(get("/api/requests/page")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test void pagesUseStableOrderWhenTimestampsMatch() throws Exception {
        var first = add("First", "Old request");
        var second = add("Second", "New request");
        jdbc.update("update service_requests set created_at = ?", java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        mvc.perform(get("/api/requests/page?size=1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(second.getId()))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/requests/page?size=1&page=1"))
                .andExpect(jsonPath("$.items[0].id").value(first.getId()));
        mvc.perform(get("/api/requests/page?size=1&page=2"))
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test void searchesTitleAndDescriptionWithoutCaseSensitivity() throws Exception {
        add("Street LIGHT", "Needs repair");
        add("Road", "Light is broken");
        add("Water", "Pipe is leaking");
        mvc.perform(get("/api/requests/page").param("q", "  LiGhT  "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test void combinesStatusAndSearch() throws Exception {
        add("Light", "New");
        var active = add("Light", "Active");
        active.changeStatus(RequestStatus.IN_PROGRESS);
        repository.save(active);
        mvc.perform(get("/api/requests/page").param("q", "light").param("status", "IN_PROGRESS"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(active.getId()));
    }

    @Test void percentAndUnderscoreAreLiteralSearchCharacters() throws Exception {
        add("100% ready", "Description");
        add("under_score", "Description");
        add("Ordinary", "Description");
        mvc.perform(get("/api/requests/page").param("q", "%"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/requests/page").param("q", "_"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test void invalidPagingAndOversizedQueryReturn400() throws Exception {
        for (String query : new String[]{"page=-1", "size=0", "size=101", "page=abc", "status=UNKNOWN"}) {
            mvc.perform(get("/api/requests/page?" + query)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/requests/page").param("q", "x".repeat(201)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
    }
}
