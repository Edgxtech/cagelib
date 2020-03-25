package tech.tgo.efusion.cdt;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tgo.efusion.EfusionListener;
import tech.tgo.efusion.EfusionProcessManager;
import tech.tgo.efusion.model.GeoMission;
import tech.tgo.efusion.model.MissionMode;
import tech.tgo.efusion.model.Target;
import tech.tgo.efusion.util.ConfigurationException;
import tech.tgo.efusion.util.Helpers;
import tech.tgo.efusion.util.SimulatedTargetObserver;
import tech.tgo.efusion.util.TestAsset;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Timothy Edge (timmyedge)
 */
public class FundamentalConvergenceTests implements EfusionListener {

    private static final Logger log = LoggerFactory.getLogger(tech.tgo.efusion.fix.AllObservationFixITs.class);

    Map<String,GeoMission> missionsMap = new HashMap<String,GeoMission>();

    EfusionProcessManager efusionProcessManager = new EfusionProcessManager(this);

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
        simulatedTargetObserver.setEfusionProcessManager(efusionProcessManager);

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

        // Optional test
//        geoMission.setFilterUseSpecificInitialCondition(true);
//        geoMission.setFilterSpecificInitialLat(-32.0);
//        geoMission.setFilterSpecificInitialLon(116.9);

        try {
            efusionProcessManager.configure(geoMission);
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

    public void printPerformance() {
        /* Performance analysis - Average True Error (ATE) */
        Double[] true_lat_lon = this.geoMission.getTarget().getTrue_current_loc();
        double[] true_nth_east = Helpers.convertLatLngToUtmNthingEasting(true_lat_lon[0], true_lat_lon[1]);
        double[] est_nth_east = Helpers.convertLatLngToUtmNthingEasting(latest_est_latlon[0], latest_est_latlon[1]);
        double ate_value = Math.sqrt(Math.pow(true_nth_east[0] - est_nth_east[0], 2) + Math.pow(true_nth_east[1] - est_nth_east[1], 2));
        ATEStats.addValue(ate_value);

        log.debug("ATE following test: "+ ATEStats.getMean()+", StdDev: "+ATEStats.getStandardDeviation());
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
            Thread thread = efusionProcessManager.start();
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
            Thread thread = efusionProcessManager.start();
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
            Thread thread = efusionProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
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
            Thread thread = efusionProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
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
            Thread thread = efusionProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
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
            Thread thread = efusionProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test123a() {
        /* 1.2.3 Converge to AOA, Range */
        // NOTE: sensitive to init conditions may sometimes chose wrong branch, try test 123a with TOP LEFT init conditions to
        simulatedTargetObserver.setTrue_lat(-34.916327); // TOP LEFT
        simulatedTargetObserver.setTrue_lon(138.596404);
        asset_a.setProvide_aoa(true);
        asset_b.setProvide_range(true);

        geoMission.setFilterUseSpecificInitialCondition(true);
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
            Thread thread = efusionProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

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
            Thread thread = efusionProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
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
            Thread thread = efusionProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
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
            Thread thread = efusionProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
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
            Thread thread = efusionProcessManager.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
