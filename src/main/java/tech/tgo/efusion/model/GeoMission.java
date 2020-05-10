package tech.tgo.efusion.model;

import tech.tgo.efusion.compute.ComputeResults;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Timothy Edge (timmyedge)
 */
public class GeoMission {

    /* required - fix or track */
    MissionMode missionMode;

    /* required - name and id */
    Target target;

    /* required */
    String geoId;

    /* Set internally using measurements */
    char latZone;
    int lonZone;

    /* Settings if choosing for fuzer to output to KML
    /*  - Switch
    /*  - Output filename
    /*  - Which artefacts to show: Geo result, measurements and CEPs */
    Boolean outputKml = false;
    Boolean outputFilterState = false;
    String outputKmlFilename = null;
    String outputFilterStateKmlFilename = null;
    Boolean showCEPs = false;
    Boolean showMeas = false;
    Boolean showGEOs = false;
    Boolean showTrueLoc = false;

    /* Memory store of assets contributing to the mission */
    Map<String,Asset> assets = new HashMap<String,Asset>();

    /* Memory store of observations contributing to the mission - updated dynamically for track missions */
    public Map<Long,Observation> observations = new ConcurrentHashMap<Long,Observation>();

    /* Memory store of calculated observation geometries for plotting */
    public Set<Long> circlesToShow = new HashSet<Long>();
    public Set<Long> hyperbolasToShow = new HashSet<Long>();
    public Set<Long> linesToShow = new HashSet<Long>();

    /* Used only to indicate when a fix mode mission has just put out it's last result */
    Boolean finished;

    /* Library default properties*/
    public Properties properties;

    /* Optional to override default - Period [ms] in which to dispatch filter location result for usage - Default: 1000[ms] */
    public Long dispatchResultsPeriod;

    /* Optional - Period [m] in which to throttle filter iterations (only to be used to slow down computation - Default: Null/Open throttle */
    public Long filterThrottle;

    /* Optional to override default - filter summative residual state error threshold used by fix mode runs only - Default: 0.01 */
    public Double filterConvergenceResidualThreshold;

    /* Optional to override default - filter summative residual state error threshold used to decide on dispatch or not - Default: 0.1. Guide [0.1 -> 5.0]. Higher values provides more insight into filter while converging */
    public Double filterDispatchResidualThreshold;

    /* Optional to override default - filter measurement error parameter (Rk) - Default: 0.1. Smaller for trusted measurements, Guide: {0.01 -> 0.1} */
    public Double filterMeasurementError;

    /* Optional override - i.e. geoMission.setFilterProcessNoise(new double[][]{{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 0.0001, 0}, {0, 0, 0 ,0.0001}}); */
    public double[][] filterProcessNoise;

    /* NOTE: purely for R&D, these should NOT be used as they unreliably skew results */
    public Double filterAOABias;

    public Double filterTDOABias;

    public Double filterRangeBias;

    public InitialStateMode initialStateMode;

    /* Allow specific conditions, otherwise default uses random conditions geographically nearby observing assets */
    //Boolean filterUseSpecificInitialCondition = null;  DEPRECATE IN FAVOUR OF INITIALSTATEMODES enum
    // Options
    Double filterSpecificInitialLat = null;
    Double filterSpecificInitialLon = null;

    /* 0=TR, 1=BR, 2=BL, 3=TL */
    //InitialStateBoxCorner filterSpecificInitialBoxCorner = null;

    Long maxFilterIterations = null;

    ComputeResults computeResults = null;

    public MissionMode getMissionMode() {
        return missionMode;
    }

