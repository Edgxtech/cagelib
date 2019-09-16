package tech.tgo.fuzer.model;

import java.util.*;

/*
 * Geolocation fusion and tracking, using custom extended kalman filter implementation
 *
 * @author Timothy Edge (timmyedge)
 */
public class GeoMission {
    FuzerMode fuzerMode;
    Target target;
    String geoId;
    char latZone;
    int lonZone;
    boolean outputKml;
    String outputKmlFilename;

    Map<String,Asset> assets = new HashMap<String,Asset>();

    //public Map<String,double[]> geoResults;

    public boolean showCEPs = false;
    public boolean showMeas = false;
    public boolean showGEOs = false;

    public Map<String,List<double[]>> measurementCircles = new HashMap<String,List<double[]>>();
    public Map<String,List<double[]>> measurementHyperbolas = new HashMap<String,List<double[]>>();
    public Map<String,List<double[]>> measurementLines = new HashMap<String,List<double[]>>();

    public FuzerMode getFuzerMode() {
        return fuzerMode;
    }

    public void setFuzerMode(FuzerMode fuzerMode) {
        this.fuzerMode = fuzerMode;
    }

    public Target getTarget() {
        return target;
    }

    public void setTarget(Target target) {
        this.target = target;
    }

    public String getGeoId() {
        return geoId;
    }

    public void setGeoId(String geoId) {
        this.geoId = geoId;
    }

    public char getLatZone() {
        return latZone;
    }

    public void setLatZone(char latZone) {
        this.latZone = latZone;
    }

    public int getLonZone() {
        return lonZone;
    }

    public void setLonZone(int lonZone) {
        this.lonZone = lonZone;
    }

    public boolean isOutputKml() {
        return outputKml;
    }

    public void setOutputKml(boolean outputKml) {
        this.outputKml = outputKml;
    }

    public String getOutputKmlFilename() {
        return outputKmlFilename;
    }

    public void setOutputKmlFilename(String outputKmlFilename) {
        this.outputKmlFilename = outputKmlFilename;
    }

    public boolean isShowCEPs() {
        return showCEPs;
    }

    public void setShowCEPs(boolean showCEPs) {
        this.showCEPs = showCEPs;
    }

    public boolean isShowMeas() {
        return showMeas;
    }

    public void setShowMeas(boolean showMeas) {
        this.showMeas = showMeas;
    }

    public boolean isShowGEOs() {
        return showGEOs;
    }

    public void setShowGEOs(boolean showGEOs) {
        this.showGEOs = showGEOs;
    }

    public Map<String, Asset> getAssets() {
        return assets;
    }

    public void setAssets(Map<String, Asset> assets) {
        this.assets = assets;
    }
}
