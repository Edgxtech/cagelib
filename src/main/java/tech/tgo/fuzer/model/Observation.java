package tech.tgo.fuzer.model;

public class Observation {
    double x;
    double y;
    double range;
    double tdoa;
    double aoa;
    ObservationType observationType;

    public Observation(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getRange() {
        return range;
    }

    public void setRange(double range) {
        this.range = range;
    }

    public double getTdoa() {
        return tdoa;
    }

    public void setTdoa(double tdoa) {
        this.tdoa = tdoa;
    }

    public double getAoa() {
        return aoa;
    }

    public void setAoa(double aoa) {
        this.aoa = aoa;
    }

    public ObservationType getObservationType() {
        return observationType;
    }

    public void setObservationType(ObservationType observationType) {
        this.observationType = observationType;
    }
}
