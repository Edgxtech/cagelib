package tech.tgo.fuzer.model;

public class Target {
    String id;
    String name;
    double[] current_loc;
    double current_cep;
    Double[] true_current_loc;

    public Target(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double[] getCurrent_loc() {
        return current_loc;
    }

    public void setCurrent_loc(double[] current_loc) {
        this.current_loc = current_loc;
    }

    public double getCurrent_cep() {
        return current_cep;
    }

    public void setCurrent_cep(double current_cep) {
        this.current_cep = current_cep;
    }

    public Double[] getTrue_current_loc() {
        return true_current_loc;
    }

    public void setTrue_current_loc(Double[] true_current_loc) {
        this.true_current_loc = true_current_loc;
    }
}
