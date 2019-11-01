//package tech.tgo.fuzer.application;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import tech.tgo.fuzer.FuzerProcess;
//import tech.tgo.fuzer.model.Observation;
//import tech.tgo.fuzer.model.ObservationType;
//
//import java.util.ArrayList;
//import java.util.Iterator;
//import java.util.List;
//import java.util.TimerTask;
//
///* Just to simulate receiving new observations */
//public class ObservationSetAdder extends TimerTask {
//
//    private static final Logger log = LoggerFactory.getLogger(ObservationSetAdder.class);
//
//    FuzerProcess fuzerProcess;
//
//    @Override
//    public void run() {
//
//        /* common asset coords to reuse */
//        double[] asset_a_coords = new double[]{-31.9, 115.98};
//        double[] asset_b_coords = new double[]{-31.88, 115.97};
//
//        // post:
//        // 010,range,7297.319277260829
//        // 011, range,5854.150068401644
//        // tdoa, 4.813894313709471E-6
//        // 010,aoa, 1.194647512344934
//        // 011,aoa,0.8981865734042553
//
//        try {
//            Observation obs = new Observation(new Long(1001), "ASSET-010", asset_a_coords[0], asset_a_coords[1]);
//            obs.setMeas(7297.319277260829); // 1000
//            obs.setObservationType(ObservationType.range);
//            fuzerProcess.addObservation(obs);
//        }
//        catch (Exception e) { e.printStackTrace(); }
//
//        try {
//            Observation obs_b = new Observation(new Long(1002), "ASSET-011", asset_b_coords[0], asset_b_coords[1]);
//            obs_b.setMeas(5854.150068401644); //range in metres, 800
//            obs_b.setObservationType(ObservationType.range);
//            fuzerProcess.addObservation(obs_b);
//        } catch (Exception e) { e.printStackTrace(); }
//
//        try {
//            Observation obs_c = new Observation(new Long(1003), "ASSET-010", asset_a_coords[0], asset_a_coords[1]);
//            obs_c.setAssetId_b("ASSET-011");
//            obs_c.setLat_b(asset_b_coords[0]);
//            obs_c.setLon_b(asset_b_coords[1]);
//            obs_c.setMeas(4.813894313709471E-6); // tdoa in seconds, 0.000001
//            obs_c.setObservationType(ObservationType.tdoa);
//            fuzerProcess.addObservation(obs_c);
//        }
//        catch (Exception e) { e.printStackTrace(); }
//
//        try {
//            Observation obs_d = new Observation(new Long(1004),"ASSET-010", asset_a_coords[0], asset_a_coords[1]);
//            obs_d.setMeas(1.194647512344934); // aoa in radians
//            obs_d.setObservationType(ObservationType.aoa);
//            fuzerProcess.addObservation(obs_d);
//        }
//        catch (Exception e) { e.printStackTrace(); }
//
//        try {
//            Observation obs_e = new Observation(new Long(1005),"ASSET-011", asset_b_coords[0], asset_b_coords[1]);
//            obs_e.setMeas(0.8981865734042553); // aoa in radians
//            obs_e.setObservationType(ObservationType.aoa);
//            fuzerProcess.addObservation(obs_e);
//        }
//        catch (Exception e) { e.printStackTrace(); }
//    }
//
//    public FuzerProcess getFuzerProcess() {
//        return fuzerProcess;
//    }
//
//    public void setFuzerProcess(FuzerProcess fuzerProcess) {
//        this.fuzerProcess = fuzerProcess;
//    }
//
//}
