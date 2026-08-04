package com.dropai.rewrite;

import com.dropai.rewrite.controller.HealthController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthControllerTests {
    @Test
    void readinessEndpointDoesNotDependOnExternalServices() {
        var response = new HealthController().health();
        assertEquals("ok", response.get("status"));
        assertEquals("dropai", response.get("service"));
    }
}
