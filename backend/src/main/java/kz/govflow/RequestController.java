package kz.govflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/requests")
public class RequestController {
    private final RequestService service;

    public RequestController(RequestService service) { this.service = service; }

    @GetMapping
    public List<RequestResponse> list(@RequestParam(required = false) RequestStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    public RequestResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping("/page")
    public RequestPage page(@RequestParam(required = false) RequestStatus status,
                            @RequestParam(defaultValue = "") String q,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "20") int size) {
        return service.page(status, q, page, size);
    }

    @GetMapping("/stats")
    public RequestStats stats() {
        return service.stats();
    }

    @PostMapping
    public ResponseEntity<RequestResponse> create(@Valid @RequestBody CreateRequest body) {
        var result = service.create(body.title(), body.description());
        return ResponseEntity.created(URI.create("/api/requests/" + result.id())).body(result);
    }

    @PatchMapping("/{id}/status")
    public RequestResponse changeStatus(@PathVariable long id, @Valid @RequestBody ChangeStatus body) {
        return service.changeStatus(id, body.status());
    }

    public record CreateRequest(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 2000) String description) {}

    public record ChangeStatus(@NotNull RequestStatus status) {}
}
