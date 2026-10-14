package ua.kpi.comsys.streams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SecurityLogTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Test
    void exposesAllComponents() {
        SecurityLog log = new SecurityLog("10.0.0.1", "INFO", "Login", TIME);
        assertEquals("10.0.0.1", log.ipAddress());
        assertEquals("INFO", log.severityLevel());
        assertEquals("Login", log.eventDescription());
        assertEquals(TIME, log.timestamp());
    }

    @Test
    void equalsIsValueBased() {
        SecurityLog first = new SecurityLog("10.0.0.1", "INFO", "Login", TIME);
        SecurityLog second = new SecurityLog("10.0.0.1", "INFO", "Login", TIME);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, new SecurityLog("10.0.0.2", "INFO", "Login", TIME));
    }
}
