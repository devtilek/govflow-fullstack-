package kz.govflow;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "request_history")
public class RequestHistory {
    public enum Action { CREATED, STATUS_CHANGED, IMPORTED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long requestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false, length = 20)
    private RequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private RequestStatus toStatus;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    protected RequestHistory() {}

    public RequestHistory(Long requestId, Action action, RequestStatus fromStatus,
                          RequestStatus toStatus, Instant occurredAt) {
        this.requestId = requestId;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public Action getAction() { return action; }
    public RequestStatus getFromStatus() { return fromStatus; }
    public RequestStatus getToStatus() { return toStatus; }
    public Instant getOccurredAt() { return occurredAt; }
}
