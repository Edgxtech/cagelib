package tech.edgx.cage.model;

import java.util.List;

public class Observation {

    /* Unique ID - specified by client, enables lifecycle management of the observation */
    Long id;

    /* Asset lat */
    double lat;

    /* Asset lon */
    double lon;

    /* Asset UTM Easting */
    double x;

    /* Asset UTM Northing */
    double y;

    /* UTM reference zones */
    int x_lonZone;
    char y_latZone;

    /* Either: Range, TDOA or AOA measurement
    /* Measured Range [m] - used ICW ObservationType.RANGE
    /*          Alternative, if passing [dBm], rudimentary propagation model may be used as follows: [dBm] to [m]
    /*          double d = Math.pow(10,((25-r[i] - 20*Math.log10(2.4*Math.pow(10, 9)) + 147.55)/20));
    /*          Note 25 = 20dBm transmitter + 5 dB gain on the receive antenna  [dBm]
    /* Measured TDOA [s] - used ICW ObservationType.TDOA
    /* Measured AOA [radians] - used ICW ObservationType.AOA
    /*          Valid for 0 -> 2pi */
    Double meas;

    Double meas_prev;

    /* Observation Type */
    /* Measured Range [m] - used ICW ObservationType.RANGE
    /*      Alternative, if passing [dBm], rudimentary propagation model may be used as follows: [dBm] to [m]
    /*      double d = Math.pow(10,((25-r[i] - 20*Math.log10(2.4*Math.pow(10, 9)) + 147.55)/20));
    /*      Note 25 = 20dBm transmitter + 5 dB gain on the receive antenna  [dBm]
    /* Measured TDOA [s] - used ICW ObservationType.TDOA
    /* Measured AOA [radians] - used ICW ObservationType.AOA
    /*      Valid for 0 -> 2pi */
    ObservationType observationType;

    /* Asset Id */
    String assetId;

    String assetId_b; // For TDOA only
    double xb; // For TDOA only
    double yb; // For TDOA only
    double lat_b; // For TDOA only
    double lon_b; // For TDOA only

    /* Lat/Lon geometry describing the measurement */
    List<double[]> circleGeometry;
    List<double[]> hyperbolaGeometry;
    List<double[]> lineGeometry;

    Boolean crossed_border;

    /* Should be capped at no more than the rand_factor, expressed in utm units
     * May expect sensors to report dynamic measurement errors, otherwise defaults
     * in application.properties should be used.
     * units are in utm. [1 utm equates to 10000m].
     * Guide: TDOA: 0.000005 [ms] == 1500 DDOA [m] == 0.15 [utm]
     * Guide: AOA: 0.2 [radians] == 202.71[m] == 0.020271 [utm] (Assuming AOA are to targets @1000m; val = tan(0.2)*1000)
     * Guide: Range: 100 [m] == 0.01 [utm] */
    Double meas_error;

    public Observation(Long id, String assetId, double lat, double lon) {
        this.id = id;
        this.assetId = assetId;
        this.lon = lon;
        this.lat = lat;
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

    public int getX_lonZone() {
        return x_lonZone;
    }

    public void setX_lonZone(int x_lonZone) {
        this.x_lonZone = x_lonZone;
    }

    public char getY_latZone() {
        return y_latZone;
    }

    public void setY_latZone(char y_latZone) {
        this.y_latZone = y_latZone;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public double getLat_b() {
        return lat_b;
    }

    public void setLat_b(double lat_b) {
        this.lat_b = lat_b;
    }

    public double getLon_b() {
        return lon_b;
    }

    public void setLon_b(double lon_b) {
        this.lon_b = lon_b;
    }

    public Double getMeas() {
        return meas;
    }

    public void setMeas(Double meas) {
        this.meas = meas;
    }

    public Double getMeas_prev() {
        return meas_prev;
    }

    public void setMeas_prev(Double meas_prev) {
        this.meas_prev = meas_prev;
    }

    public Boolean getCrossed_border() {
        return crossed_border;
    }

    public void setCrossed_border(Boolean crossed_border) {
        this.crossed_border = crossed_border;
    }

    public Double getMeas_error() {
        return meas_error;
    }

    public void setMeas_error(Double meas_error) {
        this.meas_error = meas_error;
    }
}
