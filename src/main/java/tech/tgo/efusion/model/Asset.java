package tech.tgo.efusion.model;

/**
 * @author Timothy Edge (timmyedge)
 */
public class Asset {
    String id;

    /* Location in lat,lon */
    double[] current_loc;

    public Asset() {

    }

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
