package kz.govflow;

import java.time.Instant;

public record RequestResponse(Long id, String title, String description, RequestStatus status, Instant createdAt) {
    public static RequestResponse from(ServiceRequest request) {
        return new RequestResponse(request.getId(), request.getTitle(), request.getDescription(),
                request.getStatus(), request.getCreatedAt());
    }
}
