package github.tilcob.app.listmerging.model;

import java.util.List;

/**
 * Ergebnis einer Validierung inklusive möglicher Probleme.
 */
public record ValidationReport(boolean valid, List<ValidationIssue> issues) {

    public ValidationReport {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static ValidationReport valid() {
        return new ValidationReport(true, List.of());
    }

    public static ValidationReport invalid(List<ValidationIssue> issues) {
        return new ValidationReport(false, issues);
    }

    public boolean hasErrors() {
        return !valid || !issues.isEmpty();
    }
}
