package kz.govflow;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RequestService {
    private final RequestRepository repository;

    public RequestService(RequestRepository repository) { this.repository = repository; }

    public List<RequestResponse> list(RequestStatus status) {
        var requests = status == null ? repository.findAllByOrderByCreatedAtDescIdDesc()
                : repository.findByStatusOrderByCreatedAtDescIdDesc(status);
        return requests.stream().map(RequestResponse::from).toList();
    }

    public RequestResponse get(long id) {
        return RequestResponse.from(find(id));
    }

    private ServiceRequest find(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
    }

    @Transactional
    public RequestResponse create(String title, String description) {
        return RequestResponse.from(repository.save(new ServiceRequest(title, description)));
    }

    @Transactional
    public RequestResponse changeStatus(long id, RequestStatus status) {
        var request = find(id);
        request.changeStatus(status);
        repository.flush();
        return RequestResponse.from(request);
    }
}
