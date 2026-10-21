package kz.govflow;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Locale;
import java.util.EnumMap;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@Service
@Transactional(readOnly = true)
public class RequestService {
    private final RequestRepository repository;
    private final RequestHistoryRepository historyRepository;

    public RequestService(RequestRepository repository, RequestHistoryRepository historyRepository) {
        this.repository = repository;
        this.historyRepository = historyRepository;
    }

    public List<RequestResponse> list(RequestStatus status) {
        var requests = status == null ? repository.findAllByOrderByCreatedAtDescIdDesc()
                : repository.findByStatusOrderByCreatedAtDescIdDesc(status);
        return requests.stream().map(RequestResponse::from).toList();
    }

    public RequestResponse get(long id) {
        return RequestResponse.from(find(id));
    }

    public List<HistoryResponse> history(long id) {
        find(id);
        return historyRepository.findByRequestIdOrderByOccurredAtAscIdAsc(id)
                .stream().map(HistoryResponse::from).toList();
    }

    public RequestStats stats() {
        var counts = new EnumMap<RequestStatus, Long>(RequestStatus.class);
        repository.countByStatus().forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        long fresh = counts.getOrDefault(RequestStatus.NEW, 0L);
        long active = counts.getOrDefault(RequestStatus.IN_PROGRESS, 0L);
        long resolved = counts.getOrDefault(RequestStatus.RESOLVED, 0L);
        return new RequestStats(fresh + active + resolved, fresh, active, resolved);
    }

    public RequestPage page(RequestStatus status, String query, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative; size must be between 1 and 100");
        }
        String term = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (term.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search query must not exceed 200 characters");
        }
        Specification<ServiceRequest> matches = (root, ignored, cb) -> {
            var statusMatch = status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
            if (term.isEmpty()) return statusMatch;
            // LOCATE treats %, _ and backslash as literal characters, unlike LIKE.
            var textMatch = cb.or(cb.greaterThan(cb.locate(cb.lower(root.get("title")), term), 0),
                    cb.greaterThan(cb.locate(cb.lower(root.get("description")), term), 0));
            return cb.and(statusMatch, textMatch);
        };
        var paging = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return RequestPage.from(repository.findAll(matches, paging));
    }

    private ServiceRequest find(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
    }

    @Transactional
    public RequestResponse create(String title, String description) {
        var request = repository.save(new ServiceRequest(title, description));
        historyRepository.save(new RequestHistory(request.getId(), RequestHistory.Action.CREATED,
                null, request.getStatus(), request.getCreatedAt()));
        return RequestResponse.from(request);
    }

    @Transactional
    public RequestResponse changeStatus(long id, RequestStatus status) {
        var request = find(id);
        var previous = request.getStatus();
        request.changeStatus(status);
        repository.flush();
        if (previous != request.getStatus()) {
            historyRepository.save(new RequestHistory(id, RequestHistory.Action.STATUS_CHANGED,
                    previous, request.getStatus(), Instant.now()));
        }
        return RequestResponse.from(request);
    }
}
