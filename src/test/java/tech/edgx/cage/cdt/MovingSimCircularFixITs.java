package tech.edgx.cage.cdt;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.edgx.cage.CageProcessManager;
import tech.edgx.cage.CageListener;
import tech.edgx.cage.compute.ComputeResults;
import tech.edgx.cage.model.*;
import tech.edgx.cage.util.ConfigurationException;
import tech.edgx.cage.util.Helpers;
import tech.edgx.cage.util.SimulatedAssetObserver;
import tech.edgx.cage.util.TestAssetMoving;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

import java.io.IOException;
import java.util.*;

/**
 * These tests create asset moving circular around a target, providing new observations each time as opposed to updating their 'last' observation
 */
public class MovingSimCircularFixITs implements CageListener {

    private static final Logger log = LoggerFactory.getLogger(MovingSimCircularFixITs.class);
    private static final Logger test_output_log = LoggerFactory.getLogger("test_output");

    Map<String, GeoMission> missionsMap = new HashMap<String, GeoMission>();

    CageProcessManager cageProcessManager = new CageProcessManager(this);

    SimulatedAssetObserver simulatedAssetObserver = new SimulatedAssetObserver();

    /* Some common asset coords to reuse */
    double[] asset_a_coords = new double[]{-31.9, 115.98};
    double[] asset_b_coords = new double[]{-31.88, 115.97};
    double[] asset_c_coords = new double[]{-31.78, 115.90};
    double[] asset_d_coords = new double[]{-32.0, 115.85};

    TestAssetMoving asset_a = new TestAssetMoving();
    TestAssetMoving asset_b = new TestAssetMoving();
    TestAssetMoving asset_c = new TestAssetMoving();
    TestAssetMoving asset_d = new TestAssetMoving();

    GeoMission geoMission;

    SummaryStatistics ATEStats;
    double[] latest_est_latlon;

    @Before
    public void configure() {
        /* Test specific configuration */
        simulatedAssetObserver.setEfusionProcessManager(cageProcessManager);

        /* Configure the intended mission */
        geoMission = new GeoMission();
        geoMission.setMissionMode(MissionMode.fix);
        geoMission.setTarget(new Target("MY_TGT_ID", "MY_TGT_NAME"));
        geoMission.setGeoId("MY_GEO_ID");
        geoMission.setShowMeas(true);
        geoMission.setShowCEPs(true);
        geoMission.setShowGEOs(true);
        geoMission.setOutputKml(true);
        geoMission.setOutputKmlFilename("geoOutput.kml");
        geoMission.setShowTrueLoc(true);
        geoMission.setOutputFilterState(true);
        geoMission.setOutputFilterStateKmlFilename("filterState.kml");
        geoMission.setInitialStateMode(InitialStateMode.random);

        try {
            cageProcessManager.configure(geoMission);
        } catch (ConfigurationException ce) {
            log.error("Error trying to configure mission, returning. Error: " + ce.getMessage());
            ce.printStackTrace();
            return;
        } catch (IOException ioe) {
            log.error("IO Error trying to configure mission, returning. Error: " + ioe.getMessage());
            ioe.printStackTrace();
            return;
        } catch (Exception e) {
            log.error("Error trying to configure mission, returning");
            e.printStackTrace();
            return;
        }
        log.debug("Configured Geo Mission, continuing");

        /* Client side needs to manage geomission references for callback response */
        missionsMap.put(geoMission.getGeoId(), geoMission);

        /* Reusable test assets */
        asset_a.setId("A");
        asset_a.setProvide_range(true);
        asset_a.setProvide_tdoa(false);
        asset_a.setProvide_aoa(false);
        asset_a.setCurrent_loc(asset_a_coords);

        asset_b.setId("B");
        asset_b.setProvide_range(true);
        asset_b.setProvide_tdoa(true);
        asset_b.setProvide_aoa(true);
        asset_b.setCurrent_loc(asset_b_coords);

        asset_c.setId("C");
        asset_c.setProvide_range(true);
        asset_c.setProvide_tdoa(true);
        asset_c.setProvide_aoa(true);
        asset_c.setCurrent_loc(asset_c_coords);

        asset_d.setId("D");
        asset_d.setProvide_range(true);
        asset_d.setProvide_tdoa(true);
        asset_d.setProvide_aoa(true);
        asset_d.setCurrent_loc(asset_d_coords);

        asset_a.setTdoa_asset_ids(Arrays.asList(new String[]{"B", "C", "D"}));
        asset_b.setTdoa_asset_ids(Arrays.asList(new String[]{"C", "D"}));
        asset_c.setTdoa_asset_ids(Arrays.asList(new String[]{"D"}));

        ATEStats = new SummaryStatistics();
    }

    /* Result callback */
    @Override
    public void result(String geoId, double lat, double lon, double cep_elp_maj, double cep_elp_min, double cep_elp_rot) {
        log.debug("Result -> GeoId: " + geoId + ", Lat: " + lat + ", Lon: " + lon + ", CEP major: " + cep_elp_maj + ", CEP minor: " + cep_elp_min + ", CEP rotation: " + cep_elp_rot);

        // buffer just the latest value - ok for fix tests, need to hold all est since first convergence for track tests
        latest_est_latlon = new double[]{lat, lon};
    }

