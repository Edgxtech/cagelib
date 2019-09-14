package tech.tgo.fuzer.model;

public class Observation {
    double x;
    double y;
    double range;
    double tdoa;
    double aoa;
    ObservationType observationType;
    String assetId;

    public Observation(String assetId, double y, double x) {
        this.assetId = assetId;
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

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }
}
