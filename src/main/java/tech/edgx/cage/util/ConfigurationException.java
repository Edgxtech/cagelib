package tech.edgx.cage.util;

public class ConfigurationException extends Exception {

    public String message;

    public ConfigurationException(String message) {
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
