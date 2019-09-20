package tech.tgo.fuzer.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tgo.fuzer.FuzerListener;
import tech.tgo.fuzer.FuzerProcess;
import tech.tgo.fuzer.model.*;
import tech.tgo.fuzer.util.ConfigurationException;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

import java.io.IOException;
import java.util.*;

public class Runner implements FuzerListener {

    private static final Logger log = LoggerFactory.getLogger(Runner.class);

    Map<String,GeoMission> fuzerMissions = new HashMap<String,GeoMission>();

    public static void main(String[] args) {
        Runner runner = new Runner();
        runner.runFuzerTest();
    }

    public void runFuzerTest() {
        FuzerProcess fuzerProcess = new FuzerProcess(this);

        /* Configure the intended mission */
        GeoMission geoMission = new GeoMission();
        geoMission.setFuzerMode(FuzerMode.track);
        geoMission.setTarget(new Target("RAND-TGT_ID","RAND-TGT-NAME"));
        geoMission.setGeoId("RAND-GEOID");
        geoMission.setShowMeas(true);
        geoMission.setShowCEPs(true);
        geoMission.setShowGEOs(true);
        geoMission.setOutputKml(true);
        geoMission.setOutputKmlFilename("geoOutput.kml");

        /* These configs are available for optional override */
        geoMission.setDispatchResultsPeriod(new Long(1000)); // Default it 1000
        geoMission.setFilterThrottle(null); // Default is null
        geoMission.setFilterConvergenceResidualThreshold(0.01); // Default is 0.01

        try {
            fuzerProcess.configure(geoMission);
        }
        catch (ConfigurationException ce) {
            log.error("Error trying to configure mission, returning. Error: "+ce.getMessage());
            ce.printStackTrace();
            return;
        }
        catch (IOException ioe) {
            log.error("IO Error trying to configure mission, returning. Error: "+ioe.getMessage());
            ioe.printStackTrace();
            return;
        }
        catch (Exception e) {
            log.error("Error trying to configuremission, returning");
            e.printStackTrace();
            return;
        }
        log.debug("Configured Geo Mission, continuing");

        /* Client side needs to manage geomission references for callback response */
        fuzerMissions.put(geoMission.getGeoId(), geoMission);

        /* For Tracker - start process and continually add new observations (one per asset), monitor result in result() callback */
        /* For Fixer - add observations (one per asset) then start, monitor output in result() callback */

        List<Observation> obsToAddAfter = new ArrayList<Observation>();
        List<Observation> obsToRemoveAfter = new ArrayList<Observation>();

        try {
            // Add occassional new measurements to trigger updates
            double[] asset_a_coords = new double[]{-31.9, 115.98};
            Observation obs = new Observation(new Long(1001), "RAND-ASSET-010", asset_a_coords[0], asset_a_coords[1]);
            obs.setRange(1000.0);
            obs.setObservationType(ObservationType.range);
            //fuzerProcess.addObservation(obs);

            Observation obs_update = new Observation(new Long(1001), "RAND-ASSET-010", asset_a_coords[0], asset_a_coords[1]);
            obs_update.setRange(700.0);
            obs_update.setObservationType(ObservationType.range);

            double[] asset_b_coords = new double[]{-31.88, 115.97};
            Observation obs_b = new Observation(new Long(1002),"RAND-ASSET-011", asset_b_coords[0], asset_b_coords[1]);
            obs_b.setRange(800.0); //range in metres
            obs_b.setObservationType(ObservationType.range);
            //fuzerProcess.addObservation(obs_b);

            Observation obs_c = new Observation(new Long(1003),"RAND-ASSET-010", asset_a_coords[0], asset_a_coords[1]);
            obs_c.setAssetId_b("RAND-ASSET-011");
            obs_c.setLat_b(asset_b_coords[0]);
            obs_c.setLon_b(asset_b_coords[1]);
            obs_c.setTdoa(0.000001); // tdoa in seconds
            obs_c.setObservationType(ObservationType.tdoa);
            //fuzerProcess.addObservation(obs_c);

            Observation obs_d = new Observation(new Long(1004),"RAND-ASSET-010", asset_a_coords[0], asset_a_coords[1]);
            obs_d.setAoa(2.5); // aoa in radians
            obs_d.setObservationType(ObservationType.aoa);
            fuzerProcess.addObservation(obs_d);

            Observation obs_e = new Observation(new Long(1005),"RAND-ASSET-011", asset_b_coords[0], asset_b_coords[1]);
            obs_e.setAoa(4.6); // aoa in radians
            obs_e.setObservationType(ObservationType.aoa);
            //fuzerProcess.addObservation(obs_e);

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
        catch (Exception e) {
            log.debug("Error adding observations: "+e.getMessage());
            e.printStackTrace();
        }

        try {
            fuzerProcess.start();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

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

    /* Client side receive raw result */
    @Override
    public void result(String geoId, double Xk1, double Xk2, double Xk3, double Xk4) {
        log.debug("RAW RESULT:::: GeoId: "+geoId+", Xk1: "+Xk1+", Xk2: "+Xk2+", Xk3: "+Xk3+", Xk4: "+Xk4);

        GeoMission geoMission = fuzerMissions.get(geoId);

        // Need to use lat/lonZone as set up when geo mission was first set up
        UTMRef utm = new UTMRef(Xk1,Xk2, geoMission.getLatZone(), geoMission.getLonZone());
        LatLng ltln = utm.toLatLng();
        log.debug("Result: Lat: "+ltln.getLat()+", Lon: "+ltln.getLng());
    }
}