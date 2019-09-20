package tech.tgo.fuzer.model;

import java.util.List;

/*
 * Geolocation fusion and tracking, using custom extended kalman filter implementation
 *
 * @author Timothy Edge (timmyedge)
 */
public class Observation {

    /* Unique ID - specified by client, enables lifecycle management of the observation */
    Long id;

    /* Asset UTM Easting */
    double x;

    /* Asset UTM Northing */
    double y;

    /* Measured Range [m] - used ICW ObservationType.RANGE
    /* Alternative, if passing [dBm], rudimentary propagation model may be used as follows: [dBm] to [m]
    /* double d = Math.pow(10,((25-r[i] - 20*Math.log10(2.4*Math.pow(10, 9)) + 147.55)/20));
    /* Note 25 = 20dBm transmitter + 5 dB gain on the receive antenna  [dBm]*/
    double range;

    /* Measured TDOA [s] - used ICW ObservationType.TDOA */
    double tdoa;

    /* Measured AOA [radians] - used ICW ObservationType.AOA
    /* Valid for 0 -> 2pi */
    double aoa;

    /* Observation Type */
    ObservationType observationType;

    /* Asset Id */
    String assetId;

    String assetId_b; // For TDOA only
    double xb; // For TDOA only
    double yb; // For TDOA only

    /* Lat/Lon geometry describing the measurement */
    List<double[]> circleGeometry;
    List<double[]> hyperbolaGeometry;
    List<double[]> lineGeometry;

    public Observation(Long id, String assetId, double y, double x) {
        this.id = id;
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<double[]> getCircleGeometry() {
        return circleGeometry;
    }

    public void setCircleGeometry(List<double[]> circleGeometry) {
        this.circleGeometry = circleGeometry;
    }

    public List<double[]> getHyperbolaGeometry() {
        return hyperbolaGeometry;
    }

    public void setHyperbolaGeometry(List<double[]> hyperbolaGeometry) {
        this.hyperbolaGeometry = hyperbolaGeometry;
    }

    public List<double[]> getLineGeometry() {
        return lineGeometry;
    }

    public void setLineGeometry(List<double[]> lineGeometry) {
        this.lineGeometry = lineGeometry;
    }
}
