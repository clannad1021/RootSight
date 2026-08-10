package kg.edu.nagisa.rootsight.common.exception;

public class DiagnosisUnavailableException extends RuntimeException {

    public DiagnosisUnavailableException(String message) {
        super(message);
    }

    public DiagnosisUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