    public void setMissionMode(MissionMode missionMode) {
        this.missionMode = missionMode;
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

    public String getOutputKmlFilename() {
        return outputKmlFilename;
    }

    public void setOutputKmlFilename(String outputKmlFilename) {
        this.outputKmlFilename = outputKmlFilename;
    }

    public Boolean getOutputKml() {
        return outputKml;
    }

    public void setOutputKml(Boolean outputKml) {
        this.outputKml = outputKml;
    }

    public Boolean getShowCEPs() {
        return showCEPs;
    }

    public void setShowCEPs(Boolean showCEPs) {
        this.showCEPs = showCEPs;
    }

    public Boolean getShowMeas() {
        return showMeas;
    }

    public void setShowMeas(Boolean showMeas) {
        this.showMeas = showMeas;
    }

    public Boolean getShowGEOs() {
        return showGEOs;
    }

    public void setShowGEOs(Boolean showGEOs) {
        this.showGEOs = showGEOs;
    }

    public Boolean getShowTrueLoc() {
        return showTrueLoc;
    }

    public void setShowTrueLoc(Boolean showTrueLoc) {
        this.showTrueLoc = showTrueLoc;
    }

    public Map<String, Asset> getAssets() {
        return assets;
    }

    public void setAssets(Map<String, Asset> assets) {
        this.assets = assets;
    }

    public Long getDispatchResultsPeriod() {
        return dispatchResultsPeriod;
    }

    public void setDispatchResultsPeriod(Long dispatchResultsPeriod) {
        this.dispatchResultsPeriod = dispatchResultsPeriod;
    }

    public Long getFilterThrottle() {
        return filterThrottle;
    }

    public void setFilterThrottle(Long filterThrottle) {
        this.filterThrottle = filterThrottle;
    }

    public Double getFilterConvergenceResidualThreshold() {
        return filterConvergenceResidualThreshold;
    }

    public void setFilterConvergenceResidualThreshold(Double filterConvergenceResidualThreshold) {
        this.filterConvergenceResidualThreshold = filterConvergenceResidualThreshold;
    }

    public Properties getProperties() {
        return properties;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public Map<Long, Observation> getObservations() {
        return observations;
    }

    public void setObservations(Map<Long, Observation> observations) {
        this.observations = observations;
    }

    public Double getFilterDispatchResidualThreshold() {
        return filterDispatchResidualThreshold;
    }

    public void setFilterDispatchResidualThreshold(Double filterDispatchResidualThreshold) {
        this.filterDispatchResidualThreshold = filterDispatchResidualThreshold;
    }

    public Double getFilterMeasurementError() {
        return filterMeasurementError;
    }

    public void setFilterMeasurementError(Double filterMeasurementError) {
        this.filterMeasurementError = filterMeasurementError;
    }

    public Boolean getOutputFilterState() {
        return outputFilterState;
    }

    public void setOutputFilterState(Boolean outputFilterState) {
        this.outputFilterState = outputFilterState;
    }

    public String getOutputFilterStateKmlFilename() {
        return outputFilterStateKmlFilename;
    }

    public void setOutputFilterStateKmlFilename(String outputFilterStateKmlFilename) {
        this.outputFilterStateKmlFilename = outputFilterStateKmlFilename;
    }

    public Double getFilterAOABias() {
        return filterAOABias;
    }

    public void setFilterAOABias(Double filterAOABias) {
        this.filterAOABias = filterAOABias;
    }

    public Double getFilterTDOABias() {
        return filterTDOABias;
    }

    public void setFilterTDOABias(Double filterTDOABias) {
        this.filterTDOABias = filterTDOABias;
    }

    public Double getFilterRangeBias() {
        return filterRangeBias;
    }

    public void setFilterRangeBias(Double filterRangeBias) {
        this.filterRangeBias = filterRangeBias;
    }

    public double[][] getFilterProcessNoise() {
        return filterProcessNoise;
    }

    public void setFilterProcessNoise(double[][] filterProcessNoise) {
        this.filterProcessNoise = filterProcessNoise;
    }

//    public Boolean getFilterUseSpecificInitialCondition() {
//        return filterUseSpecificInitialCondition;
//    }
//
//    public void setFilterUseSpecificInitialCondition(Boolean filterUseSpecificInitialCondition) {
//        this.filterUseSpecificInitialCondition = filterUseSpecificInitialCondition;
//    }

    public Double getFilterSpecificInitialLat() {
        return filterSpecificInitialLat;
    }

    public void setFilterSpecificInitialLat(Double filterSpecificInitialLat) {
        this.filterSpecificInitialLat = filterSpecificInitialLat;
    }

    public Double getFilterSpecificInitialLon() {
        return filterSpecificInitialLon;
    }

    public void setFilterSpecificInitialLon(Double filterSpecificInitialLon) {
        this.filterSpecificInitialLon = filterSpecificInitialLon;
    }

    public Boolean getFinished() {
        return finished;
    }

    public void setFinished(Boolean finished) {
        this.finished = finished;
    }

    public InitialStateMode getInitialStateMode() {
        return initialStateMode;
    }

    public void setInitialStateMode(InitialStateMode initialStateMode) {
        this.initialStateMode = initialStateMode;
    }

    public Long getMaxFilterIterations() {
        return maxFilterIterations;
    }

    public void setMaxFilterIterations(Long maxFilterIterations) {
        this.maxFilterIterations = maxFilterIterations;
    }

//    public InitialStateBoxCorner getFilterSpecificInitialBoxCorner() {
//        return filterSpecificInitialBoxCorner;
//    }
//
//    public void setFilterSpecificInitialBoxCorner(InitialStateBoxCorner filterSpecificInitialBoxCorner) {
//        this.filterSpecificInitialBoxCorner = filterSpecificInitialBoxCorner;
//    }

    public ComputeResults getComputeResults() {
        return computeResults;
    }

    public void setComputeResults(ComputeResults computeResults) {
        this.computeResults = computeResults;
    }
}
