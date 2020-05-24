package tech.tgo.ecg.compute;

public class GeolocationResult {
    Boolean processed_ok;
    double residual;
    double residual_rk;
    double lat;
    double lon;
    double elp_long;
    double elp_short;
    double elp_rot;



//    public GeolocationResult(Boolean processed_ok) {
//        this.processed_ok = processed_ok;
//    }

    public double getResidual() {
        return residual;
    }

    public void setResidual(double residual) {
        this.residual = residual;
    }

    public double getResidual_rk() {
        return residual_rk;
    }

    public void setResidual_rk(double residual_rk) {
        this.residual_rk = residual_rk;
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

    public double getElp_long() {
        return elp_long;
    }

    public void setElp_long(double elp_long) {
        this.elp_long = elp_long;
    }

    public double getElp_short() {
        return elp_short;
    }

    public void setElp_short(double elp_short) {
        this.elp_short = elp_short;
    }

    public double getElp_rot() {
        return elp_rot;
    }

    public void setElp_rot(double elp_rot) {
        this.elp_rot = elp_rot;
    }

    public Boolean getProcessed_ok() {
        return processed_ok;
    }

    public void setProcessed_ok(Boolean processed_ok) {
        this.processed_ok = processed_ok;
    }
}
