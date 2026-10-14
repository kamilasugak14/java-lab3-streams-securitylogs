# Security Log Streams (нескінченні потоки, Optional, Gatherers)

Лабораторна робота №3 з дисципліни «Технології розробки на платформі Java»
(КПІ ім. Ігоря Сікорського).

**Тема:** опрацювання нескінченних послідовностей даних за допомогою ледачих (lazy)
операцій Java Stream API, безпечне керування наявністю значень через `Optional` та
створення кастомних проміжних операцій за допомогою Stream Gatherers.

## Опис проєкту

Сервіс аналізує потік безпекових подій `Stream<SecurityLog>`, який може бути
нескінченним. Усі класи розташовані в пакеті `ua.kpi.comsys.streams`.

| Клас | Призначення |
|------|-------------|
| `SecurityLog` | `record` з полями `ipAddress`, `severityLevel`, `eventDescription`, `timestamp` |
| `SecurityLogAnalyzer` | сервіс з трьома методами аналізу потоку |
| `IpBurstGatherer` | кастомний `Gatherer` для виявлення сплесків подій від однієї IP |

### Методи `SecurityLogAnalyzer`

| Метод | Опис |
|-------|------|
| `Optional<SecurityLog> findFirstCriticalEvent(Stream<SecurityLog>)` | перша подія з рівнем `CRITICAL` (`filter` + `findFirst`, коротке замикання) |
| `List<SecurityLog> getLogsBeforeDeadline(Stream<SecurityLog>, LocalDateTime)` | події, доки не настане дедлайн (`takeWhile`) |
| `List<String> detectIpBursts(Stream<SecurityLog>, int, int)` | IP-адреси зі сплесками подій (`gather` + `limit`) |

### Поведінка та припущення

- Якщо `stream`, `deadline` або інший обов'язковий аргумент дорівнює `null`, кидається
  `IllegalArgumentException`. Для `detectIpBursts` також кидається
  `IllegalArgumentException`, якщо `burstThreshold <= 0` або `maxIpsToReport <= 0`.
- `findFirstCriticalEvent` порівнює рівень критичності точно з рядком `CRITICAL`
  і повертає `Optional.empty()`, якщо такої події немає. Метод `Optional.get()` не
  використовується.
- `getLogsBeforeDeadline` зупиняється на першій події, час якої не раніше за `deadline`
  (подія рівно в момент `deadline` не включається).
- `detectIpBursts`: серія - це події від однієї IP-адреси поспіль. Коли довжина серії
  досягає `burstThreshold`, IP додається до звіту один раз (довша серія повторно
  не повідомляється). Дві окремі серії однієї IP дають два записи. Обробка
  зупиняється після `maxIpsToReport` IP-адрес.
- Елементи `null` пропускаються. Події без `timestamp` пропускаються в
  `getLogsBeforeDeadline`, події без `ipAddress` - в `detectIpBursts`.

### Кастомний Gatherer

`IpBurstGatherer` створюється через `Gatherer.ofSequential(...)`. Його стан - поточна
IP-адреса та довжина її неперервної серії. Для кожного елемента Gatherer оновлює
стан і передає IP далі по потоку, коли серія досягає порога. Оператор не накопичує
потік, тому разом із `limit(...)` працює з нескінченними джерелами.

## Варіант

Виконано **варіант 2** (обробка безпекових подій, `SecurityLogAnalyzer`).
Варіант обрано за погодженням з викладачем.

## Gradle та Version Catalog

- Збірка налаштована у `build.gradle.kts`, ім'я проєкту - у `settings.gradle.kts`.
- Версії залежностей винесені в `gradle/libs.versions.toml`
  (JUnit 5 підключається через BOM `org.junit:junit-bom`, версія JaCoCo - також з каталогу).
- Використовується Gradle Wrapper (Gradle 9.1.0), окремо встановлювати Gradle не потрібно.

## Вимоги

- **JDK 25** (`java.util.stream.Gatherer` стабільний починаючи з Java 24;
  Gradle 9.1+ підтримує Java 25).
  Встановлення на Windows: `winget install EclipseAdoptium.Temurin.25.JDK`
- Інтернет під час першого запуску (Wrapper завантажує Gradle, Gradle - залежності)

## Збірка та запуск тестів

Windows (PowerShell або cmd):

```
.\gradlew.bat build
.\gradlew.bat test
```

Linux / macOS:

```
./gradlew build
./gradlew test
```

Звіт про результати тестів: `build/reports/tests/test/index.html`.
Звіт про покриття (JaCoCo): `build/reports/jacoco/test/html/index.html`.

## Тести

Тести написані на JUnit 5. Кожен тестовий клас, що працює з потоками, має
`@Timeout(2)`: якщо метод спробує обробити нескінченний потік повністю, тест упаде
за таймаутом, а не зависне. Нескінченні джерела створюються через `Stream.iterate(...)`
та `Stream.generate(...)`. Ліниве виконання перевіряється лічильником елементів у
`peek(...)` (метод споживає рівно стільки елементів, скільки потрібно).

## Структура

```
build.gradle.kts
settings.gradle.kts
gradle/libs.versions.toml
gradle/wrapper/
src/main/java/ua/kpi/comsys/streams/
    SecurityLog.java
    SecurityLogAnalyzer.java
    IpBurstGatherer.java
src/test/java/ua/kpi/comsys/streams/
    SecurityLogTest.java
    SecurityLogAnalyzerTest.java
    IpBurstGathererTest.java
```

## Автор

Сугак Каміла Романівна, група ІП-42
