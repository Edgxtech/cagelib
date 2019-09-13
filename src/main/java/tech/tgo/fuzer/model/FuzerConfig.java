package tech.tgo.fuzer.model;

public class FuzerConfig {
    FuzerMode fuzerMode;
    String target;
    String geoId;

    public FuzerMode getFuzerMode() {
        return fuzerMode;
    }

    public void setFuzerMode(FuzerMode fuzerMode) {
        this.fuzerMode = fuzerMode;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getGeoId() {
        return geoId;
    }

    public void setGeoId(String geoId) {
        this.geoId = geoId;
    }
}
