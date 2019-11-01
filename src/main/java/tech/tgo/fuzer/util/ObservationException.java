package tech.tgo.fuzer.util;

/**
 * @author Timothy Edge (timmyedge)
 */
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
