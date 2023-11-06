package tech.edgx.cage.compute;

import java.util.List;

public class ComputeResults {
    String geoId;
    GeolocationResult geolocationResult;
    List<GeolocationResult> additionalResults;

    public String getGeoId() {
        return geoId;
    }

    public void setGeoId(String geoId) {
        this.geoId = geoId;
    }

    public GeolocationResult getGeolocationResult() {
        return geolocationResult;
    }

    public void setGeolocationResult(GeolocationResult geolocationResult) {
        this.geolocationResult = geolocationResult;
    }

    public List<GeolocationResult> getAdditionalResults() {
        return additionalResults;
    }

    public void setAdditionalResults(List<GeolocationResult> additionalResults) {
        this.additionalResults = additionalResults;
    }
}
