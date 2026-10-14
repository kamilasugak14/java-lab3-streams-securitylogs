package ua.kpi.comsys.streams;

import java.time.LocalDateTime;

/**
 * Запис журналу безпекових подій.
 *
 * @param ipAddress        IP-адреса джерела події
 * @param severityLevel    рівень критичності (наприклад, {@code INFO}, {@code CRITICAL})
 * @param eventDescription опис події
 * @param timestamp        час події
 */
public record SecurityLog(String ipAddress, String severityLevel, String eventDescription,
                          LocalDateTime timestamp) {
}
