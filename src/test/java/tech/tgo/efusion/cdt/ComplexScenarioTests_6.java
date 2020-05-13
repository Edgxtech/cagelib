package tech.tgo.efusion.cdt;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tgo.efusion.EfusionListener;
import tech.tgo.efusion.EfusionProcessManager;
import tech.tgo.efusion.compute.ComputeResults;
import tech.tgo.efusion.model.*;
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
public class ComplexScenarioTests_6 implements EfusionListener {

    private static final Logger log = LoggerFactory.getLogger(ComplexScenarioTests_6.class);
    private static final Logger test_output_log = LoggerFactory.getLogger("test_output");

    Map<String,GeoMission> missionsMap = new HashMap<String,GeoMission>();

    EfusionProcessManager efusionProcessManager = new EfusionProcessManager(this);

    SimulatedTargetObserver simulatedTargetObserver = new SimulatedTargetObserver();

    /* Some common asset coords to reuse */
    double[] asset_a_coords = new double[]{-34.940732, 138.624252}; // BOTTOM RIGHT
    double[] asset_b_coords = new double[]{-34.942842, 138.583395}; // BOTTOM LEFT
    double[] asset_c_coords = new double[]{-34.898546, 138.639188}; // TOP RIGHT
    double[] asset_d_coords = new double[]{-34.898546, 138.61}; // TOP
    double[] asset_e_coords = new double[]{-34.898546, 138.57}; // TOP LEFT
    double[] asset_f_coords = new double[]{-34.91, 138.56}; // LEFT
    double[] asset_g_coords = new double[]{-34.91, 138.64}; // RIGHT
    double[] asset_h_coords = new double[]{-34.898546, 138.61}; // BOTTOM

    TestAsset asset_a = new TestAsset();
    TestAsset asset_b = new TestAsset();
    TestAsset asset_c = new TestAsset();
    TestAsset asset_d = new TestAsset();
    TestAsset asset_e = new TestAsset();
    TestAsset asset_f = new TestAsset();
    TestAsset asset_g = new TestAsset();
    TestAsset asset_h = new TestAsset();

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
        asset_a.setProvide_range(true);
        asset_a.setProvide_tdoa(true);
        asset_a.setProvide_aoa(true);
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

        asset_e.setId("E");
        asset_e.setProvide_range(true);
        asset_e.setProvide_tdoa(true);
        asset_e.setProvide_aoa(true);
        asset_e.setCurrent_loc(asset_e_coords);

        asset_f.setId("F");
        asset_f.setProvide_range(true);
        asset_f.setProvide_tdoa(true);
        asset_f.setProvide_aoa(true);
        asset_f.setCurrent_loc(asset_f_coords);

        asset_g.setId("G");
        asset_g.setProvide_range(true);
        asset_g.setProvide_tdoa(true);
        asset_g.setProvide_aoa(true);
        asset_g.setCurrent_loc(asset_g_coords);

        asset_h.setId("H");
        asset_h.setProvide_range(true);
        asset_h.setProvide_tdoa(true);
        asset_h.setProvide_aoa(true);
        asset_h.setCurrent_loc(asset_h_coords);

        asset_a.setTdoa_asset_ids(Arrays.asList(new String[]{"B","C","D"}));
        asset_b.setTdoa_asset_ids(Arrays.asList(new String[]{"C","D"}));
        asset_c.setTdoa_asset_ids(Arrays.asList(new String[]{"D"}));

        asset_d.setTdoa_asset_ids(Arrays.asList(new String[]{"B","C","D"}));
        asset_e.setTdoa_asset_ids(Arrays.asList(new String[]{"C","D"}));
        asset_f.setTdoa_asset_ids(Arrays.asList(new String[]{"D"}));

        asset_g.setTdoa_asset_ids(Arrays.asList(new String[]{"B","C","D"}));
        asset_h.setTdoa_asset_ids(Arrays.asList(new String[]{"C","D"}));

