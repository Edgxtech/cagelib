package tech.tgo.efusion.compute;

public class FilterExecution {
    public FilterExecution(Double[] latlon) {
        this.latlon = latlon;
    }
    Double[] latlon;

    public Double[] getLatlon() {
        return latlon;
    }

    public void setLatlon(Double[] latlon) {
        this.latlon = latlon;
    }
}