    /* Result callback */
    @Override
    public void result(ComputeResults results) {
        log.debug("Result [NEW] -> GeoId: " + results.getGeoId() + ", Lat: " + results.getGeolocationResult().getLat() + ", Lon: " + results.getGeolocationResult().getLon() + ", CEP major: " + results.getGeolocationResult().getElp_long() + ", CEP minor: " + results.getGeolocationResult().getElp_short() + ", CEP rotation: " + results.getGeolocationResult().getElp_rot());

        // buffer just the latest value - ok for fix tests, need to hold all est since first convergence for track tests
        latest_est_latlon = new double[]{results.getGeolocationResult().getLat(), results.getGeolocationResult().getLon()};
    }

    public void printPerformance() {
        /* Performance analysis - Average True Error (ATE) */
        Double[] true_lat_lon = this.geoMission.getTarget().getTrue_current_loc();
        double[] true_nth_east = Helpers.convertLatLngToUtmNthingEasting(true_lat_lon[0], true_lat_lon[1]);
        double[] est_nth_east = Helpers.convertLatLngToUtmNthingEasting(latest_est_latlon[0], latest_est_latlon[1]);
        double ate_value = Math.sqrt(Math.pow(true_nth_east[0] - est_nth_east[0], 2) + Math.pow(true_nth_east[1] - est_nth_east[1], 2));
        ATEStats.addValue(ate_value);

        log.debug("ATE following test: " + ATEStats.getMean() + ", StdDev: " + ATEStats.getStandardDeviation());

        /* PRINT RESULTS IN T&E REPORT RESULTS FORMAT - see testoutput.log */
        test_output_log.debug("------------------------------------------");
        GeoMission geoMission = cageProcessManager.getGeoMission();
        test_output_log.debug("Assets:");
        for (Asset asset : geoMission.getAssets().values()) {
            test_output_log.debug(asset.getId() + ": " + asset.getCurrent_loc()[0] + "," + asset.getCurrent_loc()[1]);
        }
        test_output_log.debug("\nTarget:\n" + geoMission.getTarget().getTrue_current_loc()[0] + "," + geoMission.getTarget().getTrue_current_loc()[1]);
        test_output_log.debug("\nTechnique Params:");
        test_output_log.debug("Qu: " + geoMission.getFilterProcessNoise()[0][0]);
        test_output_log.debug("Initial State: " + geoMission.getInitialStateMode().name());

        test_output_log.debug("\nMeasurements:");
        for (Observation obs : geoMission.getObservations().values()) {
            test_output_log.debug(obs.getAssetId() + "_" + obs.getObservationType().name() + ": " + obs.getMeas() + " +-" + obs.getMeas_error());
        }

        ComputeResults computeResults = geoMission.getComputeResults();
        test_output_log.debug("\nLat: " + geoMission.getTarget().getCurrent_loc()[0] + "\nLon: " + geoMission.getTarget().getCurrent_loc()[1] + "\nCEP major: " + computeResults.getGeolocationResult().getElp_long() + "\nCEP minor: " + computeResults.getGeolocationResult().getElp_short() + "\nCEP rotation: " + computeResults.getGeolocationResult().getElp_rot());
        test_output_log.debug("ATE: " + ATEStats.getMean());
    }

