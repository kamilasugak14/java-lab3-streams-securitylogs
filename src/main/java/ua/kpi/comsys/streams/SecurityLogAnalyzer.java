package ua.kpi.comsys.streams;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Аналізатор потоку безпекових подій. Усі методи працюють ліниво і придатні
 * для нескінченних потоків.
 */
public class SecurityLogAnalyzer {

    private static final String CRITICAL = "CRITICAL";

    /**
     * Знаходить першу подію з рівнем {@code CRITICAL}.
     *
     * @param stream потік подій (може бути нескінченним)
     * @return перша критична подія або {@link Optional#empty()}
     * @throws IllegalArgumentException якщо stream == null
     */
    public Optional<SecurityLog> findFirstCriticalEvent(Stream<SecurityLog> stream) {
        requireNonNull(stream, "stream");
        return stream
                .filter(log -> log != null && CRITICAL.equals(log.severityLevel()))
                .findFirst();
    }

    /**
     * Збирає події, доки не настане дедлайн: обробка зупиняється на першій події,
     * час якої не раніше за deadline.
     *
     * @param stream   потік подій (може бути нескінченним)
     * @param deadline момент часу, до якого збираються події (не включно)
     * @return список подій до дедлайну
     * @throws IllegalArgumentException якщо stream або deadline дорівнює null
     */
    public List<SecurityLog> getLogsBeforeDeadline(Stream<SecurityLog> stream,
                                                   LocalDateTime deadline) {
        requireNonNull(stream, "stream");
        requireNonNull(deadline, "deadline");
        return stream
                .filter(log -> log != null && log.timestamp() != null)
                .takeWhile(log -> log.timestamp().isBefore(deadline))
                .toList();
    }

    /**
     * Виявляє IP-адреси, від яких надійшло щонайменше burstThreshold подій поспіль
     * (без подій від інших IP між ними).
     *
     * @param stream         потік подій (може бути нескінченним)
     * @param burstThreshold мінімальна довжина серії
     * @param maxIpsToReport максимальна кількість IP у звіті
     * @return IP-адреси у порядку виявлення сплесків
     * @throws IllegalArgumentException якщо stream == null, burstThreshold &lt;= 0
     *                                  або maxIpsToReport &lt;= 0
     */
    public List<String> detectIpBursts(Stream<SecurityLog> stream, int burstThreshold,
                                       int maxIpsToReport) {
        requireNonNull(stream, "stream");
        if (burstThreshold <= 0) {
            throw new IllegalArgumentException("burstThreshold must be greater than 0");
        }
        if (maxIpsToReport <= 0) {
            throw new IllegalArgumentException("maxIpsToReport must be greater than 0");
        }
        return stream
                .filter(log -> log != null && log.ipAddress() != null)
                .gather(IpBurstGatherer.of(burstThreshold))
                .limit(maxIpsToReport)
                .toList();
    }

    private static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
    }
}
