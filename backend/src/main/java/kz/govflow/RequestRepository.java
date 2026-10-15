package kz.govflow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RequestRepository extends JpaRepository<Request,Long> { List<Request> findByStatusOrderByCreatedAtDesc(Request.Status status); List<Request> findAllByOrderByCreatedAtDesc(); }
