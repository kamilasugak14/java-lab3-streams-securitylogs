package ua.kpi.comsys.streams;

import java.util.Objects;
import java.util.stream.Gatherer;

/**
 * Кастомний статевий (stateful) проміжний оператор для виявлення «сплесків» (burst).
 *
 * <p>Gatherer рахує довжину поточної неперервної серії подій від однієї IP-адреси.
 * Щойно серія досягає порога, IP-адреса передається далі по потоку рівно один раз
 * (подальші події тієї самої серії повторно не повідомляються). Коли надходить подія
 * від іншої IP-адреси, лічильник починається з одиниці.
 *
 * <p>Оператор обробляє елементи по одному і не накопичує потік, тому коректно працює
 * з нескінченними джерелами у поєднанні з {@code limit(...)}.
 */
public final class IpBurstGatherer {

    private IpBurstGatherer() {
    }

    /**
     * Створює Gatherer для заданого порога.
     *
     * @param burstThreshold мінімальна довжина серії від однієї IP-адреси
     * @return Gatherer, що перетворює потік {@link SecurityLog} на потік IP-адрес
     * @throws IllegalArgumentException якщо burstThreshold &lt;= 0
     */
    public static Gatherer<SecurityLog, ?, String> of(int burstThreshold) {
        if (burstThreshold <= 0) {
            throw new IllegalArgumentException("burstThreshold must be greater than 0");
        }
        return Gatherer.<SecurityLog, RunState, String>ofSequential(
                RunState::new,
                (state, log, downstream) -> {
                    int runLength = state.register(log.ipAddress());
                    if (runLength == burstThreshold) {
                        return downstream.push(log.ipAddress());
                    }
                    return true;
                });
    }

    /** Внутрішній стан: поточна IP-адреса та довжина її неперервної серії. */
    private static final class RunState {
        private String currentIp;
        private int runLength;

        int register(String ip) {
            if (runLength > 0 && Objects.equals(currentIp, ip)) {
                runLength++;
            } else {
                currentIp = ip;
                runLength = 1;
            }
            return runLength;
        }
    }
}
