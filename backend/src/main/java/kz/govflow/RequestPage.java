package kz.govflow;

import java.util.List;
import org.springframework.data.domain.Page;

public record RequestPage(List<RequestResponse> items, int page, int size, long totalElements, int totalPages) {
    public static RequestPage from(Page<ServiceRequest> result) {
        return new RequestPage(result.getContent().stream().map(RequestResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
