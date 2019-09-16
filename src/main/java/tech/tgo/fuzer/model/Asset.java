package tech.tgo.fuzer.model;

public class Asset {
    String id;
    double[] current_loc;

    public Asset(String id, double[] current_loc) {
        this.id = id;
        this.current_loc = current_loc;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double[] getCurrent_loc() {
        return current_loc;
    }

    public void setCurrent_loc(double[] current_loc) {
        this.current_loc = current_loc;
    }
}
