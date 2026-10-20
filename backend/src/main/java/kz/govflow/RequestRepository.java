package kz.govflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface RequestRepository extends JpaRepository<ServiceRequest, Long>, JpaSpecificationExecutor<ServiceRequest> {
    List<ServiceRequest> findAllByOrderByCreatedAtDescIdDesc();
    List<ServiceRequest> findByStatusOrderByCreatedAtDescIdDesc(RequestStatus status);

    @Query("select r.status as status, count(r) as total from ServiceRequest r group by r.status")
    List<StatusCount> countByStatus();

    interface StatusCount {
        RequestStatus getStatus();
        long getTotal();
    }
}
