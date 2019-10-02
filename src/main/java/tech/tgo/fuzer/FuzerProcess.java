package tech.tgo.fuzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tgo.fuzer.model.*;
import tech.tgo.fuzer.compute.AlgorithmEKF;
import tech.tgo.fuzer.util.ConfigurationException;
import tech.tgo.fuzer.util.FuzerValidator;
import tech.tgo.fuzer.util.Helpers;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;
import java.io.*;
import java.util.*;

/*
 * Geolocation fusion and tracking, using custom extended kalman filter implementation
 *
 * @author Timothy Edge (timmyedge)
 */
public class FuzerProcess implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(FuzerProcess.class);

    FuzerListener actionListener;

    GeoMission geoMission;

    AlgorithmEKF algorithmEKF;

    public FuzerProcess(FuzerListener actionListener) {
        this.actionListener = actionListener;
    }

    public void configure(GeoMission geoMission) throws Exception {
        this.geoMission = geoMission;

        FuzerValidator.validate(geoMission);

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
            throw new ConfigurationException("Trouble loading common application properties, reinstall the application");
        }

        if (geoMission.getOutputKml()) {
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

        /* Extract dispatch threshold setting */
        if (geoMission.getFilterDispatchResidualThreshold()==null) {
            if (geoMission.getProperties().getProperty("ekf.filter.default.dispatch_residual_threshold") != null && !geoMission.getProperties().getProperty("ekf.filter.default.dispatch_residual_threshold").isEmpty()) {
                geoMission.setFilterDispatchResidualThreshold(Double.parseDouble(geoMission.getProperties().getProperty("ekf.filter.default.dispatch_residual_threshold")));
            }
            else {
                throw new ConfigurationException("No dispatch threshold specified");
            }
        }

        /* Extract filter measurement error setting */
        if (geoMission.getFilterMeasurementError()==null) {
            if (geoMission.getProperties().getProperty("ekf.filter.default.measurement.error") != null && !geoMission.getProperties().getProperty("ekf.filter.default.measurement.error").isEmpty()) {
                geoMission.setFilterMeasurementError(Double.parseDouble(geoMission.getProperties().getProperty("ekf.filter.default.measurement.error")));
            }
            else {
                throw new ConfigurationException("No filter measurement error specified specified");
            }
        }
    }

    public void removeObservation(Long observationId) throws Exception {
        Observation obs = this.geoMission.observations.get(observationId);
        removeObservation(obs);
    }

    public void removeObservation(Observation obs) throws Exception {
        log.debug("Removing observation: "+obs.getAssetId()+","+obs.getObservationType().name());
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

    public void addObservation(Observation obs) throws Exception {
        FuzerValidator.validate(obs);

        /* Set previous measurement here, if this is a repeated measurement */
        if (this.getGeoMission().getObservations().get(obs.getId()) != null) {
            Observation prev_obs = this.getGeoMission().getObservations().get(obs.getId());
            if (prev_obs.getMeas()!=null) {
                log.debug("Setting previous observation for: "+prev_obs.getId()+", type: "+prev_obs.getObservationType().name()+", as: "+prev_obs.getMeas());
                obs.setMeas_prev(prev_obs.getMeas());
            }
        }

        log.debug("Adding observation: "+obs.getAssetId()+","+obs.getObservationType().name()+", ID: "+obs.getId());
        this.geoMission.getObservations().put(obs.getId(), obs);

        Object[] zones = Helpers.getUtmLatZoneLonZone(obs.getLat(), obs.getLon());
        obs.setY_latZone((char)zones[0]);
        obs.setX_lonZone((int)zones[1]);

        /* Rudimentary here - use zones attached to the most recent observation. improvements to come */
        this.geoMission.setLatZone(obs.getY_latZone());
        this.geoMission.setLonZone(obs.getX_lonZone());

        double[] utm_coords = Helpers.convertLatLngToUtmNthingEasting(obs.getLat(), obs.getLon());
        obs.setY(utm_coords[0]);
        obs.setX(utm_coords[1]);
        log.debug("Asset:"+obs.getY()+","+obs.getX());

        Asset asset = new Asset(obs.getAssetId(),new double[]{obs.getLat(),obs.getLon()});
        this.geoMission.getAssets().put(obs.getAssetId(),asset);

        /* There is a second asset to register its location */
        if (obs.getObservationType().equals(ObservationType.tdoa)) {
            double[] utm_coords_b = Helpers.convertLatLngToUtmNthingEasting(obs.getLat_b(), obs.getLon_b());
            obs.setYb(utm_coords_b[0]);
            obs.setXb(utm_coords_b[1]);

            Asset asset_b = new Asset(obs.getAssetId_b(),new double[]{obs.getLat_b(),obs.getLon_b()});
            this.geoMission.getAssets().put(obs.getAssetId_b(),asset_b);
        }

        if (this.geoMission.showMeas)
        {
            /* RANGE MEASUREMENT */
            if (obs.getObservationType().equals(ObservationType.range)) {
                List<double[]> measurementCircle = new ArrayList<double[]>();
                for (double theta = (1 / 2) * Math.PI; theta <= (5 / 2) * Math.PI; theta += 0.2) {
                    //UTMRef utmMeas = new UTMRef(obs.getRange() * Math.cos(theta) + obs.getX(), obs.getRange() * Math.sin(theta) + obs.getY(), this.geoMission.getLatZone(), this.geoMission.getLonZone());
                    UTMRef utmMeas = new UTMRef(obs.getMeas() * Math.cos(theta) + obs.getX(), obs.getMeas() * Math.sin(theta) + obs.getY(), this.geoMission.getLatZone(), this.geoMission.getLonZone());
                    LatLng ltln = utmMeas.toLatLng();
                    double[] measPoint = {ltln.getLat(), ltln.getLng()};
                    measurementCircle.add(measPoint);
                }
                this.geoMission.circlesToShow.add(obs.getId());
                obs.setCircleGeometry(measurementCircle);
            }

            /* TDOA MEASUREMENT */
            if (obs.getObservationType().equals(ObservationType.tdoa)) {
                List<double[]> measurementHyperbola = new ArrayList<double[]>();
                double c = Math.sqrt(Math.pow((obs.getX()-obs.getXb()),2)+Math.pow((obs.getYb()-obs.getY()),2))/2;
                //double a=(obs.getTdoa()* Helpers.SPEED_OF_LIGHT)/2; double b=Math.sqrt(Math.abs(Math.pow(c,2)-Math.pow(a,2)));
                double a=(obs.getMeas()* Helpers.SPEED_OF_LIGHT)/2; double b=Math.sqrt(Math.abs(Math.pow(c,2)-Math.pow(a,2)));
                double ca = (obs.getXb()-obs.getX())/(2*c); double sa = (obs.getYb()-obs.getY())/(2*c); //# COS and SIN of rot angle
                for (double t = -2; t<= 2; t += 0.1) {
                    double X = a*Math.cosh(t); double Y = b*Math.sinh(t); //# Hyperbola branch
                    double x = (obs.getX()+obs.getXb())/2 + X*ca - Y*sa; //# Rotated and translated
                    double y = (obs.getY()+obs.getYb())/2 + X*sa + Y*ca;
//                    log.debug("branch x/y: "+X+","+Y);
//                    log.debug("Asset1/2 X: "+obs.getX()+","+obs.getXb());
//                    log.debug("Asset1/2 Y: "+obs.getY()+","+obs.getYb());
//                    log.debug("HYP x/y: "+x+","+y);
                    UTMRef utmMeas = new UTMRef(x, y, this.geoMission.getLatZone(), this.geoMission.getLonZone());
                    LatLng ltln = utmMeas.toLatLng();
                    measurementHyperbola.add(new double[]{ltln.getLat(),ltln.getLng()});
                }
                this.geoMission.hyperbolasToShow.add(obs.getId());
                obs.setHyperbolaGeometry(measurementHyperbola);
            }

            /* AOA MEASUREMENT */
            if (obs.getObservationType().equals(ObservationType.aoa)) {
                List<double[]> measurementLine = new ArrayList<double[]>();
                //double b = obs.getY() - Math.tan(obs.getAoa())*obs.getX();
                double b = obs.getY() - Math.tan(obs.getMeas())*obs.getX();
                double fromVal=0; double toVal=0;
                //double x_run = Math.abs(Math.cos(obs.getAoa()))*5000;
                double x_run = Math.abs(Math.cos(obs.getMeas()))*5000;
                //if (obs.getAoa()>Math.PI/2 && obs.getAoa()<3*Math.PI/2) { // negative-x plane projection
                if (obs.getMeas()>Math.PI/2 && obs.getMeas()<3*Math.PI/2) { // negative-x plane projection
                    fromVal=-x_run; toVal=0;
                }
                else { // positive-x plane projection
                    fromVal=0; toVal=x_run;
                }

                for (double t = obs.getX()+fromVal; t<= obs.getX()+toVal; t += 100) {
                    //double y = Math.tan(obs.getAoa())*t + b;
                    double y = Math.tan(obs.getMeas())*t + b;
                    UTMRef utmMeas = new UTMRef(t, y, this.geoMission.getLatZone(), this.geoMission.getLonZone());
                    LatLng ltln = utmMeas.toLatLng();
                    double[] measPoint = {ltln.getLat(), ltln.getLng()};
                    measurementLine.add(measPoint);
                }
                this.geoMission.linesToShow.add(obs.getId());
                obs.setLineGeometry(measurementLine);
            }
        }

        /* Update the live observations - if a 'tracking' mission type */
        if (algorithmEKF !=null && algorithmEKF.isRunning()) {
            log.debug("Algorithm was running, will update observations list for tracking mode runs only");
            if (this.geoMission.getFuzerMode().equals(FuzerMode.track)) {

                // TODO, do this in batches to avoid filter drift mid process


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

    public void stop() throws Exception {
        algorithmEKF.stopThread();
    }

    /* For Tracker - start process and continually add new observations (one per asset), monitor result in result() callback */
    /* For Fixer - add observations (one per asset) then start, monitor output in result() callback */
    public void start() throws Exception {
        Iterator it = this.geoMission.observations.values().iterator();
        if (!it.hasNext() && this.geoMission.getFuzerMode().equals(FuzerMode.fix)) {
            throw new ConfigurationException("There were no observations, couldn't start the process");
        }

        algorithmEKF = new AlgorithmEKF(this.actionListener, this.geoMission.observations, this.geoMission);
        Thread thread = new Thread(algorithmEKF);
        thread.start();
    }

    public GeoMission getGeoMission() {
        return geoMission;
    }
}