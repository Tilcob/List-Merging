package github.tilcob.app.listmerging.model;

import java.util.List;

/**
 * Result of a validation run including potential issues.
 */
public record ValidationReport(boolean valid, List<ValidationIssue> issues) {

    public ValidationReport {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static ValidationReport success() {
        return new ValidationReport(true, List.of());
    }

    public static ValidationReport failure(List<ValidationIssue> issues) {
        return new ValidationReport(false, issues);
    }

    public boolean hasErrors() {
        return !valid || !issues.isEmpty();
    }
}
