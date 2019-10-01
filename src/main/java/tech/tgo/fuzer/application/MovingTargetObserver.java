package tech.tgo.fuzer.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tgo.fuzer.FuzerProcess;
import tech.tgo.fuzer.model.Observation;
import tech.tgo.fuzer.model.ObservationType;
import tech.tgo.fuzer.model.Target;
import tech.tgo.fuzer.util.Helpers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TimerTask;

/* Just to simulate receiving new observations following a target */
public class MovingTargetObserver extends TimerTask {

    private static final Logger log = LoggerFactory.getLogger(MovingTargetObserver.class);

    FuzerProcess fuzerProcess;

    double true_lat; double true_lon;

    /* Some common asset coords to reuse */
    double[] asset_a_coords = new double[]{-31.9, 115.98};
    double[] asset_b_coords = new double[]{-31.88, 115.97};

//    double range_rand_factor = 5; /* Guide: 50 [m] */
//    double tdoa_rand_factor = 0.0000001; /* Guide: 0.0000001 [sec] */
//    double aoa_rand_factor = 0.001; /* Guide: 0.1 [radians] */
    double range_rand_factor = 300; /* Guide: 50 [m] */
    double tdoa_rand_factor = 0.0000001; /* Guide: 0.0000001 [sec] */
    double aoa_rand_factor = 0.2; /* Guide: 0.1 [radians] */

    @Override
    public void run() {

        // Generate lat,lon path
        // at fixed rate, add new observations suite (one of each type)
        // move from previous point according to some movement model
        true_lat = true_lat + 0.012;
        true_lon = true_lon - 0.006;
        log.debug("Moving Observer, moved target to: "+true_lat+","+true_lon);

        // update GeoMission::Target::TrueLocation
        fuzerProcess.getGeoMission().getTarget().setTrue_current_loc(new Double[]{true_lat,true_lon});

        double[] utm_coords = Helpers.convertLatLngToUtmNthingEasting(true_lat, true_lon);
        double true_y = utm_coords[0];
        double true_x = utm_coords[1];

        utm_coords = Helpers.convertLatLngToUtmNthingEasting(asset_a_coords[0], asset_a_coords[1]);
        double a_y = utm_coords[0];
        double a_x = utm_coords[1];

        utm_coords = Helpers.convertLatLngToUtmNthingEasting(asset_b_coords[0], asset_b_coords[1]);
        double b_y = utm_coords[0];
        double b_x = utm_coords[1];

        try {
            double meas_range = Math.sqrt(Math.pow(a_y-true_y,2) + Math.pow(a_x-true_x,2)) + Math.random()*range_rand_factor;
            log.debug("Meas range: "+meas_range);

            Observation obs = new Observation(new Long(1001), "ASSET-010", asset_a_coords[0], asset_a_coords[1]);
            obs.setMeas(meas_range);
            obs.setObservationType(ObservationType.range);
            fuzerProcess.addObservation(obs);
        }
        catch (Exception e) { e.printStackTrace(); }

        try {
            double meas_range = Math.sqrt(Math.pow(b_y-true_y,2) + Math.pow(b_x-true_x,2)) + Math.random()*range_rand_factor;
            log.debug("Meas range: "+meas_range);

            Observation obs_b = new Observation(new Long(1002), "ASSET-011", asset_b_coords[0], asset_b_coords[1]);
            obs_b.setMeas(meas_range); //range in metres
            obs_b.setObservationType(ObservationType.range);
            fuzerProcess.addObservation(obs_b);
        } catch (Exception e) { e.printStackTrace(); }

        try {
            double meas_tdoa = (Math.sqrt(Math.pow(a_y-true_y,2) + Math.pow(a_x-true_x,2))
                    - Math.sqrt(Math.pow(b_y-true_y,2) + Math.pow(b_x-true_x,2)))/Helpers.SPEED_OF_LIGHT
                    + Math.random()*tdoa_rand_factor;
            log.debug("Meas tdoa: "+meas_tdoa);

            Observation obs_c = new Observation(new Long(1003), "ASSET-010", asset_a_coords[0], asset_a_coords[1]);
            obs_c.setAssetId_b("ASSET-011");
            obs_c.setLat_b(asset_b_coords[0]);
            obs_c.setLon_b(asset_b_coords[1]);
            obs_c.setMeas(meas_tdoa); // tdoa in seconds
            obs_c.setObservationType(ObservationType.tdoa);
            fuzerProcess.addObservation(obs_c);
        }
        catch (Exception e) { e.printStackTrace(); }

        try {
            double meas_aoa = Math.atan((a_y-true_y)/(a_x-true_x)) + Math.random()*aoa_rand_factor;;
            log.debug("Meas AOA: "+meas_aoa);

            if (true_x < a_x) {
                meas_aoa = meas_aoa + Math.PI;
            }
            if (true_y<a_y && true_x>=a_x) {
                meas_aoa = (Math.PI- Math.abs(meas_aoa)) + Math.PI;
            }
            log.debug("Meas AOA (adjusted): "+meas_aoa);

            Observation obs_d = new Observation(new Long(1004),"ASSET-010", asset_a_coords[0], asset_a_coords[1]);
            obs_d.setMeas(meas_aoa); // aoa in radians
            obs_d.setObservationType(ObservationType.aoa);
            fuzerProcess.addObservation(obs_d);
        }
        catch (Exception e) { e.printStackTrace(); }

        try {
            double meas_aoa = Math.atan((b_y-true_y)/(b_x-true_x)) + Math.random()*aoa_rand_factor;;
            log.debug("Meas AOA: "+meas_aoa);

            if (true_x < b_x) {
                meas_aoa = meas_aoa + Math.PI;
            }
            if (true_y<b_y && true_x>=b_x) {
                meas_aoa = (Math.PI- Math.abs(meas_aoa)) + Math.PI;
            }
            log.debug("Meas AOA (adjusted): "+meas_aoa);

            Observation obs_e = new Observation(new Long(1005),"ASSET-011", asset_b_coords[0], asset_b_coords[1]);
            obs_e.setMeas(meas_aoa); // aoa in radians
            obs_e.setObservationType(ObservationType.aoa);
            fuzerProcess.addObservation(obs_e);
        }
        catch (Exception e) { e.printStackTrace(); }
    }

    public FuzerProcess getFuzerProcess() {
        return fuzerProcess;
    }

    public void setFuzerProcess(FuzerProcess fuzerProcess) {
        this.fuzerProcess = fuzerProcess;
    }

    public double getTrue_lat() {
        return true_lat;
    }

    public void setTrue_lat(double true_lat) {
        this.true_lat = true_lat;
    }

    public double getTrue_lon() {
        return true_lon;
    }

    public void setTrue_lon(double true_lon) {
        this.true_lon = true_lon;
    }
}
