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

    // Generate lat,lon path as desired
    double true_lat; double true_lon;

    /* Some common asset coords to reuse */
    double[] asset_a_coords = new double[]{-31.9, 115.98};
    double[] asset_b_coords = new double[]{-31.88, 115.97};

    double rand_factor = 0.0000001;

    @Override
    public void run() {

        // at fixed rate, add new observations suite (one of each type)
        // move from previous point according to some movement model
        true_lat = true_lat + 0.00001;
        true_lon = true_lon + 0.00001;

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
            //    init_meas(j,:) = [sqrt((x_rssi(j)-X_true(1,1))^2 + (y_rssi(j)-X_true(2,1))^2)];
            //f_meas(i,:) = [sqrt((x_rssi(i)-X_true(1,k))^2 + (y_rssi(i)-X_true(2,k))^2)] + 1*(0.5-rand);


            // TODO, use UTM here

            double meas_range = Math.sqrt(Math.pow(a_y-true_y,2) + Math.pow(a_x-true_x,2)) + Math.random()*rand_factor;
            log.debug("Meas range: "+meas_range);

            Observation obs = new Observation(new Long(1001), "RAND-ASSET-010", asset_a_coords[0], asset_a_coords[1]);
            obs.setRange(meas_range);
            obs.setObservationType(ObservationType.range);
            fuzerProcess.addObservation(obs);
        }
        catch (Exception e) { e.printStackTrace(); }

        try {
            double meas_range = Math.sqrt(Math.pow(b_y-true_y,2) + Math.pow(b_x-true_x,2)) + Math.random()*rand_factor;
            log.debug("Meas range: "+meas_range);

            Observation obs_b = new Observation(new Long(1002), "RAND-ASSET-011", asset_b_coords[0], asset_b_coords[1]);
            obs_b.setRange(meas_range); //range in metres
            obs_b.setObservationType(ObservationType.range);
            fuzerProcess.addObservation(obs_b);
        } catch (Exception e) { e.printStackTrace(); }


//        try {
        //     init_meas(j,:) = [sqrt((x(1)-X_true(1,1))^2 + (y(1)-X_true(2,1))^2) - sqrt((x(j+1)-X_true(1,1))^2 + (y(j+1)-X_true(2,1))^2)];
        // f_meas(i,:) = [sqrt((x(1)-X_true(1,k))^2 + (y(1)-X_true(2,k))^2) - sqrt((x(i+1)-X_true(1,k))^2 + (y(i+1)-X_true(2,k))^2)] + 1*(0.5-rand);


//            double meas_range = Math.sqrt(Math.pow(asset_a_coords[0]-true_lat,2) + Math.pow(asset_a_coords[1]-true_lon,2)) + Math.random()*rand_factor;
//            log.debug("Meas range: "+meas_range);
//
//            Observation obs_c = new Observation(new Long(1003), "RAND-ASSET-010", asset_a_coords[0], asset_a_coords[1]);
//            obs_c.setAssetId_b("RAND-ASSET-011");
//            obs_c.setLat_b(asset_b_coords[0]);
//            obs_c.setLon_b(asset_b_coords[1]);
//            obs_c.setTdoa(0.000001); // tdoa in seconds
//            obs_c.setObservationType(ObservationType.tdoa);
//            fuzerProcess.addObservation(obs_c);
//        }
//        catch (Exception e) { e.printStackTrace(); }
//
//        try {
        // f_meas(i,:) = [atan((y_aoa(i) - X_true(2,k))/(x_aoa(i) - X_true(1,k)))*180/pi];
        // Need to adjust for quadrant


//            Observation obs_d = new Observation(new Long(1004),"RAND-ASSET-010", asset_a_coords[0], asset_a_coords[1]);
//            obs_d.setAoa(2.5); // aoa in radians
//            obs_d.setObservationType(ObservationType.aoa);
//            fuzerProcess.addObservation(obs_d);
//        }
//        catch (Exception e) { e.printStackTrace(); }
//
//
//        try {
//            Observation obs_e = new Observation(new Long(1005),"RAND-ASSET-011", asset_b_coords[0], asset_b_coords[1]);
//            obs_e.setAoa(4.6); // aoa in radians
//            obs_e.setObservationType(ObservationType.aoa);
//            fuzerProcess.addObservation(obs_e);
//        }
//        catch (Exception e) { e.printStackTrace(); }
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
