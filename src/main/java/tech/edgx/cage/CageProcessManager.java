package tech.edgx.cage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.edgx.cage.compute.ComputeProcessor;
import tech.edgx.cage.model.*;
import tech.edgx.cage.util.CageValidator;
import tech.edgx.cage.util.ConfigurationException;
import tech.edgx.cage.util.Helpers;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

import java.io.*;
import java.util.*;

/**
 * Geolocation fusion and tracking, using custom extended kalman filter implementation
 */
public class CageProcessManager implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(CageProcessManager.class);

    CageListener actionListener;

    GeoMission geoMission;

    ComputeProcessor computeProcessor;

    public CageProcessManager(CageListener actionListener) {
        this.actionListener = actionListener;
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
        CageValidator.validate(obs);

        /* Enrich the base image further, required for filter operation */
        configureObservation(obs);

        log.debug("Adding observation: "+obs.getAssetId()+","+obs.getObservationType().name()+","+obs.getMeas()+",ID:"+obs.getId());
        this.geoMission.getObservations().put(obs.getId(), obs);
    }

    public void stop() throws Exception {
        computeProcessor.stopThread();
    }

    /* For Tracker - start process and continually add new observations (one per asset), monitor result in result() callback */
    /* For Fixer - add observations (one per asset) then start, monitor output in result() callback */
    public Thread start() throws Exception {
        Iterator it = this.geoMission.observations.values().iterator();
        if (!it.hasNext() && this.geoMission.getMissionMode().equals(MissionMode.fix)) {
            throw new ConfigurationException("There were no observations, couldn't start the process");
        }
        computeProcessor = new ComputeProcessor(this.actionListener, this.geoMission.observations, this.geoMission);
        computeProcessor.initialiseFilter();
        Thread thread = new Thread(computeProcessor);
        thread.start();
        return thread;
    }

    public GeoMission getGeoMission() {
        return geoMission;
    }

    public void configureObservation(Observation obs) throws Exception {
        Properties properties = this.geoMission.getProperties();

        /* Set previous measurement here, if this is a repeated measurement */
        if (this.getGeoMission().getObservations().get(obs.getId()) != null) {
            Observation prev_obs = this.getGeoMission().getObservations().get(obs.getId());
            if (prev_obs.getMeas()!=null) {
                log.trace("Setting previous observation for: "+prev_obs.getId()+", type: "+prev_obs.getObservationType().name()+", as: "+prev_obs.getMeas());
                obs.setMeas_prev(prev_obs.getMeas());
            }
        }

        Object[] zones = Helpers.getUtmLatZoneLonZone(obs.getLat(), obs.getLon());
        obs.setY_latZone((char)zones[0]);
        obs.setX_lonZone((int)zones[1]);

        /* Rudimentary - use zones attached to the most recent observation */
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

        /* Extract default measurement error if not provided with observation */
        if (obs.getMeas_error()==null) {
            if (obs.getObservationType().equals(ObservationType.range)) {
                if (properties.getProperty("ekf.filter.default.range.meas_error") != null && !properties.getProperty("ekf.filter.default.range.meas_error").isEmpty()) {
                    obs.setMeas_error(Double.parseDouble(properties.getProperty("ekf.filter.default.range.meas_error")));
                } else {
                    throw new ConfigurationException("No range meas error specified");
                }
            }
            else if (obs.getObservationType().equals(ObservationType.aoa)) {
                if (properties.getProperty("ekf.filter.default.aoa.meas_error") != null && !properties.getProperty("ekf.filter.default.aoa.meas_error").isEmpty()) {
                    obs.setMeas_error(Double.parseDouble(properties.getProperty("ekf.filter.default.aoa.meas_error")));
                } else {
                    throw new ConfigurationException("No aoa meas error specified");
                }
            }
            else if (obs.getObservationType().equals(ObservationType.tdoa)) {
                if (properties.getProperty("ekf.filter.default.tdoa.meas_error") != null && !properties.getProperty("ekf.filter.default.tdoa.meas_error").isEmpty()) {
                    obs.setMeas_error(Double.parseDouble(properties.getProperty("ekf.filter.default.tdoa.meas_error")));
                } else {
                    throw new ConfigurationException("No tdoa meas error specified");
                }
            }
        }

        if (this.geoMission.getShowMeas()) {
            /* RANGE MEASUREMENT */
            if (obs.getObservationType().equals(ObservationType.range)) {
                List<double[]> measurementCircle = new ArrayList<double[]>();
                for (double theta = (1 / 2) * Math.PI; theta <= (5 / 2) * Math.PI; theta += 0.2) {
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
                double a=(obs.getMeas()* Helpers.SPEED_OF_LIGHT)/2; double b=Math.sqrt(Math.abs(Math.pow(c,2)-Math.pow(a,2)));
                double ca = (obs.getXb()-obs.getX())/(2*c); double sa = (obs.getYb()-obs.getY())/(2*c); //# COS and SIN of rot angle
                for (double t = -2; t<= 2; t += 0.1) {
                    double X = a*Math.cosh(t); double Y = b*Math.sinh(t); //# Hyperbola branch
                    double x = (obs.getX()+obs.getXb())/2 + X*ca - Y*sa; //# Rotated and translated
                    double y = (obs.getY()+obs.getYb())/2 + X*sa + Y*ca;
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
                double b = obs.getY() - Math.tan(obs.getMeas())*obs.getX();
                double fromVal=0; double toVal=0;
                double x_run = Math.abs(Math.cos(obs.getMeas()))*5000;
                if (obs.getMeas()>Math.PI/2 && obs.getMeas()<3*Math.PI/2) { // negative-x plane projection
                    fromVal=-x_run; toVal=0;
                }
                else { // positive-x plane projection
                    fromVal=0; toVal=x_run;
                }

                for (double t = obs.getX()+fromVal; t<= obs.getX()+toVal; t += 100) {
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

        log.debug("Configured Observation");
    }

    public void configure(GeoMission geoMission) throws Exception {
        this.geoMission = geoMission;

        CageValidator.validate(geoMission);

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

        if (geoMission.getOutputFilterState()) {
            log.debug("Creating new kml output file as: "+ properties.getProperty("working.directory")+"output/"+geoMission.getOutputFilterStateKmlFilename());
            File kmlFilterStateOutput = new File(properties.getProperty("working.directory")+"output/"+geoMission.getOutputFilterStateKmlFilename());
            kmlFilterStateOutput.createNewFile();
        }

        /* Extract filter process noise */
        if (geoMission.getFilterProcessNoise()==null) {
            if (geoMission.getProperties().getProperty("ekf.filter.default.process_noise") != null && !geoMission.getProperties().getProperty("ekf.filter.default.process_noise").isEmpty()) {
                double process_noise = Double.parseDouble(geoMission.getProperties().getProperty("ekf.filter.default.process_noise"));
                geoMission.setFilterProcessNoise(new double[][]{{process_noise, 0}, {0, process_noise}});
            }
            else {
                throw new ConfigurationException("No filter process noise specified");
            }
        }

        /* Extract results dispatch period */
        if (geoMission.getDispatchResultsPeriod()==null) {
            if (geoMission.getProperties().getProperty("ekf.filter.default.dispatch_results_period") != null && !geoMission.getProperties().getProperty("ekf.filter.default.dispatch_results_period").isEmpty()) {
                geoMission.setDispatchResultsPeriod(Long.parseLong(properties.getProperty("ekf.filter.default.dispatch_results_period")));
            }
            else {
                throw new ConfigurationException("No dispatch results period specified");
            }
        }

        log.debug("Max iterations 1: "+geoMission.getMaxFilterIterations());
        /* Extract max filter iterations */
        if (geoMission.getMaxFilterIterations()==null) {
            if (geoMission.getProperties().getProperty("ekf.filter.default.max_iterations") != null && !geoMission.getProperties().getProperty("ekf.filter.default.max_iterations").isEmpty()) {
                geoMission.setMaxFilterIterations(Long.parseLong(properties.getProperty("ekf.filter.default.max_iterations")));
            }
            else {
                throw new ConfigurationException("No dispatch results period specified");
            }
        }

        /* Extract Initial State Mode */
        if (geoMission.getInitialStateMode()==null) {
            if (geoMission.getProperties().getProperty("ekf.filter.default.initial_state_mode") != null && !geoMission.getProperties().getProperty("ekf.filter.default.initial_state_mode").isEmpty()) {
                geoMission.setInitialStateMode(InitialStateMode.valueOf(properties.getProperty("ekf.filter.default.initial_state_mode")));
            }
            else {
                throw new ConfigurationException("No initial_state_mode specified");
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

        log.debug("Max iterations 2: "+geoMission.getMaxFilterIterations());
        if (geoMission.getMaxFilterIterations()<100000) {
            log.warn("Max Iterations is: "+geoMission.getMaxFilterIterations()+", less than recommended minimum of 100000, may see early exit and incorrect result");
        }
    }
}