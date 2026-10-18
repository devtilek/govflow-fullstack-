package kz.govflow;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RequestRepository extends JpaRepository<ServiceRequest, Long> {
    List<ServiceRequest> findAllByOrderByCreatedAtDescIdDesc();
    List<ServiceRequest> findByStatusOrderByCreatedAtDescIdDesc(RequestStatus status);
}
