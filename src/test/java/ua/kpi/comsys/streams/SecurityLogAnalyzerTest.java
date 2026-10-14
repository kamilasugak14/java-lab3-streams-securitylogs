package ua.kpi.comsys.streams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Кожен тест обмежений 2 секундами: якщо метод спробує обробити нескінченний потік
 * повністю, тест впаде за таймаутом, а не зависне.
 */
@Timeout(2)
class SecurityLogAnalyzerTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 1, 12, 0);

    private SecurityLogAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new SecurityLogAnalyzer();
    }

    private static SecurityLog log(String ip, String severity, int minutes) {
        return new SecurityLog(ip, severity, "event-" + minutes, BASE.plusMinutes(minutes));
    }

    private static SecurityLog fromIp(String ip) {
        return log(ip, "INFO", 0);
    }

    @Nested
    class FindFirstCriticalEvent {

        @Test
        void returnsFirstCriticalEventOfFiniteStream() {
            SecurityLog first = log("10.0.0.3", "CRITICAL", 2);
            Optional<SecurityLog> result = analyzer.findFirstCriticalEvent(Stream.of(
                    log("10.0.0.1", "INFO", 0),
                    log("10.0.0.2", "WARNING", 1),
                    first,
                    log("10.0.0.4", "CRITICAL", 3)));
            assertEquals(Optional.of(first), result);
        }

        @Test
        void returnsEmptyWhenNoCriticalEvent() {
            Optional<SecurityLog> result = analyzer.findFirstCriticalEvent(Stream.of(
                    log("10.0.0.1", "INFO", 0), log("10.0.0.2", "ERROR", 1)));
            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEmptyForEmptyStream() {
            assertTrue(analyzer.findFirstCriticalEvent(Stream.empty()).isEmpty());
        }

        @Test
        void findsEventInInfiniteStream() {
            Stream<SecurityLog> infinite = Stream.iterate(0, i -> i + 1)
                    .map(i -> log("10.0.0." + (i % 250), i == 500 ? "CRITICAL" : "INFO", i));

            String description = analyzer.findFirstCriticalEvent(infinite)
                    .map(SecurityLog::eventDescription)
                    .orElse("not found");

            assertEquals("event-500", description);
        }

        @Test
        void stopsConsumingRightAfterTheMatch() {
            AtomicInteger consumed = new AtomicInteger();
            Stream<SecurityLog> infinite = Stream.iterate(0, i -> i + 1)
                    .peek(i -> consumed.incrementAndGet())
                    .map(i -> log("10.0.0.1", i == 7 ? "CRITICAL" : "INFO", i));

            assertTrue(analyzer.findFirstCriticalEvent(infinite).isPresent());
            assertEquals(8, consumed.get());
        }

        @Test
        void matchesSeverityExactly() {
            Optional<SecurityLog> result = analyzer.findFirstCriticalEvent(Stream.of(
                    log("10.0.0.1", "critical", 0), log("10.0.0.2", null, 1)));
            assertTrue(result.isEmpty());
        }

        @Test
        void skipsNullElements() {
            SecurityLog critical = log("10.0.0.1", "CRITICAL", 1);
            Optional<SecurityLog> result = analyzer.findFirstCriticalEvent(
                    Stream.of(null, critical));
            assertEquals(Optional.of(critical), result);
        }

        @Test
        void throwsOnNullStream() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> analyzer.findFirstCriticalEvent(null));
            assertEquals("stream cannot be null", ex.getMessage());
        }
    }

    @Nested
    class GetLogsBeforeDeadline {

        @Test
        void collectsEventsBeforeDeadlineFromInfiniteStream() {
            Stream<SecurityLog> infinite = Stream.iterate(0, i -> i + 1)
                    .map(i -> log("10.0.0.1", "INFO", i));

            List<SecurityLog> result = analyzer.getLogsBeforeDeadline(infinite, BASE.plusMinutes(5));

            assertEquals(5, result.size());
            assertEquals(BASE, result.getFirst().timestamp());
            assertEquals(BASE.plusMinutes(4), result.getLast().timestamp());
        }

        @Test
        void excludesEventExactlyAtDeadline() {
            List<SecurityLog> result = analyzer.getLogsBeforeDeadline(
                    Stream.of(log("a", "INFO", 1), log("a", "INFO", 2), log("a", "INFO", 3)),
                    BASE.plusMinutes(3));
            assertEquals(2, result.size());
        }

        @Test
        void returnsAllEventsWhenAllAreBeforeDeadline() {
            List<SecurityLog> result = analyzer.getLogsBeforeDeadline(
                    Stream.of(log("a", "INFO", 1), log("b", "INFO", 2)), BASE.plusDays(1));
            assertEquals(2, result.size());
        }

        @Test
        void returnsEmptyWhenFirstEventIsAfterDeadline() {
            List<SecurityLog> result = analyzer.getLogsBeforeDeadline(
                    Stream.of(log("a", "INFO", 10)), BASE.plusMinutes(5));
            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEmptyForEmptyStream() {
            assertTrue(analyzer.getLogsBeforeDeadline(Stream.empty(), BASE).isEmpty());
        }

        @Test
        void stopsAtFirstEventAfterDeadlineEvenIfLaterEventsAreEarlier() {
            List<SecurityLog> result = analyzer.getLogsBeforeDeadline(
                    Stream.of(log("a", "INFO", 0), log("a", "INFO", 1),
                            log("a", "INFO", 10), log("a", "INFO", 2)),
                    BASE.plusMinutes(5));
            assertEquals(2, result.size());
        }

        @Test
        void consumesOnlyOneElementPastTheDeadline() {
            AtomicInteger consumed = new AtomicInteger();
            Stream<SecurityLog> infinite = Stream.iterate(0, i -> i + 1)
                    .peek(i -> consumed.incrementAndGet())
                    .map(i -> log("10.0.0.1", "INFO", i));

            analyzer.getLogsBeforeDeadline(infinite, BASE.plusMinutes(5));

            assertEquals(6, consumed.get());
        }

        @Test
        void skipsNullElementsAndEventsWithoutTimestamp() {
            List<SecurityLog> result = analyzer.getLogsBeforeDeadline(
                    Stream.of(log("a", "INFO", 1), null,
                            new SecurityLog("b", "INFO", "no time", null), log("c", "INFO", 2)),
                    BASE.plusMinutes(5));
            assertEquals(2, result.size());
        }

        @Test
        void throwsOnNullStream() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> analyzer.getLogsBeforeDeadline(null, BASE));
            assertEquals("stream cannot be null", ex.getMessage());
        }

        @Test
        void throwsOnNullDeadline() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> analyzer.getLogsBeforeDeadline(Stream.empty(), null));
            assertEquals("deadline cannot be null", ex.getMessage());
        }
    }

    @Nested
    class DetectIpBursts {

        @Test
        void reportsIpWithEnoughConsecutiveEvents() {
            List<String> result = analyzer.detectIpBursts(Stream.of(
                    fromIp("A"), fromIp("A"), fromIp("A"),
                    fromIp("B"), fromIp("B"),
                    fromIp("A")), 3, 10);
            assertEquals(List.of("A"), result);
        }

        @Test
        void reportsLongRunOnlyOnce() {
            List<String> result = analyzer.detectIpBursts(Stream.of(
                    fromIp("A"), fromIp("A"), fromIp("A"), fromIp("A"), fromIp("A")), 3, 10);
            assertEquals(List.of("A"), result);
        }

        @Test
        void interruptedRunDoesNotCount() {
            List<String> result = analyzer.detectIpBursts(Stream.of(
                    fromIp("A"), fromIp("A"), fromIp("B"), fromIp("A"), fromIp("A")), 3, 10);
            assertTrue(result.isEmpty());
        }

        @Test
        void separateRunsOfTheSameIpAreReportedSeparately() {
            List<String> result = analyzer.detectIpBursts(Stream.of(
                    fromIp("A"), fromIp("A"), fromIp("A"), fromIp("B"),
                    fromIp("A"), fromIp("A"), fromIp("A")), 3, 10);
            assertEquals(List.of("A", "A"), result);
        }

        @Test
        void thresholdOfOneReportsEveryNewRun() {
            List<String> result = analyzer.detectIpBursts(Stream.of(
                    fromIp("A"), fromIp("B"), fromIp("B"), fromIp("C")), 1, 10);
            assertEquals(List.of("A", "B", "C"), result);
        }

        @Test
        void limitsNumberOfReportedIps() {
            List<String> result = analyzer.detectIpBursts(Stream.of(
                    fromIp("A"), fromIp("A"), fromIp("B"), fromIp("B"),
                    fromIp("C"), fromIp("C")), 2, 2);
            assertEquals(List.of("A", "B"), result);
        }

        @Test
        void returnsFewerIpsWhenThereAreNotEnoughBursts() {
            List<String> result = analyzer.detectIpBursts(Stream.of(
                    fromIp("A"), fromIp("A"), fromIp("B")), 2, 5);
            assertEquals(List.of("A"), result);
        }

        @Test
        void returnsEmptyListForEmptyStream() {
            assertTrue(analyzer.detectIpBursts(Stream.empty(), 2, 5).isEmpty());
        }

        @Test
        void detectsBurstsInInfiniteStream() {
            Stream<SecurityLog> infinite = Stream.iterate(0, i -> i + 1)
                    .map(i -> fromIp("10.0.0." + (i / 3)));

            List<String> result = analyzer.detectIpBursts(infinite, 3, 5);

            assertEquals(List.of("10.0.0.0", "10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4"), result);
        }

        @Test
        void infiniteStreamOfOneIpProducesSingleBurstAndStopsAtLimit() {
            Stream<SecurityLog> infinite = Stream.generate(() -> fromIp("10.0.0.9"));
            // Сплеск у єдиній нескінченній серії один, тому ліміт 1 має зупинити обробку
            assertEquals(List.of("10.0.0.9"), analyzer.detectIpBursts(infinite, 4, 1));
        }

        @Test
        void stopsConsumingOnceLimitIsReached() {
            AtomicInteger consumed = new AtomicInteger();
            Stream<SecurityLog> infinite = Stream.iterate(0, i -> i + 1)
                    .peek(i -> consumed.incrementAndGet())
                    .map(i -> fromIp("10.0.0." + (i / 3)));

            List<String> result = analyzer.detectIpBursts(infinite, 3, 2);

            assertEquals(2, result.size());
            assertTrue(consumed.get() <= 7, "consumed " + consumed.get() + " elements");
        }

        @Test
        void skipsNullElementsAndEventsWithoutIp() {
            List<String> result = analyzer.detectIpBursts(Arrays.asList(
                    fromIp("A"), null, fromIp("A"),
                    new SecurityLog(null, "INFO", "no ip", BASE), fromIp("A")).stream(), 3, 5);
            assertEquals(List.of("A"), result);
        }

        @Test
        void throwsOnNullStream() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> analyzer.detectIpBursts(null, 3, 5));
            assertEquals("stream cannot be null", ex.getMessage());
        }

        @Test
        void throwsOnNonPositiveThreshold() {
            for (int threshold : new int[] {0, -1, Integer.MIN_VALUE}) {
                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                        () -> analyzer.detectIpBursts(Stream.empty(), threshold, 5));
                assertEquals("burstThreshold must be greater than 0", ex.getMessage());
            }
        }

        @Test
        void throwsOnNonPositiveMaxIps() {
            for (int max : new int[] {0, -1, Integer.MIN_VALUE}) {
                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                        () -> analyzer.detectIpBursts(Stream.empty(), 3, max));
                assertEquals("maxIpsToReport must be greater than 0", ex.getMessage());
            }
        }

        @Test
        void doesNotConsumeStreamWhenArgumentsAreInvalid() {
            AtomicInteger consumed = new AtomicInteger();
            Stream<SecurityLog> stream = Stream.of(fromIp("A")).peek(l -> consumed.incrementAndGet());
            assertThrows(IllegalArgumentException.class, () -> analyzer.detectIpBursts(stream, 0, 1));
            assertEquals(0, consumed.get());
        }
    }
}
