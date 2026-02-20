package github.tilcob.app.listmerging.model;

/**
 * Einzelnes Validierungsproblem mit stabilem Fehlercode für UI und Logging.
 */
public record ValidationIssue(String code, String message, String headerName, String details) {

    public static final String HEADER_NOT_DETECTED = "HEADER_NOT_DETECTED";
    public static final String COUNT_MISMATCH = "COUNT_MISMATCH";
    public static final String SUM_MISMATCH = "SUM_MISMATCH";

    public ValidationIssue {
        code = requireNonBlank(code, "code");
        message = requireNonBlank(message, "message");
    }

    public ValidationIssue(String code, String message) {
        this(code, message, null, null);
    }

    public ValidationIssue(String code, String message, String headerName) {
        this(code, message, headerName, null);
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
