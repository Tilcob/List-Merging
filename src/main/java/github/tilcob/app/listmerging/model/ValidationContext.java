package github.tilcob.app.listmerging.model;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Context containing expected values and comparison parameters for merge validation.
 * <p>
 * An {@link ExpectedMetrics} entry can be stored for each header name. If an expected row count or
 * expected sum is missing, behavior is controlled by {@code treatMissingExpectationsAsWarning}:
 * </p>
 * <ul>
 *   <li>{@code true}: missing expectations are treated as warnings and only logged; validation does
 *  not automatically become invalid.</li>
 *  <li>{@code false}: missing expectations are treated as errors and added as {@link ValidationIssue}
 *  entries to the report.</li>
 * </ul>
 */
public record ValidationContext(Map<String, ExpectedMetrics> expectedMetricsByHeader,
                                BigDecimal sumTolerance,
                                int sumScale,
                                boolean treatMissingExpectationsAsWarning,
                                boolean enableReferenceAggregation) {

    public ValidationContext {
        expectedMetricsByHeader = expectedMetricsByHeader == null ? Map.of() : Map.copyOf(expectedMetricsByHeader);
        sumTolerance = sumTolerance == null ? new BigDecimal("0.01") : sumTolerance.abs();
        if (sumScale < 0) {
            sumScale = 2;
        }
    }

    public static ValidationContext defaults() {
        return new ValidationContext(Map.of(), null, 2, true, false);
    }

    public Optional<ExpectedMetrics> metricsFor(String headerName) {
        if (headerName == null || headerName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(expectedMetricsByHeader.get(headerName));
    }

    public Optional<Integer> expectedRowsFor(String headerName) {
        return metricsFor(headerName).flatMap(metrics -> Optional.ofNullable(metrics.expectedRowCount()));
    }

    public Optional<BigDecimal> expectedSumFor(String headerName) {
        return metricsFor(headerName).flatMap(metrics -> Optional.ofNullable(metrics.expectedTotalSum()));
    }

    public record ExpectedMetrics(Integer expectedRowCount,
                                  BigDecimal expectedTotalSum) {

        public ExpectedMetrics {
            if (expectedRowCount != null && expectedRowCount < 0) {
                throw new IllegalArgumentException("expectedRowCount must be >= 0");
            }
        }
    }
}
