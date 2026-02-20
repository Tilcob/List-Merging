package github.tilcob.app.listmerging.model;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Kontext mit Soll-Werten und Vergleichsparametern für die Merge-Validierung.
 * <p>
 * Für jeden Headernamen kann ein {@link ExpectedMetrics} hinterlegt werden. Fehlt ein Sollwert für
 * Zeilenanzahl oder Summe, wird das Verhalten über {@code treatMissingExpectationsAsWarning} gesteuert:
 * </p>
 * <ul>
 *   <li>{@code true}: fehlende Sollwerte werden als Warnung betrachtet und nur geloggt; die Validierung
 *   bleibt dadurch nicht automatisch ungültig.</li>
 *   <li>{@code false}: fehlende Sollwerte werden als Fehler behandelt und als {@link ValidationIssue}
 *   in den Report aufgenommen.</li>
 * </ul>
 */
public record ValidationContext(Map<String, ExpectedMetrics> expectedMetricsByHeader,
                                BigDecimal sumTolerance,
                                int sumScale,
                                boolean treatMissingExpectationsAsWarning) {

    public ValidationContext {
        expectedMetricsByHeader = expectedMetricsByHeader == null ? Map.of() : Map.copyOf(expectedMetricsByHeader);
        sumTolerance = sumTolerance == null ? new BigDecimal("0.01") : sumTolerance.abs();
        if (sumScale < 0) {
            sumScale = 2;
        }
    }

    public static ValidationContext defaults() {
        return new ValidationContext(Map.of(), null, 2, true);
    }

    public Optional<ExpectedMetrics> metricsFor(String headerName) {
        if (headerName == null || headerName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(expectedMetricsByHeader.get(headerName));
    }

    public Optional<Integer> expectedRowsFor(String headerName) {
        return metricsFor(headerName).flatMap(ExpectedMetrics::expectedRowCount);
    }

    public Optional<BigDecimal> expectedSumFor(String headerName) {
        return metricsFor(headerName).flatMap(ExpectedMetrics::expectedTotalSum);
    }

    public record ExpectedMetrics(Integer expectedRowCount,
                                  BigDecimal expectedTotalSum) {

        public ExpectedMetrics {
            if (expectedRowCount != null && expectedRowCount < 0) {
                throw new IllegalArgumentException("expectedRowCount must be >= 0");
            }
        }

        public Optional<Integer> expectedRowCount() {
            return Optional.ofNullable(expectedRowCount);
        }

        public Optional<BigDecimal> expectedTotalSum() {
            return Optional.ofNullable(expectedTotalSum);
        }
    }
}
