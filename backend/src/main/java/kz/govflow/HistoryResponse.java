package kz.govflow;

import java.time.Instant;

public record HistoryResponse(Long id, RequestHistory.Action action, RequestStatus fromStatus,
                              RequestStatus toStatus, Instant occurredAt) {
    public static HistoryResponse from(RequestHistory event) {
        return new HistoryResponse(event.getId(), event.getAction(), event.getFromStatus(),
                event.getToStatus(), event.getOccurredAt());
    }
}
