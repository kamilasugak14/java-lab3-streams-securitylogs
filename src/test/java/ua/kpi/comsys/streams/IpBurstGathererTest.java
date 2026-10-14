package ua.kpi.comsys.streams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Gatherer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(2)
class IpBurstGathererTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 1, 1, 12, 0);

    private static SecurityLog fromIp(String ip) {
        return new SecurityLog(ip, "INFO", "event", TIME);
    }

    @Test
    void emitsIpOnceWhenRunReachesThreshold() {
        List<String> result = Stream.of(fromIp("A"), fromIp("A"), fromIp("A"), fromIp("A"))
                .gather(IpBurstGatherer.of(2))
                .toList();
        assertEquals(List.of("A"), result);
    }

    @Test
    void emitsNothingBelowThreshold() {
        List<String> result = Stream.of(fromIp("A"), fromIp("B"), fromIp("A"), fromIp("B"))
                .gather(IpBurstGatherer.of(2))
                .toList();
        assertTrue(result.isEmpty());
    }

    @Test
    void eachUseStartsWithFreshState() {
        Gatherer<SecurityLog, ?, String> gatherer = IpBurstGatherer.of(2);
        List<String> first = Stream.of(fromIp("A")).gather(gatherer).toList();
        List<String> second = Stream.of(fromIp("A")).gather(gatherer).toList();
        assertTrue(first.isEmpty());
        assertTrue(second.isEmpty());
    }

    @Test
    void keepsEncounterOrderInParallelStream() {
        List<String> result = Stream.of(fromIp("A"), fromIp("A"), fromIp("B"), fromIp("B"),
                        fromIp("C"), fromIp("C"))
                .parallel()
                .gather(IpBurstGatherer.of(2))
                .toList();
        assertEquals(List.of("A", "B", "C"), result);
    }

    @Test
    void worksOnInfiniteStreamWithLimit() {
        List<String> result = Stream.iterate(0, i -> i + 1)
                .map(i -> fromIp("10.0.0." + (i / 2)))
                .gather(IpBurstGatherer.of(2))
                .limit(3)
                .toList();
        assertEquals(List.of("10.0.0.0", "10.0.0.1", "10.0.0.2"), result);
    }

    @Test
    void rejectsNonPositiveThreshold() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> IpBurstGatherer.of(0));
        assertEquals("burstThreshold must be greater than 0", ex.getMessage());
        assertThrows(IllegalArgumentException.class, () -> IpBurstGatherer.of(-5));
    }
}
