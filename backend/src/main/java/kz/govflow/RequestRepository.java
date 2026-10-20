package kz.govflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;

public interface RequestRepository extends JpaRepository<ServiceRequest, Long>, JpaSpecificationExecutor<ServiceRequest> {
    List<ServiceRequest> findAllByOrderByCreatedAtDescIdDesc();
    List<ServiceRequest> findByStatusOrderByCreatedAtDescIdDesc(RequestStatus status);
}
