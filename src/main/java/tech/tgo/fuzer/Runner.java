package tech.tgo.fuzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tgo.fuzer.model.*;
import tech.tgo.fuzer.util.Helpers;
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

        // config the process - Target, Type (Geo/Track), Tolerance's
        GeoMission geoMission = new GeoMission();
        geoMission.setFuzerMode(FuzerMode.track);
        geoMission.setTarget(new Target("RAND-TGT_ID","RAND-TGT-NAME"));
        geoMission.setGeoId("RAND-GEOID");
        geoMission.setShowMeas(true);
        geoMission.setShowCEPs(true);
        geoMission.setShowGEOs(true);
        geoMission.setOutputKml(true);
        geoMission.setOutputKmlFilename("geoOutput.kml");
        geoMission.setDispatchResultsPeriod(new Long(1000));

        try {
            fuzerProcess.configure(geoMission);
        }
        catch (IOException ioe) {
            log.debug("IO Error trying to configure geo mission: "+ioe.getMessage());
            ioe.printStackTrace();
            return;
        }
        catch (Exception e) {
            log.debug("Error trying to configure geo mission, returning");
            e.printStackTrace();
            return;
        }
        log.debug("Configured Geo Mission, continuing");

        // Need an indication of geographical area to start with IOT set common lat/lon Zone
        LatLng ltln = new LatLng(-31.891551,115.996399); // -31.891551,115.996399 PERTH AREA   6471146.785151098,405091.95251542656
        UTMRef utm = ltln.toUTMRef();
        geoMission.setLatZone(utm.getLatZone());
        geoMission.setLonZone(utm.getLngZone());
        fuzerMissions.put(geoMission.getGeoId(), geoMission);

        /* For Tracker - start process and continually add new observations (one per asset), monitor result in result() callback */
        /* For Fixer - add observations (one per asset) then start, monitor output in result() callback */

        List<Observation> obsToAddAfter = new ArrayList<Observation>();

        try {
            // Add occassional new measurements to trigger updates -
            double[] utm_coords = Helpers.convertLatLngToUtmNthingEasting(-31.9, 115.98);
            Observation obs = new Observation("RAND-ASSET-010", utm_coords[0], utm_coords[1]);
            obs.setRange(1000.0);
            obs.setObservationType(ObservationType.range);
//            fuzerProcess.addObservation(obs);

            double[] utm_coords_b = Helpers.convertLatLngToUtmNthingEasting(-31.88, 115.97);
            Observation obs_b = new Observation("RAND-ASSET-011", utm_coords_b[0], utm_coords_b[1]);
            obs_b.setRange(800.0); //range in metres
            obs_b.setObservationType(ObservationType.range);
//            fuzerProcess.addObservation(obs_b);

            Observation obs_c = new Observation("RAND-ASSET-010", utm_coords[0], utm_coords[1]);
            obs_c.setAssetId_b("RAND-ASSET-011");
            obs_c.setYb(utm_coords_b[0]);
            obs_c.setXb(utm_coords_b[1]);
            obs_c.setTdoa(0.000001); // tdoa in seconds
            obs_c.setObservationType(ObservationType.tdoa);
            fuzerProcess.addObservation(obs_c);

            Observation obs_d = new Observation("RAND-ASSET-010", utm_coords[0], utm_coords[1]);
            obs_d.setAoa(2.5); // aoa in radians
            obs_d.setObservationType(ObservationType.aoa);
            //fuzerProcess.addObservation(obs_d);

            Observation obs_e = new Observation("RAND-ASSET-011", utm_coords_b[0], utm_coords_b[1]);
            obs_e.setAoa(4.6); // aoa in radians
            obs_e.setObservationType(ObservationType.aoa);
            fuzerProcess.addObservation(obs_e);

            //obsToAddAfter.add(obs);
            //obsToAddAfter.add(obs_b);
            //obsToAddAfter.add(obs_c);
            obsToAddAfter.add(obs_d);
            //obsToAddAfter.add(obs_e);
        }
        catch (Exception e) {
            log.debug("Error adding observations: "+e.getMessage());
            e.printStackTrace();
        }

        fuzerProcess.start();

        // TODO, create a moving track simulation with reverse engineered observations to test tracking better
        log.debug("For sim and testing: adding this many observations periodically: "+obsToAddAfter.size());
        if (obsToAddAfter.size()>0) {
            Timer timer = new Timer();
            ObservationAdder observationAdder = new ObservationAdder();
            observationAdder.setFuzerProcess(fuzerProcess);
            observationAdder.setObservations(obsToAddAfter);
            timer.scheduleAtFixedRate(observationAdder,10000,5000);
        }

        // TEST that when a FIX msn exits, can re-run it with latest set of observations

        try {
            if (geoMission.getFuzerMode().equals(FuzerMode.fix)) {
                Thread.sleep(30000);
                fuzerProcess.start();
            }
        }
        catch (InterruptedException iee) {

        }
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