package github.tilcob.app.listmerging.model;

import java.math.BigDecimal;

public record AggregationResult(int rowCount, BigDecimal sumValue) {
    public AggregationResult {
        if (sumValue == null) {
            sumValue = BigDecimal.ZERO;
        }
    }

    public AggregationResult add(AggregationResult other) {
        return new AggregationResult(rowCount + other.rowCount, sumValue.add(other.sumValue));
    }
}
