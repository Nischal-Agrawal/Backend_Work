package com.syfe.personalfinancemanager;

import com.syfe.personalfinancemanager.controller.HealthController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthControllerTest {

    @Test
    @DisplayName("Unit test: health check returns UP")
    void testHealthCheck() {
        HealthController controller = new HealthController();
        ResponseEntity<Map<String, String>> response = controller.health();
        assertEquals(200, response.getStatusCode().value());
        assertEquals("UP", response.getBody().get("status"));
    }
}