        simulatedTargetObserver.setAoa_rand_factor(0.2);
        simulatedTargetObserver.setTdoa_rand_factor(0.000005); // ~5ms
        simulatedTargetObserver.setRange_rand_factor(100);

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

//    public void printPerformance() {
//        /* Performance analysis - Average True Error (ATE) */
//        Double[] true_lat_lon = this.geoMission.getTarget().getTrue_current_loc();
//        double[] true_nth_east = Helpers.convertLatLngToUtmNthingEasting(true_lat_lon[0], true_lat_lon[1]);
//        double[] est_nth_east = Helpers.convertLatLngToUtmNthingEasting(latest_est_latlon[0], latest_est_latlon[1]);
//        double ate_value = Math.sqrt(Math.pow(true_nth_east[0] - est_nth_east[0], 2) + Math.pow(true_nth_east[1] - est_nth_east[1], 2));
//        ATEStats.addValue(ate_value);
//
//        log.debug("ATE following test: "+ ATEStats.getMean()+", StdDev: "+ATEStats.getStandardDeviation());
//    }
public void printPerformance() {
        /* Performance analysis - Average True Error (ATE) */
    Double[] true_lat_lon = this.geoMission.getTarget().getTrue_current_loc();
    double[] true_nth_east = Helpers.convertLatLngToUtmNthingEasting(true_lat_lon[0], true_lat_lon[1]);
    double[] est_nth_east = Helpers.convertLatLngToUtmNthingEasting(latest_est_latlon[0], latest_est_latlon[1]);
    double ate_value = Math.sqrt(Math.pow(true_nth_east[0] - est_nth_east[0], 2) + Math.pow(true_nth_east[1] - est_nth_east[1], 2));
    ATEStats.addValue(ate_value);

    log.debug("ATE following test: "+ ATEStats.getMean()+", StdDev: "+ATEStats.getStandardDeviation());

        /* PRINT RESULTS IN T&E REPORT RESULTS FORMAT - check /var/log/efusionlib/efusionlib_testoutput.log */
    test_output_log.debug("------------------------------------------");
    GeoMission geoMission = efusionProcessManager.getGeoMission();
    test_output_log.debug("Assets:");
    for (Asset asset : geoMission.getAssets().values()) {
        test_output_log.debug(asset.getId()+": "+asset.getCurrent_loc()[0]+","+asset.getCurrent_loc()[1]);
    }
    test_output_log.debug("\nTarget:\n"+geoMission.getTarget().getTrue_current_loc()[0]+","+geoMission.getTarget().getTrue_current_loc()[1]);
    test_output_log.debug("\nTechnique Params:");
    test_output_log.debug("Qu: "+geoMission.getFilterProcessNoise()[0][0]);
    test_output_log.debug("\nMeasurements:");
    for (Observation obs : geoMission.getObservations().values()) {
        test_output_log.debug(obs.getAssetId()+"_"+obs.getObservationType().name()+": "+obs.getMeas()+" +-"+obs.getMeas_error());
    }

    test_output_log.debug("\nLat: "+geoMission.getTarget().getCurrent_loc()[0]+"\nLon: "+geoMission.getTarget().getCurrent_loc()[1]+"\nCEP major: "+geoMission.getTarget().getElp_major()+"\nCEP minor: "+geoMission.getTarget().getElp_minor()+"\nCEP rotation: "+geoMission.getTarget().getElp_rot());
    test_output_log.debug("ATE: "+ATEStats.getMean());
}

    @Test
    public void test611() {
        /* 6.1.1 Converge to Complex Middle */
        simulatedTargetObserver.setTrue_lat(-34.916327); // Middle
        simulatedTargetObserver.setTrue_lon(138.625);
        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
            put(asset_c.getId(), asset_c);
            put(asset_d.getId(), asset_d);
            put(asset_e.getId(), asset_e);
            put(asset_f.getId(), asset_f);
            put(asset_g.getId(), asset_g);
            put(asset_h.getId(), asset_h);
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
    public void test612() {
        /* 6.1.2 Converge to Complex Top */
        simulatedTargetObserver.setTrue_lat(-34.816327); // TOP
        simulatedTargetObserver.setTrue_lon(138.596404);
        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
            put(asset_c.getId(), asset_c);
            put(asset_d.getId(), asset_d);
            put(asset_e.getId(), asset_e);
            put(asset_f.getId(), asset_f);
            put(asset_g.getId(), asset_g);
            put(asset_h.getId(), asset_h);
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
    public void test613() {
        /* 6.1.2 Converge to Complex Right */
        simulatedTargetObserver.setTrue_lat(-34.916327); // RIGHT
        simulatedTargetObserver.setTrue_lon(138.716404);
        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
            put(asset_c.getId(), asset_c);
            put(asset_d.getId(), asset_d);
            put(asset_e.getId(), asset_e);
            put(asset_f.getId(), asset_f);
            put(asset_g.getId(), asset_g);
            put(asset_h.getId(), asset_h);
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
}
