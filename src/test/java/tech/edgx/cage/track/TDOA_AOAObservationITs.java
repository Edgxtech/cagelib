package tech.edgx.cage.track;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.edgx.cage.CageProcessManager;
import tech.edgx.cage.CageListener;
import tech.edgx.cage.compute.ComputeResults;
import tech.edgx.cage.model.GeoMission;
import tech.edgx.cage.model.MissionMode;
import tech.edgx.cage.model.Target;
import tech.edgx.cage.util.ConfigurationException;
import tech.edgx.cage.util.TestAsset;
import tech.edgx.cage.util.SimulatedTargetObserver;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

public class TDOA_AOAObservationITs implements CageListener {

    private static final Logger log = LoggerFactory.getLogger(TDOA_AOAObservationITs.class);

    Map<String, GeoMission> missionsMap = new HashMap<String,GeoMission>();

    CageProcessManager cageProcessManager = new CageProcessManager(this);

    SimulatedTargetObserver simulatedTargetObserver = new SimulatedTargetObserver();

    Timer timer = new Timer();

    /* Some common asset coords to reuse */
    double[] asset_a_coords = new double[]{-31.9, 115.98};
    double[] asset_b_coords = new double[]{-31.88, 115.97};
    double[] asset_c_coords = new double[]{-31.78, 115.90};
    double[] asset_d_coords = new double[]{-32.0, 115.85};

    TestAsset asset_a = new TestAsset();
    TestAsset asset_b = new TestAsset();
    TestAsset asset_c = new TestAsset();
    TestAsset asset_d = new TestAsset();

    GeoMission geoMission;

    double[] latest_est_latlon;

    @Before
    public void configure() {
        simulatedTargetObserver.setCageProcessManager(cageProcessManager);

        /* Configure the intended mission */
        geoMission = new GeoMission();
        geoMission.setMissionMode(MissionMode.track);
        geoMission.setTarget(new Target("MY_TGT_ID","MY_TGT_NAME"));
        geoMission.setGeoId("MY_GEO_ID");
        geoMission.setShowMeas(true);
        geoMission.setShowCEPs(true);
        geoMission.setShowGEOs(true);
        geoMission.setOutputKml(true);
        geoMission.setOutputKmlFilename("geoOutput.kml");
        geoMission.setShowTrueLoc(true);
        geoMission.setOutputFilterState(true);
        geoMission.setOutputFilterStateKmlFilename("filterState.kml");

        try {
            cageProcessManager.configure(geoMission);
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
            log.error("Error trying to configure mission, returning");
            e.printStackTrace();
            return;
        }
        log.debug("Configured Geo Mission, continuing");

        /* Client side needs to manage geomission references for callback response */
        missionsMap.put(geoMission.getGeoId(), geoMission);

        /* Create some reusable test assets */
        asset_a.setId("A");
        asset_a.setProvide_aoa(true);
        asset_a.setProvide_tdoa(true);
        asset_a.setCurrent_loc(asset_a_coords);

        asset_b.setId("B");
        asset_b.setProvide_aoa(true);
        asset_b.setProvide_tdoa(true);
        asset_b.setCurrent_loc(asset_b_coords);

        asset_c.setId("C");
        asset_c.setProvide_aoa(true);
        asset_c.setProvide_tdoa(true);
        asset_c.setCurrent_loc(asset_c_coords);

        asset_d.setId("D");
        asset_d.setProvide_aoa(true);
        asset_d.setProvide_tdoa(true);
        asset_d.setCurrent_loc(asset_d_coords);

        asset_a.setTdoa_asset_ids(Arrays.asList(new String[]{"B","C","D"}));
        asset_b.setTdoa_asset_ids(Arrays.asList(new String[]{"C","D"}));
        asset_c.setTdoa_asset_ids(Arrays.asList(new String[]{"D"}));
    }

    /* Result callback */
    @Override
    public void result(String geoId, double lat, double lon, double cep_elp_maj, double cep_elp_min, double cep_elp_rot) {
        log.debug("Result -> GeoId: " + geoId + ", Lat: " + lat + ", Lon: " + lon + ", CEP major: " + cep_elp_maj + ", CEP minor: " + cep_elp_min + ", CEP rotation: " + cep_elp_rot);
    }

    /* Result callback */
    @Override
    public void result(ComputeResults results) {
        log.debug("Result [NEW] -> GeoId: "+results.getGeoId()+", Lat: "+results.getGeolocationResult().getLat()+", Lon: "+results.getGeolocationResult().getLon()+", CEP major: "+results.getGeolocationResult().getElp_long()+", CEP minor: "+results.getGeolocationResult().getElp_short()+", CEP rotation: "+results.getGeolocationResult().getElp_rot());

        // buffer just the latest value - ok for fix tests, need to hold all est since first convergence for track tests
        latest_est_latlon = new double[]{results.getGeolocationResult().getLat(),results.getGeolocationResult().getLon()};
    }

