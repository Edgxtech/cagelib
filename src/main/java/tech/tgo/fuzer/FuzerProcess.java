package tech.tgo.fuzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tgo.fuzer.model.*;
import tech.tgo.fuzer.thread.AlgorithmEKF;
import tech.tgo.fuzer.util.ConfigurationException;
import tech.tgo.fuzer.util.Helpers;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

import java.io.*;
import java.util.*;

public class FuzerProcess implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(FuzerProcess.class);

    FuzerListener actionListener;

    //Map<Long,Observation> observations = new ConcurrentHashMap<Long,Observation>();  moved to GM

    GeoMission geoMission;

    AlgorithmEKF algorithmEKF;

    public FuzerProcess(FuzerListener actionListener) {
        this.actionListener = actionListener;

    }

    public void configure(GeoMission geoMission) throws Exception {
        this.geoMission = geoMission;

        Properties properties = new Properties();
        String appConfigPath = Thread.currentThread().getContextClassLoader().getResource("").getPath() + "application.properties";
        try {
            properties.load(new FileInputStream(appConfigPath));
            this.geoMission.setProperties(properties);
        }
        catch(IOException ioe) {
            log.error(ioe.getMessage());
            ioe.printStackTrace();
            log.error("Error reading application properties");
        }

        if (geoMission.isOutputKml()) {
            log.debug("Creating new kml output file as: "+ properties.getProperty("working.directory")+"output/"+geoMission.getOutputKmlFilename());
            File kmlOutput = new File(properties.getProperty("working.directory")+"output/"+geoMission.getOutputKmlFilename());
            kmlOutput.createNewFile();
        }

        /* Extract results dispatch period */
        if (geoMission.getDispatchResultsPeriod()==null) {
            if (geoMission.getProperties().getProperty("ekf.filter.default.dispatch_results_period") != null && !geoMission.getProperties().getProperty("ekf.filter.default.dispatch_results_period").isEmpty()) {
                geoMission.setDispatchResultsPeriod(new Long(properties.getProperty("ekf.filter.default.dispatch_results_period")));
            }
            else {
                throw new ConfigurationException("No dispatch results period specified");
            }
        }

        /* Extract throttle setting - NULL is allowed */
        if (geoMission.getFilterThrottle()==null) {
            if (geoMission.getProperties().getProperty("ekf.filter.default.throttle") != null) {
                if (geoMission.getProperties().getProperty("ekf.filter.default.throttle").isEmpty()) {
                    geoMission.setFilterThrottle(null);
                }
                else {
                    geoMission.setFilterThrottle(Long.parseLong(geoMission.getProperties().getProperty("ekf.filter.default.throttle")));
                }
            }
        }

        /* Extract convergence threshold setting */
        if (geoMission.getFilterConvergenceResidualThreshold()==null) {
            if (geoMission.getProperties().getProperty("ekf.filter.default.convergence_residual_threshold") != null && !geoMission.getProperties().getProperty("ekf.filter.default.convergence_residual_threshold").isEmpty()) {
                geoMission.setFilterConvergenceResidualThreshold(Double.parseDouble(geoMission.getProperties().getProperty("ekf.filter.default.convergence_residual_threshold")));
            }
            else {
                throw new ConfigurationException("No convergence threshold specified");
            }
        }
    }

    public void removeObservation(Observation obs) throws Exception {
        log.debug("Removing observation: "+obs.getAssetId()+","+obs.getObservationType().name());
        //this.observations.remove(obs.getAssetId()+","+obs.getObservationType().name());
        this.geoMission.observations.remove(obs.getId());

        /* If asset has no other linked observations, remove it */
        boolean hasOtherObs = false;
        for (Map.Entry<Long, Observation> o : this.geoMission.observations.entrySet()) {
            if (o.getValue().getAssetId().equals(obs.getAssetId())) {
                hasOtherObs=true;
                break;
            }
        }
        if (!hasOtherObs) {
            this.geoMission.getAssets().remove(obs.getAssetId());
        }

        // Remove plottable measurement
        if (obs.getObservationType().equals(ObservationType.range)) {
            this.geoMission.circlesToShow.remove(obs.getId());
        }
        else if (obs.getObservationType().equals(ObservationType.tdoa)) {
            this.geoMission.hyperbolasToShow.remove(obs.getId());
        }
        else if (obs.getObservationType().equals(ObservationType.aoa)) {
            this.geoMission.linesToShow.remove(obs.getId());
        }
    }

    // Fix vs Track: difference is one uses preselected obs only, track ha ability to dynamically add/remove them and stay online
    public void addObservation(Observation obs) throws Exception {
        // TODO, input validation

        // Restricted to hold only one observation per asset per type
        log.debug("Adding observation: "+obs.getAssetId()+","+obs.getObservationType().name()+", ID: "+obs.getId());
        //this.observations.put(obs.getAssetId()+","+obs.getObservationType().name(), obs);
        this.geoMission.observations.put(obs.getId(), obs);

        UTMRef assetUtmLoc = new UTMRef(obs.getX(), obs.getY(), this.geoMission.getLatZone(), this.geoMission.getLonZone());
        LatLng asset_ltln = assetUtmLoc.toLatLng();
        Asset asset = new Asset(obs.getAssetId(),new double[]{asset_ltln.getLat(),asset_ltln.getLng()});
        this.geoMission.getAssets().put(obs.getAssetId(),asset);


        if (obs.getObservationType().equals(ObservationType.tdoa)) {
            // There is a second asset to register its location
            assetUtmLoc = new UTMRef(obs.getXb(), obs.getYb(), this.geoMission.getLatZone(), this.geoMission.getLonZone());
            asset_ltln = assetUtmLoc.toLatLng();
            Asset asset_b = new Asset(obs.getAssetId_b(),new double[]{asset_ltln.getLat(),asset_ltln.getLng()});
            this.geoMission.getAssets().put(obs.getAssetId_b(),asset_b);
        }

        if (this.geoMission.showMeas)
        {
            /* RANGE MEASUREMENT */
            if (obs.getObservationType().equals(ObservationType.range)) {
                List<double[]> measurementCircle = new ArrayList<double[]>();
                for (double theta = (1 / 2) * Math.PI; theta <= (5 / 2) * Math.PI; theta += 0.2) {
                    UTMRef utmMeas = new UTMRef(obs.getRange() * Math.cos(theta) + obs.getX(), obs.getRange() * Math.sin(theta) + obs.getY(), this.geoMission.getLatZone(), this.geoMission.getLonZone());
                    LatLng ltln = utmMeas.toLatLng();
                    double[] measPoint = {ltln.getLat(), ltln.getLng()};
                    measurementCircle.add(measPoint);
                }
                // tODO, add it back to the observation itself, instead of the geoMission, then just add a refernce to current active measCircles in a Set
                //this.geoMission.measurementCircles.put(obs.getAssetId(), measurementCircle);
                this.geoMission.circlesToShow.add(obs.getId());
                obs.setCircleGeometry(measurementCircle);
            }

            /* TDOA MEASUREMENT */
            if (obs.getObservationType().equals(ObservationType.tdoa)) {
                List<double[]> measurementHyperbola = new ArrayList<double[]>();
                double c = Math.sqrt(Math.pow((obs.getX()-obs.getXb()),2)+Math.pow((obs.getYb()-obs.getY()),2))/2;
                double a=(obs.getTdoa()* Helpers.SPEED_OF_LIGHT)/2; double b=Math.sqrt(Math.pow(c,2)-Math.pow(a,2));
                double ca = (obs.getXb()-obs.getX())/(2*c); double sa = (obs.getYb()-obs.getY())/(2*c); // COS and SIN of rot angle
                for (double t = -2; t<= 2; t += 0.1) {
                    double X = a*Math.cosh(t); double Y = b*Math.sinh(t); // Hyperbola branch
                    double x = (obs.getX()+obs.getXb())/2 + X*ca - Y*sa; //# Rotated and translated
                    double y = (obs.getY()+obs.getYb())/2 + X*sa + Y*ca;
                    UTMRef utmMeas = new UTMRef(x, y, this.geoMission.getLatZone(), this.geoMission.getLonZone());
                    LatLng ltln = utmMeas.toLatLng();
                    measurementHyperbola.add(new double[]{ltln.getLat(),ltln.getLng()});
                }
                //this.geoMission.measurementHyperbolas.put(obs.getAssetId()+"/"+obs.getAssetId_b(), measurementHyperbola);
                this.geoMission.hyperbolasToShow.add(obs.getId());
                obs.setHyperbolaGeometry(measurementHyperbola);
            }

            /* AOA MEASUREMENT */
            if (obs.getObservationType().equals(ObservationType.aoa)) {
                List<double[]> measurementLine = new ArrayList<double[]>();
                double b = obs.getY() - Math.tan(obs.getAoa())*obs.getX();
                double fromVal=0; double toVal=0;
                double x_run = Math.abs(Math.cos(obs.getAoa()))*5000;
                log.debug("X RUN: "+x_run);
                if (obs.getAoa()>Math.PI/2 && obs.getAoa()<3*Math.PI/2) { // negative-x plane projection
                    fromVal=-x_run; toVal=0;
                }
                else { // positive-x plane projection
                    fromVal=0; toVal=x_run;
                }

                for (double t = obs.getX()+fromVal; t<= obs.getX()+toVal; t += 100) {
                    double y = Math.tan(obs.getAoa())*t + b;
                    UTMRef utmMeas = new UTMRef(t, y, this.geoMission.getLatZone(), this.geoMission.getLonZone());
                    LatLng ltln = utmMeas.toLatLng();
                    double[] measPoint = {ltln.getLat(), ltln.getLng()};
                    measurementLine.add(measPoint);
                }
                //this.geoMission.measurementLines.put(obs.getAssetId(), measurementLine);
                this.geoMission.linesToShow.add(obs.getId());
                obs.setLineGeometry(measurementLine);
            }
        }


        /* Update the live observations - if 'tracking' mission type */
        if (algorithmEKF !=null && algorithmEKF.isRunning()) {
            log.debug("Algorithm was running, will update observations list for tracking mode runs only");
            if (this.geoMission.getFuzerMode().equals(FuzerMode.track)) {
                log.debug("Setting OBSERVATIONS in the filter, new size: "+this.geoMission.observations.size());
                algorithmEKF.setObservations(this.geoMission.observations);
            }
            else {
                log.debug("Not adding this OBSERVATION to filter since is configured to produce a single FIX, run again with different observations");
            }
        }
        else {
            log.debug("Algorithm was not running, observation will be available for future runs");
        }
    }

    public void restart() {
        algorithmEKF.stopThread();

        start();
    }

    public void start() {
        // TODO, stop currently active thread if any, but preserve its state if it was running

        /* run a filter thread here using current observations */
        algorithmEKF = new AlgorithmEKF(this.actionListener, this.geoMission.observations, this.geoMission);
        Thread thread = new Thread(algorithmEKF);
        thread.start();

        // TODO, also need a method for managing/expiring old observations
    }
}