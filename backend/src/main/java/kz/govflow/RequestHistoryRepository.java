package kz.govflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestHistoryRepository extends JpaRepository<RequestHistory, Long> {
    List<RequestHistory> findByRequestIdOrderByOccurredAtAscIdAsc(long requestId);
}