    @Test
    public void testMoverNorthEast() {
        simulatedTargetObserver.setTrue_lat(-31.98); // BOTTOM
        simulatedTargetObserver.setTrue_lon(116.000);
        simulatedTargetObserver.setTdoa_rand_factor(0.0000001);
        simulatedTargetObserver.setAoa_rand_factor(0.0);
        simulatedTargetObserver.setLat_move(+0.005); // MOVE NE
        simulatedTargetObserver.setLon_move(+0.005);

        geoMission.setFilterDispatchResidualThreshold(1.0);
        geoMission.setDispatchResultsPeriod(new Long(100));

        geoMission.setFilterAOABias(1.0);
        geoMission.setFilterTDOABias(1.0);
        geoMission.setFilterRangeBias(1.0);

        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
            put(asset_c.getId(), asset_c);
            put(asset_d.getId(), asset_d);
        }};
        simulatedTargetObserver.setTestAssets(assets);
        timer.scheduleAtFixedRate(simulatedTargetObserver,0,999);

        try {
            cageProcessManager.start();

            Thread.sleep(40000);

            timer.cancel();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Flicks over to wrong side from 360-0 crossing
    // Sometimes doesnt latch on from initial convergence
    @Test
    public void testMoverNorthEast_TwoAssets() {
        simulatedTargetObserver.setTrue_lat(-31.98);  // BOTTOM
        simulatedTargetObserver.setTrue_lon(116.000);
        simulatedTargetObserver.setTdoa_rand_factor(0.0000001);
        simulatedTargetObserver.setAoa_rand_factor(0.1);
        simulatedTargetObserver.setLat_move(+0.005); // MOVE NE
        simulatedTargetObserver.setLon_move(+0.005);

        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
            asset_a.setTdoa_asset_ids(Arrays.asList(new String[]{"B"}));
            asset_b.setTdoa_asset_ids(Arrays.asList(new String[]{}));
        }};
        simulatedTargetObserver.setTestAssets(assets);
        timer.scheduleAtFixedRate(simulatedTargetObserver,0,999);

        try {
            cageProcessManager.start();

            Thread.sleep(40000);

            timer.cancel();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testStationaryTarget() {
        simulatedTargetObserver.setTrue_lat(-31.98);  // LEFT
        simulatedTargetObserver.setTrue_lon(115.80);
//        simulatedTargetObserver.setTrue_lat(-31.98);  // BOTTOM (Drifts DR incorrectly under bad init conditions)
//        simulatedTargetObserver.setTrue_lon(116.000);
        simulatedTargetObserver.setAoa_rand_factor(0.1);
        simulatedTargetObserver.setTdoa_rand_factor(0.0000001);
        simulatedTargetObserver.setLat_move(0.000); // NO MOVEMENT
        simulatedTargetObserver.setLon_move(0.000);

        geoMission.setFilterDispatchResidualThreshold(1.0);
        geoMission.setDispatchResultsPeriod(new Long(100));

        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
            put(asset_c.getId(), asset_c);
            put(asset_d.getId(), asset_d);
        }};
        simulatedTargetObserver.setTestAssets(assets);
        timer.scheduleAtFixedRate(simulatedTargetObserver,0,5000);

        try {
            cageProcessManager.start();

            Thread.sleep(40000);

            timer.cancel();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testStationaryTarget_SingleMeasurement() {
        simulatedTargetObserver.setTrue_lat(-31.98); // LEFT
        simulatedTargetObserver.setTrue_lon(115.80);
        simulatedTargetObserver.setTrue_lat(-31.98);  // BOTTOM
        simulatedTargetObserver.setTrue_lon(116.000);
        simulatedTargetObserver.setTrue_lat(-31.7); // TOPRIGHT
        simulatedTargetObserver.setTrue_lon(116.08);
        simulatedTargetObserver.setAoa_rand_factor(0.15);
        simulatedTargetObserver.setTdoa_rand_factor(0.0000001);
        simulatedTargetObserver.setLat_move(0.000); // NO MOVEMENT
        simulatedTargetObserver.setLon_move(0.000);

        geoMission.setFilterDispatchResidualThreshold(100.0);
        geoMission.setDispatchResultsPeriod(new Long(100));

        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
            put(asset_c.getId(), asset_c);
            put(asset_d.getId(), asset_d);
        }};
        simulatedTargetObserver.setTestAssets(assets);
        simulatedTargetObserver.run();

        try {
            cageProcessManager.start();

            Thread.sleep(40000);

            timer.cancel();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testMoverSouthWest() {
        simulatedTargetObserver.setTrue_lat(-31.7); // TOPRIGHT
        simulatedTargetObserver.setTrue_lon(116.08);
        simulatedTargetObserver.setAoa_rand_factor(0.1);
        simulatedTargetObserver.setTdoa_rand_factor(0.0000001);
        simulatedTargetObserver.setLat_move(-0.005); // MOVE SW
        simulatedTargetObserver.setLon_move(-0.005);

        geoMission.setFilterMeasurementError(1.0);
        geoMission.setFilterDispatchResidualThreshold(4.0);

        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
            put(asset_c.getId(), asset_c);
            put(asset_d.getId(), asset_d);
        }};
        simulatedTargetObserver.setTestAssets(assets);
        timer.scheduleAtFixedRate(simulatedTargetObserver,0,999);

        try {
            cageProcessManager.start();

            Thread.sleep(60000);

            timer.cancel();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