    @Test
    public void test_711_Ranges_10() {
        simulatedAssetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedAssetObserver.setTrue_lon(138.596404);
        simulatedAssetObserver.setAoa_rand_factor(0.1);
        simulatedAssetObserver.setTdoa_rand_factor(0.0000001);
        simulatedAssetObserver.setRange_rand_factor(200);
        simulatedAssetObserver.setLat_move(0.0); // STATIC
        simulatedAssetObserver.setLon_move(0.0);

        /* Define circular movement trajectory around the target for asset*/
        List<Double[]> locs = getCircularTrajectory(simulatedAssetObserver.getTrue_lat(), simulatedAssetObserver.getTrue_lon(), 5000);
        asset_a.setLocs(locs);
        Map<String, TestAssetMoving> assets = new HashMap<String, TestAssetMoving>() {{
            put(asset_a.getId(), asset_a);
        }};
        simulatedAssetObserver.setTestAssets(assets);
        /* Add the specified number of observations */
        for (int i=0;i<10;i++){
            simulatedAssetObserver.run();
        }
        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    @Test
    public void test_711b_Ranges_20() {
        simulatedAssetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedAssetObserver.setTrue_lon(138.596404);
        simulatedAssetObserver.setAoa_rand_factor(0.1);
        simulatedAssetObserver.setTdoa_rand_factor(0.0000001);
        simulatedAssetObserver.setRange_rand_factor(200);
        simulatedAssetObserver.setLat_move(0.0); // STATIC
        simulatedAssetObserver.setLon_move(0.0);

        /* Define circular movement trajectory around the target for asset*/
        List<Double[]> locs = getCircularTrajectory(simulatedAssetObserver.getTrue_lat(), simulatedAssetObserver.getTrue_lon(), 5000);
        asset_a.setLocs(locs);
        Map<String, TestAssetMoving> assets = new HashMap<String, TestAssetMoving>() {{
            put(asset_a.getId(), asset_a);
        }};
        simulatedAssetObserver.setTestAssets(assets);
        /* Add the specified number of observations */
        for (int i=0;i<20;i++){
            simulatedAssetObserver.run();
        }
        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    @Test
    public void test_711c_Ranges_30() {
        simulatedAssetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedAssetObserver.setTrue_lon(138.596404);
        simulatedAssetObserver.setAoa_rand_factor(0.1);
        simulatedAssetObserver.setTdoa_rand_factor(0.0000001);
        simulatedAssetObserver.setRange_rand_factor(200);
        simulatedAssetObserver.setLat_move(0.0); // STATIC
        simulatedAssetObserver.setLon_move(0.0);

        /* Define circular movement trajectory around the target for asset*/
        List<Double[]> locs = getCircularTrajectory(simulatedAssetObserver.getTrue_lat(), simulatedAssetObserver.getTrue_lon(), 5000);
        asset_a.setLocs(locs);
        Map<String, TestAssetMoving> assets = new HashMap<String, TestAssetMoving>() {{
            put(asset_a.getId(), asset_a);
        }};
        simulatedAssetObserver.setTestAssets(assets);
        /* Add the specified number of observations */
        for (int i=0;i<30;i++){
            simulatedAssetObserver.run();
        }
        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    @Test
    public void test_711d_Ranges_40() {
        simulatedAssetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedAssetObserver.setTrue_lon(138.596404);
        simulatedAssetObserver.setAoa_rand_factor(0.1);
        simulatedAssetObserver.setTdoa_rand_factor(0.0000001);
        simulatedAssetObserver.setRange_rand_factor(200);
        simulatedAssetObserver.setLat_move(0.0); // STATIC
        simulatedAssetObserver.setLon_move(0.0);

        /* Define circular movement trajectory around the target for asset*/
        List<Double[]> locs = getCircularTrajectory(simulatedAssetObserver.getTrue_lat(), simulatedAssetObserver.getTrue_lon(), 5000);
        asset_a.setLocs(locs);
        Map<String, TestAssetMoving> assets = new HashMap<String, TestAssetMoving>() {{
            put(asset_a.getId(), asset_a);
        }};
        simulatedAssetObserver.setTestAssets(assets);
        /* Add the specified number of observations */
        for (int i=0;i<40;i++){
            simulatedAssetObserver.run();
        }
        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    @Test
    public void test_711e_Ranges_50() {
        simulatedAssetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedAssetObserver.setTrue_lon(138.596404);
        simulatedAssetObserver.setAoa_rand_factor(0.1);
        simulatedAssetObserver.setTdoa_rand_factor(0.0000001);
        simulatedAssetObserver.setRange_rand_factor(200);
        simulatedAssetObserver.setLat_move(0.0); // STATIC
        simulatedAssetObserver.setLon_move(0.0);

        /* Define circular movement trajectory around the target for asset*/
        List<Double[]> locs = getCircularTrajectory(simulatedAssetObserver.getTrue_lat(), simulatedAssetObserver.getTrue_lon(), 5000);
        asset_a.setLocs(locs);
        Map<String, TestAssetMoving> assets = new HashMap<String, TestAssetMoving>() {{
            put(asset_a.getId(), asset_a);
        }};
        simulatedAssetObserver.setTestAssets(assets);
        /* Add the specified number of observations */
        for (int i=0;i<50;i++){
            simulatedAssetObserver.run();
        }
        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    public List<Double[]> getCircularTrajectory(double centre_lat, double centre_lon, double radius) {
        List<Double[]> trajectory = new ArrayList<Double[]>();
        double[] utmNthEasting = Helpers.convertLatLngToUtmNthingEasting(centre_lat, centre_lon);
        log.debug("Asset, centre lat/lon: "+centre_lat+","+centre_lon+", UTM NTH/EASTING: "+utmNthEasting[0]+","+utmNthEasting[1]);
        Object[] zones = Helpers.getUtmLatZoneLonZone(centre_lat, centre_lon);
        char latZone = (char)zones[0];
        int lonZone = (int)zones[1];
        for (double theta = (1 / 2) * Math.PI; theta <= (5 / 2) * Math.PI; theta += 0.2) {
            UTMRef utmMeas = new UTMRef(radius * Math.cos(theta) + utmNthEasting[1], radius * Math.sin(theta) + utmNthEasting[0], latZone, lonZone);
            LatLng ltln = utmMeas.toLatLng();
            Double[] assetPoint = {ltln.getLat(), ltln.getLng()};
            log.debug("Asset, adding to trajectory: "+assetPoint[0]+","+assetPoint[1]);
            trajectory.add(assetPoint);
        }
        return trajectory;
    }
}

