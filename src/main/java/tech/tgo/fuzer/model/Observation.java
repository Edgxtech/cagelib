package tech.tgo.fuzer.model;

/*
 * Geolocation fusion and tracking, using custom extended kalman filter implementation
 *
 * Filter operates in UTM coordinates
 *
 * @author Timothy Edge (timmyedge)
 */
public class Observation {
    double x;
    double y;
    double range;
    double tdoa;
    /* Valid for 0 -> 2pi */
    double aoa;
    ObservationType observationType;
    String assetId;

    String assetId_b; // For TDOA only
    double xb; // For TDOA only
    double yb; // For TDOA only

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

    public double getXb() {
        return xb;
    }

    public void setXb(double xb) {
        this.xb = xb;
    }

    public double getYb() {
        return yb;
    }

    public void setYb(double yb) {
        this.yb = yb;
    }

    public String getAssetId_b() {
        return assetId_b;
    }

    public void setAssetId_b(String assetId_b) {
        this.assetId_b = assetId_b;
    }
}
