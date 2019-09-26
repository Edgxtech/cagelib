package tech.tgo.fuzer.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tgo.fuzer.FuzerProcess;
import tech.tgo.fuzer.model.Observation;
import tech.tgo.fuzer.model.ObservationType;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/* Add various specified observations */
public class StationaryTargetObserver extends TimerTask {

    private static final Logger log = LoggerFactory.getLogger(StationaryTargetObserver.class);

    FuzerProcess fuzerProcess;

    // Generate lat,lon path as desired
    double true_lat; double true_lon;

    /* Some common asset coords to reuse */
    double[] asset_a_coords = new double[]{-31.9, 115.98};
    double[] asset_b_coords = new double[]{-31.88, 115.97};


    // TODO, test filter with these
    //    010
    //            18:02:43.438 [Timer-0] DEBUG t.t.f.a.MovingTargetObserver - Meas range: 12127.685912858213
    //    011
    //            18:02:43.442 [Timer-0] DEBUG t.t.f.a.MovingTargetObserver - Meas range: 9866.33387558542
    //    010/011 TDOA
    //            18:02:43.442 [Timer-0] DEBUG t.t.f.a.MovingTargetObserver - Meas tdoa: 7.550502415838483E-6


    @Override
    public void run() {

        // Use specified measurements

        try {
            Observation obs = new Observation(new Long(1001), "RAND-ASSET-010", asset_a_coords[0], asset_a_coords[1]);
            obs.setRange(9578.033028205187); // 1000
            obs.setObservationType(ObservationType.range);
            fuzerProcess.addObservation(obs);
        }
        catch (Exception e) { e.printStackTrace(); }

        try {
            Observation obs_b = new Observation(new Long(1002), "RAND-ASSET-011", asset_b_coords[0], asset_b_coords[1]);
            obs_b.setRange(7167.968659991545); //range in metres, 800
            obs_b.setObservationType(ObservationType.range);
            fuzerProcess.addObservation(obs_b);
        } catch (Exception e) { e.printStackTrace(); }

        try {
            Observation obs_c = new Observation(new Long(1003), "RAND-ASSET-010", asset_a_coords[0], asset_a_coords[1]);
            obs_c.setAssetId_b("RAND-ASSET-011");
            obs_c.setLat_b(asset_b_coords[0]);
            obs_c.setLon_b(asset_b_coords[1]);
            obs_c.setTdoa(8.11004087653552E-6); // tdoa in seconds, 0.000001
            obs_c.setObservationType(ObservationType.tdoa);
            fuzerProcess.addObservation(obs_c);
        }
        catch (Exception e) { e.printStackTrace(); }

        try {
            Observation obs_d = new Observation(new Long(1004),"RAND-ASSET-010", asset_a_coords[0], asset_a_coords[1]);
            obs_d.setAoa(1.9973481642311934); // aoa in radians
            obs_d.setObservationType(ObservationType.aoa);
            fuzerProcess.addObservation(obs_d);
        }
        catch (Exception e) { e.printStackTrace(); }

//        try {
//            Observation obs_e = new Observation(new Long(1005),"RAND-ASSET-011", asset_b_coords[0], asset_b_coords[1]);
//            obs_e.setAoa(4.6); // aoa in radians
//            obs_e.setObservationType(ObservationType.aoa);
//            //fuzerProcess.addObservation(obs_e);
//        }
//        catch (Exception e) { e.printStackTrace(); }


        List<Observation> obsToRemoveAfter = new ArrayList<Observation>();
        List<Observation> obsToAddAfter = new ArrayList<Observation>();

        try{
            //obsToAddAfter.add(obs);
            //obsToAddAfter.add(obs_b);
            //obsToAddAfter.add(obs_c);
            //obsToAddAfter.add(obs_d);
            //obsToAddAfter.add(obs_e);
            //obsToAddAfter.add(obs_update);

            //obsToRemoveAfter.add(obs);
            //obsToRemoveAfter.add(obs_b);
            //obsToRemoveAfter.add(obs_c);

        }
        catch (Exception e) { e.printStackTrace(); }

        // TODO, create a moving track simulation with reverse engineered observations to test tracking better

        log.debug("For sim and testing: adding this many observations periodically: "+obsToAddAfter.size());
        if (obsToAddAfter.size()>0) {
            Timer timer = new Timer();
            ObservationAdder observationAdder = new ObservationAdder();
            observationAdder.setFuzerProcess(fuzerProcess);
            observationAdder.setObservations(obsToAddAfter);
            timer.scheduleAtFixedRate(observationAdder,5000,5000);
        }
        log.debug("For sim and testing: removing this many observations periodically: "+obsToAddAfter.size());
        if (obsToRemoveAfter.size()>0) {
            Timer timer = new Timer();
            ObservationRemover observationRemover = new ObservationRemover();
            observationRemover.setFuzerProcess(fuzerProcess);
            observationRemover.setObservations(obsToRemoveAfter);
            timer.scheduleAtFixedRate(observationRemover,10000,5000);
        }

        /* RUN STOP START AS A TEST */
//        try {
//            Thread.sleep(10000);
//            log.info("STOPPING PROCESS");
//            fuzerProcess.stop();
//
//            fuzerProcess.removeObservation(new Long(1001));
//            fuzerProcess.removeObservation(new Long(1002));
//
//            Observation obs_d = new Observation(new Long(1006),"RAND-ASSET-013", -31.93, 115.93);
//            obs_d.setAoa(2.6); // aoa in radians
//            obs_d.setObservationType(ObservationType.aoa);
//            fuzerProcess.addObservation(obs_d);
//
//            Observation obs_e = new Observation(new Long(1007),"RAND-ASSET-011", -31.88, 115.97);
//            obs_e.setAoa(3.3); // aoa in radians
//            obs_e.setObservationType(ObservationType.aoa);
//            fuzerProcess.addObservation(obs_e);
//
//            Thread.sleep(5000);
//
//            log.info("STARTING PROCESS");
//            fuzerProcess.start();
//        }
//        catch (Exception e) {
//            log.error("Problem starting process: "+e.getMessage());
//        }
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
