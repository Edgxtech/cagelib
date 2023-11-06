package tech.edgx.cage.util;

public class ObservationException extends Exception {

    public String message;

    public ObservationException(String message) {
        this.message = message;
    }
    @Override
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
