package kz.govflow;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "service_requests")
public class ServiceRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.NEW;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Version
    private long version;

    protected ServiceRequest() {}

    public ServiceRequest(String title, String description) {
        this.title = title.strip();
        this.description = description.strip();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public RequestStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void changeStatus(RequestStatus next) {
        if (next == status) return;
        if ((status == RequestStatus.NEW && next == RequestStatus.IN_PROGRESS)
                || (status == RequestStatus.IN_PROGRESS && next == RequestStatus.RESOLVED)) {
            status = next;
            return;
        }
        throw new InvalidTransitionException("Allowed workflow: NEW -> IN_PROGRESS -> RESOLVED");
    }
}
