package kz.govflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class RequestHistoryRollbackTest {
    @Autowired RequestService service;
    @Autowired RequestRepository repository;
    @MockitoBean RequestHistoryRepository historyRepository;

    @BeforeEach void reset() {
        repository.deleteAll();
        when(historyRepository.save(any(RequestHistory.class)))
                .thenThrow(new DataIntegrityViolationException("Simulated history write failure"));
    }

    @Test void historyFailureRollsBackRequestCreation() {
        assertThatThrownBy(() -> service.create("Light", "Broken"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.count()).isZero();
    }

    @Test void historyFailureRollsBackAlreadyFlushedStatusChange() {
        var request = repository.save(new ServiceRequest("Light", "Broken"));
        assertThatThrownBy(() -> service.changeStatus(request.getId(), RequestStatus.IN_PROGRESS))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(RequestStatus.NEW);
    }
}
