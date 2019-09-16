package tech.tgo.fuzer;

import tech.tgo.fuzer.model.*;
import tech.tgo.fuzer.util.Helpers;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Runner implements FuzerListener {

    Map<String,GeoMission> fuzerMissions = new HashMap<String,GeoMission>();

    public static void main(String[] args) {
        Runner runner = new Runner();
        runner.runFuzerTest();
    }

    public void runFuzerTest() {
        FuzerProcess fuzerProcess = new FuzerProcess(this);

        // config the process - Target, Type (Geo/Track), Tolerance's
        GeoMission geoMission = new GeoMission();
        geoMission.setFuzerMode(FuzerMode.fix);
        geoMission.setTarget(new Target("RAND-TGT_ID","RAND-TGT-NAME")); // Set this in client logic
        geoMission.setGeoId("RAND-GEOID"); // Set this in client logic
        geoMission.setShowMeas(true);
        geoMission.setShowCEPs(true);
        geoMission.setShowGEOs(true);
        geoMission.setOutputKml(true);
        geoMission.setOutputKmlFilename("geoOutput.kml");

        try {
            fuzerProcess.configure(geoMission);
        }
        catch (IOException ioe) {
            System.out.println("IO Error trying to configure geo mission: "+ioe.getMessage());
            ioe.printStackTrace();
            return;
        }
        catch (Exception e) {
            System.out.println("Error trying to configure geo mission, returning");
            e.printStackTrace();
            return;
        }
        System.out.println("Configured Geo Mission, continuing");

        // Need an indication of geographical area to start with IOT set common lat/lon Zone
        LatLng ltln = new LatLng(-31.891551,115.996399); // -31.891551,115.996399 PERTH AREA   6471146.785151098,405091.95251542656
        UTMRef utm = ltln.toUTMRef();
        geoMission.setLatZone(utm.getLatZone());
        geoMission.setLonZone(utm.getLngZone());
        fuzerMissions.put(geoMission.getGeoId(), geoMission);

        /* For Tracker - start process and continually add new observations (one per asset), monitor result in result() callback */
        /* For Fixer - add observations (one per asset) then start, monitor output in result() callback */

        try {
            // Add occassional new measurements to trigger updates -
            double[] utm_coords = Helpers.convertLatLngToUtmNthingEasting(-31.9, 115.98);
//            Observation obs = new Observation("RAND-ASSET-010", utm_coords[0], utm_coords[1]);
//            obs.setRange(1000.0);
//            obs.setObservationType(ObservationType.range);
//            fuzerProcess.addObservation(obs);
//            System.out.println("Added obs from 010");

            double[] utm_coords_b = Helpers.convertLatLngToUtmNthingEasting(-31.88, 115.97);
//            Observation obs_b = new Observation("RAND-ASSET-011", utm_coords_b[0], utm_coords_b[1]);
//            obs_b.setRange(800.0);
//            obs_b.setObservationType(ObservationType.range);
//            fuzerProcess.addObservation(obs_b);
//            System.out.println("Added obs from 011");

            // Add an example TDOA measurement between 010and 011
            Observation obs_c = new Observation("RAND-ASSET-010", utm_coords[0], utm_coords[1]);
            obs_c.setAssetId_b("RAND-ASSET-011");
            obs_c.setYb(utm_coords_b[0]);
            obs_c.setXb(utm_coords_b[1]);
            obs_c.setTdoa(0.000001); // tdoa in seconds
            obs_c.setObservationType(ObservationType.tdoa);
            fuzerProcess.addObservation(obs_c);
            System.out.println("Added tdoa obs from 010/011");

            Observation obs_d = new Observation("RAND-ASSET-010", utm_coords_b[0], utm_coords_b[1]);
            obs_d.setAoa(6); // Approx 2.09~=120degress in radians, 4.88~=280 degrees
            obs_d.setObservationType(ObservationType.aoa);
            fuzerProcess.addObservation(obs_d);
            System.out.println("Added aoa obs from 010");

        }
        catch (Exception e) {
            System.out.println("Error adding observations: "+e.getMessage());
            e.printStackTrace();
        }

        fuzerProcess.start();
    }

    /* Client side receive raw result */
    @Override
    public void result(String geoId, double Xk1, double Xk2, double Xk3, double Xk4) {
        System.out.println("RAW RESULT:::: GeoId: "+geoId+", Xk1: "+Xk1+", Xk2: "+Xk2+", Xk3: "+Xk3+", Xk4: "+Xk4);

        GeoMission geoMission = fuzerMissions.get(geoId);

        // Need to use lat/lonZone as set up when geo mission was first set up
        UTMRef utm = new UTMRef(Xk1,Xk2, geoMission.getLatZone(), geoMission.getLonZone());
        LatLng ltln = utm.toLatLng();
        System.out.println("Result: Lat: "+ltln.getLat()+", Lon: "+ltln.getLng());

    }
}