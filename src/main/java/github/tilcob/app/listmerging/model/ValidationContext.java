package github.tilcob.app.listmerging.model;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Kontext mit Soll-Werten und Vergleichsparametern für die Merge-Validierung.
 */
public record ValidationContext(Map<String, Integer> expectedRowCounts,
                                Map<String, BigDecimal> expectedSums,
                                BigDecimal sumTolerance,
                                int sumScale) {

    public ValidationContext {
        expectedRowCounts = expectedRowCounts == null ? Map.of() : Map.copyOf(expectedRowCounts);
        expectedSums = expectedSums == null ? Map.of() : Map.copyOf(expectedSums);
        sumTolerance = sumTolerance == null ? new BigDecimal("0.01") : sumTolerance.abs();
        if (sumScale < 0) {
            sumScale = 2;
        }
    }

    public int expectedRowsFor(String headerName) {
        if (headerName == null) {
            return -1;
        }
        return expectedRowCounts.getOrDefault(headerName, -1);
    }

    public BigDecimal expectedSumFor(String headerName) {
        if (headerName == null) {
            return null;
        }
        return expectedSums.get(headerName);
    }
}
