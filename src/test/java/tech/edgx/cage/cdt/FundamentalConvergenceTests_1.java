package tech.edgx.cage.cdt;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.edgx.cage.CageListener;
import tech.edgx.cage.CageProcessManager;
import tech.edgx.cage.compute.ComputeResults;
import tech.edgx.cage.model.*;
import tech.edgx.cage.util.ConfigurationException;
import tech.edgx.cage.util.Helpers;
import tech.edgx.cage.util.TestAsset;
import tech.edgx.cage.util.SimulatedTargetObserver;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class FundamentalConvergenceTests_1 implements CageListener {

    private static final Logger log = LoggerFactory.getLogger(FundamentalConvergenceTests_1.class);
    private static final Logger test_output_log = LoggerFactory.getLogger("test_output");

    Map<String, GeoMission> missionsMap = new HashMap<String,GeoMission>();

    CageProcessManager cageProcessManager = new CageProcessManager(this);

    SimulatedTargetObserver simulatedTargetObserver = new SimulatedTargetObserver();

    /* Some common asset coords to reuse */
    double[] asset_a_coords = new double[]{-34.940732, 138.624252}; // BOTTOM RIGHT
    double[] asset_b_coords = new double[]{-34.942842, 138.583395}; // BOTTOM LEFT
    double[] asset_c_coords = new double[]{-34.898546, 138.639188}; // TOP RIGHT

    TestAsset asset_a = new TestAsset();
    TestAsset asset_b = new TestAsset();
    TestAsset asset_c = new TestAsset();
    TestAsset asset_d = new TestAsset();

    GeoMission geoMission;

    SummaryStatistics ATEStats;
    double[] latest_est_latlon;

    @Before
    public void configure() {
        /* Test specific configuration */
        simulatedTargetObserver.setCageProcessManager(cageProcessManager);

        /* Configure the intended mission */
        geoMission = new GeoMission();
        geoMission.setMissionMode(MissionMode.fix);
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

        /* Reusable test assets */
        asset_a.setId("A");
        asset_a.setProvide_range(false);
        asset_a.setProvide_tdoa(false);
        asset_a.setProvide_aoa(false);
        asset_a.setCurrent_loc(asset_a_coords);

        asset_b.setId("B");
        asset_b.setProvide_range(false);
        asset_b.setProvide_tdoa(false);
        asset_b.setProvide_aoa(false);
        asset_b.setCurrent_loc(asset_b_coords);

        asset_c.setId("C");
        asset_c.setProvide_range(false);
        asset_c.setProvide_tdoa(false);
        asset_c.setProvide_aoa(false);
        asset_c.setCurrent_loc(asset_c_coords);

        asset_a.setTdoa_asset_ids(Arrays.asList(new String[]{"B","C","D"}));
        asset_b.setTdoa_asset_ids(Arrays.asList(new String[]{"C","D"}));
        asset_c.setTdoa_asset_ids(Arrays.asList(new String[]{"D"}));

        simulatedTargetObserver.setAoa_rand_factor(0.0);
        simulatedTargetObserver.setTdoa_rand_factor(0.0);
        simulatedTargetObserver.setRange_rand_factor(0);

        ATEStats = new SummaryStatistics();
    }

    /* Result callback */
    @Override
    public void result(String geoId, double lat, double lon, double cep_elp_maj, double cep_elp_min, double cep_elp_rot) {
        log.debug("Result -> GeoId: "+geoId+", Lat: "+lat+", Lon: "+lon+", CEP major: "+cep_elp_maj+", CEP minor: "+cep_elp_min+", CEP rotation: "+cep_elp_rot);

        // buffer just the latest value - ok for fix tests, need to hold all est since first convergence for track tests
        latest_est_latlon = new double[]{lat,lon};
    }

    /* Result callback */
    @Override
    public void result(ComputeResults results) {
        log.debug("Result [NEW] -> GeoId: "+results.getGeoId()+", Lat: "+results.getGeolocationResult().getLat()+", Lon: "+results.getGeolocationResult().getLon()+", CEP major: "+results.getGeolocationResult().getElp_long()+", CEP minor: "+results.getGeolocationResult().getElp_short()+", CEP rotation: "+results.getGeolocationResult().getElp_rot());

        // buffer just the latest value - ok for fix tests, need to hold all est since first convergence for track tests
        latest_est_latlon = new double[]{results.getGeolocationResult().getLat(),results.getGeolocationResult().getLon()};
    }

    public void printPerformance() {
        /* Performance analysis - Average True Error (ATE) */
        Double[] true_lat_lon = this.geoMission.getTarget().getTrue_current_loc();
        double[] true_nth_east = Helpers.convertLatLngToUtmNthingEasting(true_lat_lon[0], true_lat_lon[1]);
        double[] est_nth_east = Helpers.convertLatLngToUtmNthingEasting(latest_est_latlon[0], latest_est_latlon[1]);
        double ate_value = Math.sqrt(Math.pow(true_nth_east[0] - est_nth_east[0], 2) + Math.pow(true_nth_east[1] - est_nth_east[1], 2));
        ATEStats.addValue(ate_value);

        log.debug("ATE following test: "+ ATEStats.getMean()+", StdDev: "+ATEStats.getStandardDeviation());

        /* PRINT RESULTS IN T&E REPORT RESULTS FORMAT - see testoutput.log */
        test_output_log.debug("------------------------------------------");
        GeoMission geoMission = cageProcessManager.getGeoMission();
        test_output_log.debug("Assets:");
        for (Asset asset : geoMission.getAssets().values()) {
            test_output_log.debug(asset.getId()+": "+asset.getCurrent_loc()[0]+","+asset.getCurrent_loc()[1]);
        }
        test_output_log.debug("\nTarget:\n"+geoMission.getTarget().getTrue_current_loc()[0]+","+geoMission.getTarget().getTrue_current_loc()[1]);
        test_output_log.debug("\nTechnique Params:");
        test_output_log.debug("Qu: "+geoMission.getFilterProcessNoise()[0][0]);
        test_output_log.debug("Initial State: "+geoMission.getInitialStateMode().name());

        test_output_log.debug("\nMeasurements:");
        for (Observation obs : geoMission.getObservations().values()) {
            test_output_log.debug(obs.getAssetId()+"_"+obs.getObservationType().name()+": "+obs.getMeas()+" +-"+obs.getMeas_error());
        }

        ComputeResults computeResults = geoMission.getComputeResults();
        test_output_log.debug("\nLat: "+geoMission.getTarget().getCurrent_loc()[0]+"\nLon: "+geoMission.getTarget().getCurrent_loc()[1]+"\nCEP major: "+computeResults.getGeolocationResult().getElp_long()+"\nCEP minor: "+computeResults.getGeolocationResult().getElp_short()+"\nCEP rotation: "+computeResults.getGeolocationResult().getElp_rot());
        test_output_log.debug("ATE: "+ATEStats.getMean());
    }

    @Test
    public void test111() {
        /* 1.1.1 Converge to AOA */
        simulatedTargetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedTargetObserver.setTrue_lon(138.596404);
        asset_a.setProvide_aoa(true);
        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
        }};

        simulatedTargetObserver.setTestAssets(assets);
        simulatedTargetObserver.run();

        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    @Test
    public void test112() {
        /* 1.1.2 Converge to Range */
        simulatedTargetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedTargetObserver.setTrue_lon(138.596404);
        asset_a.setProvide_range(true);
        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
        }};
        simulatedTargetObserver.setTestAssets(assets);
        simulatedTargetObserver.run();

        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    @Test
    public void test113() {
        /* 1.1.3 Converge to TDOA */
        simulatedTargetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedTargetObserver.setTrue_lon(138.596404);
        asset_a.setProvide_tdoa(true);
        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
            asset_a.setTdoa_asset_ids(Arrays.asList(new String[]{"B"}));
        }};
        simulatedTargetObserver.setTestAssets(assets);
        simulatedTargetObserver.run();

        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    @Test
    public void test121() {
        /* 1.2.1 Converge to AOA, AOA */
        simulatedTargetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedTargetObserver.setTrue_lon(138.596404);
        asset_a.setProvide_aoa(true);
        asset_b.setProvide_aoa(true);
        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
        }};
        simulatedTargetObserver.setTestAssets(assets);
        simulatedTargetObserver.run();

        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    @Test
    public void test122() {
        /* 1.2.2 Converge to AOA, TDOA */
        simulatedTargetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedTargetObserver.setTrue_lon(138.596404);
        asset_a.setProvide_tdoa(true);
        asset_a.setProvide_aoa(true);
        asset_b.setProvide_aoa(true);
        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
            asset_a.setTdoa_asset_ids(Arrays.asList(new String[]{"B"}));
        }};
        simulatedTargetObserver.setTestAssets(assets);
        simulatedTargetObserver.run();

        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    @Test
    public void test123() {
        /* 1.2.3 Converge to AOA, Range */
        // NOTE: sensitive to init conditions may sometimes chose wrong branch, try test 123a with TOP LEFT init conditions to
        simulatedTargetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedTargetObserver.setTrue_lon(138.596404);
        asset_a.setProvide_aoa(true);
        asset_b.setProvide_range(true);
        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
        }};
        simulatedTargetObserver.setTestAssets(assets);
        simulatedTargetObserver.run();

        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    @Test
    public void test123a() {
        /* 1.2.3 Converge to AOA, Range */
        // NOTE: sensitive to init conditions may sometimes chose wrong branch
        simulatedTargetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedTargetObserver.setTrue_lon(138.596404);
        asset_a.setProvide_aoa(true);
        asset_b.setProvide_range(true);

        geoMission.setInitialStateMode(InitialStateMode.specified);
        geoMission.setFilterSpecificInitialLat(-34.90);
        geoMission.setFilterSpecificInitialLon(138.0);

        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
        }};
        simulatedTargetObserver.setTestAssets(assets);
        simulatedTargetObserver.run();

        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    @Test
    public void test123b() {
        /* 1.2.3 Converge to AOA, Range */
        // NOTE: sensitive to init conditions may sometimes chose wrong branch
        simulatedTargetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedTargetObserver.setTrue_lon(138.596404);
        asset_a.setProvide_aoa(true);
        asset_b.setProvide_range(true);

        geoMission.setInitialStateMode(InitialStateMode.bottom_left);

        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
        }};
        simulatedTargetObserver.setTestAssets(assets);
        simulatedTargetObserver.run();

        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    @Test
    public void test123d() {
        /* 1.2.3 Converge to AOA, Range */
        // NOTE: sensitive to init conditions may sometimes chose wrong branch
        // The box_single_out use case may only end up being useful for engineering purposes
        simulatedTargetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedTargetObserver.setTrue_lon(138.596404);
        asset_a.setProvide_aoa(true);
        asset_b.setProvide_range(true);

        geoMission.setInitialStateMode(InitialStateMode.box_single_out);

        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
        }};
        simulatedTargetObserver.setTestAssets(assets);
        simulatedTargetObserver.run();

        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    @Test
    public void test123c() {
        /* 1.2.3 Converge to AOA, Range */
        // NOTE: sensitive to init conditions may sometimes chose wrong branch
        simulatedTargetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedTargetObserver.setTrue_lon(138.596404);
        asset_a.setProvide_aoa(true);
        asset_b.setProvide_range(true);

        geoMission.setInitialStateMode(InitialStateMode.box_all_out);

        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
        }};
        simulatedTargetObserver.setTestAssets(assets);
        simulatedTargetObserver.run();

        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    // v.sensitive to init conditions, can fail to converge.
    // Will require the boxing technique
    @Test
    public void test124() {
        /* 1.2.4 Converge to TDOA, TDOA */
        simulatedTargetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedTargetObserver.setTrue_lon(138.596404);
        asset_a.setProvide_tdoa(true);
        asset_b.setProvide_tdoa(true);
        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
            put(asset_c.getId(), asset_c);
            asset_a.setTdoa_asset_ids(Arrays.asList(new String[]{"B"}));
            asset_b.setTdoa_asset_ids(Arrays.asList(new String[]{"C"}));
        }};
        simulatedTargetObserver.setTestAssets(assets);
        simulatedTargetObserver.run();

        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    @Test
    public void test125() {
        /* 1.2.5 Converge to TDOA, Range */
        simulatedTargetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedTargetObserver.setTrue_lon(138.596404);
        asset_a.setProvide_tdoa(true);
        asset_b.setProvide_range(true);
        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
            asset_a.setTdoa_asset_ids(Arrays.asList(new String[]{"B"}));
        }};
        simulatedTargetObserver.setTestAssets(assets);
        simulatedTargetObserver.run();

        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    @Test
    public void test126() {
        /* 1.2.6 Converge to Range, Range */
        simulatedTargetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedTargetObserver.setTrue_lon(138.596404);
        asset_a.setProvide_range(true);
        asset_b.setProvide_range(true);
        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
        }};
        simulatedTargetObserver.setTestAssets(assets);
        simulatedTargetObserver.run();

        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }


    @Test
    public void test131() {
        /* 1.3.1 Converge to AOA, TDOA, Range */
        simulatedTargetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedTargetObserver.setTrue_lon(138.596404);
        asset_a.setProvide_aoa(true);
        asset_b.setProvide_tdoa(true);
        asset_c.setProvide_range(true);
        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
            put(asset_c.getId(), asset_c);
            asset_b.setTdoa_asset_ids(Arrays.asList(new String[]{"C"}));
        }};
        simulatedTargetObserver.setTestAssets(assets);
        simulatedTargetObserver.run();

        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    @Test
    public void test131a() {
        /* 1.3.1 Converge to AOA, TDOA, Range */
        simulatedTargetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedTargetObserver.setTrue_lon(138.596404);
        asset_a.setProvide_aoa(true);
        asset_b.setProvide_tdoa(true);
        asset_c.setProvide_range(true);
        geoMission.setInitialStateMode(InitialStateMode.box_all_out);
        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
            put(asset_c.getId(), asset_c);
            asset_b.setTdoa_asset_ids(Arrays.asList(new String[]{"C"}));
        }};
        simulatedTargetObserver.setTestAssets(assets);
        simulatedTargetObserver.run();

        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }

    @Test
    public void test131b() {
        /* 1.3.1 Converge to AOA x2, TDOA, Range */
        // USED to show that GDOP form 1.3.1 led to selection of wrong intersection point
        simulatedTargetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedTargetObserver.setTrue_lon(138.596404);
        asset_a.setProvide_aoa(true);
        asset_b.setProvide_tdoa(true);
        asset_c.setProvide_range(true);
        asset_c.setProvide_aoa(true);
        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
            put(asset_c.getId(), asset_c);
            asset_b.setTdoa_asset_ids(Arrays.asList(new String[]{"C"}));
        }};
        simulatedTargetObserver.setTestAssets(assets);
        simulatedTargetObserver.run();

        try {
            Thread thread = cageProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        printPerformance();
    }
}
