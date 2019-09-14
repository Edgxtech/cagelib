package tech.tgo.fuzer.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GeoMission {
    FuzerMode fuzerMode;
    //String target;
    Target target;
    String geoId;
    char latZone;
    int lonZone;
    boolean outputKml;
    String outputKmlFilename;
    boolean outputJson;
    String outputJsonFilename;

    public Map<String,double[]> geoResults;

    //public Map<String,Target> targets;

    //public Object[] devicesUsed;
    //public GeoMissionTab geoMissionTab;
    //public AlgorithmEKF ekf;
    //public TCPGeoServer geoServer;
    //public Hashtable<String,Integer> ekfArrayDeviceIndexes;

    public boolean showCEPs = false;
    public boolean showMeas = false;
    public boolean showGEOs = false;

    public Map<String,Double> measurementMetres = new HashMap<String,Double>();
    public Map<String,List<double[]>> measurementCircles = new HashMap<String,List<double[]>>();
    public Map<String,KMLCircle> measKMLCircles = new HashMap<String,KMLCircle>();

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

    public boolean isOutputJson() {
        return outputJson;
    }

    public void setOutputJson(boolean outputJson) {
        this.outputJson = outputJson;
    }

    public String getOutputJsonFilename() {
        return outputJsonFilename;
    }

    public void setOutputJsonFilename(String outputJsonFilename) {
        this.outputJsonFilename = outputJsonFilename;
    }

//    public Map<String, Target> getTargets() {
//        return targets;
//    }
//
//    public void setTargets(Map<String, Target> targets) {
//        this.targets = targets;
//    }

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
}
