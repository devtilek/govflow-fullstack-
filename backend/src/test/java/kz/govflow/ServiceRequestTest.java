package kz.govflow;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ServiceRequestTest {
    @Test void resolvedRequestCannotBeReopened() {
        var request = new ServiceRequest("Title", "Description");
        request.changeStatus(RequestStatus.IN_PROGRESS);
        request.changeStatus(RequestStatus.RESOLVED);
        assertThatThrownBy(() -> request.changeStatus(RequestStatus.NEW))
                .isInstanceOf(InvalidTransitionException.class);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.RESOLVED);
    }

    @Test void inProgressRequestCannotMoveBackwards() {
        var request = new ServiceRequest("Title", "Description");
        request.changeStatus(RequestStatus.IN_PROGRESS);
        assertThatThrownBy(() -> request.changeStatus(RequestStatus.NEW))
                .isInstanceOf(InvalidTransitionException.class);
    }
}
