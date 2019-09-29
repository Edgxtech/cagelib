package tech.tgo.fuzer.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tgo.fuzer.FuzerProcess;
import tech.tgo.fuzer.model.Observation;
import tech.tgo.fuzer.model.ObservationType;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

/* Add various specified observations */
public class ObservationAdderMain {

    private static final Logger log = LoggerFactory.getLogger(ObservationAdderMain.class);

    FuzerProcess fuzerProcess;

    double true_lat; double true_lon;

    /* common asset coords to reuse */
    double[] asset_a_coords = new double[]{-31.9, 115.98};
    double[] asset_b_coords = new double[]{-31.88, 115.97};

    public void run() {

        // Use specified measurements

        // trouble
        // pre:
        // 010,range,7365.616902255358
        // 011,range,5882.339355162425
        //tdoa,4.9476813292378846E-6
        //aoa,1.212210248792518
        //aoa,0.9225553622513195

        // post:
        // 010,range,7297.319277260829
        // 011, range,5854.150068401644
        // tdoa, 4.813894313709471E-6
        // 010,aoa, 1.194647512344934
        // 011,aoa,0.8981865734042553

        try {
            Observation obs = new Observation(new Long(1001), "ASSET-010", asset_a_coords[0], asset_a_coords[1]);
            obs.setRange(7365.616902255358); // 1000
            obs.setObservationType(ObservationType.range);
            fuzerProcess.addObservation(obs);
        }
        catch (Exception e) { e.printStackTrace(); }

        try {
            Observation obs_b = new Observation(new Long(1002), "ASSET-011", asset_b_coords[0], asset_b_coords[1]);
            obs_b.setRange(5882.339355162425); //range in metres, 800
            obs_b.setObservationType(ObservationType.range);
            fuzerProcess.addObservation(obs_b);
        } catch (Exception e) { e.printStackTrace(); }

        try {
            Observation obs_c = new Observation(new Long(1003), "ASSET-010", asset_a_coords[0], asset_a_coords[1]);
            obs_c.setAssetId_b("ASSET-011");
            obs_c.setLat_b(asset_b_coords[0]);
            obs_c.setLon_b(asset_b_coords[1]);
            obs_c.setTdoa(4.9476813292378846E-6); // tdoa in seconds, 0.000001
            obs_c.setObservationType(ObservationType.tdoa);
            fuzerProcess.addObservation(obs_c);
        }
        catch (Exception e) { e.printStackTrace(); }

        try {
            Observation obs_d = new Observation(new Long(1004),"ASSET-010", asset_a_coords[0], asset_a_coords[1]);
            obs_d.setAoa(1.194647512344934); // aoa in radians
            obs_d.setObservationType(ObservationType.aoa);
            fuzerProcess.addObservation(obs_d);
        }
        catch (Exception e) { e.printStackTrace(); }

        try {
            Observation obs_e = new Observation(new Long(1005),"ASSET-011", asset_b_coords[0], asset_b_coords[1]);
            obs_e.setAoa(0.8981865734042553); // aoa in radians
            obs_e.setObservationType(ObservationType.aoa);
            fuzerProcess.addObservation(obs_e);
        }
        catch (Exception e) { e.printStackTrace(); }


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

        Timer timer = new Timer();
        ObservationSetAdder observationSetAdder = new ObservationSetAdder();
        observationSetAdder.setFuzerProcess(fuzerProcess);
        timer.schedule(observationSetAdder,2000);

        /* RUN STOP START AS A TEST */
//        try {
//            Thread.sleep(10000);
//            log.info("STOPPING PROCESS");
//            fuzerProcess.stop();
//
//            fuzerProcess.removeObservation(new Long(1001));
//            fuzerProcess.removeObservation(new Long(1002));
//
//            Observation obs_d = new Observation(new Long(1006),"ASSET-013", -31.93, 115.93);
//            obs_d.setAoa(2.6); // aoa in radians
//            obs_d.setObservationType(ObservationType.aoa);
//            fuzerProcess.addObservation(obs_d);
//
//            Observation obs_e = new Observation(new Long(1007),"ASSET-011", -31.88, 115.97);
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
