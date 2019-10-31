package tech.tgo.fuzer.fix;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tgo.fuzer.FuzerListener;
import tech.tgo.fuzer.FuzerProcess;
import tech.tgo.fuzer.model.FuzerMode;
import tech.tgo.fuzer.model.GeoMission;
import tech.tgo.fuzer.model.Target;
import tech.tgo.fuzer.util.ConfigurationException;
import tech.tgo.fuzer.util.SimulatedTargetObserver;
import tech.tgo.fuzer.util.TestAsset;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class AllObservationITs implements FuzerListener {

    private static final Logger log = LoggerFactory.getLogger(AllObservationITs.class);

    Map<String,GeoMission> fuzerMissions = new HashMap<String,GeoMission>();

    FuzerProcess fuzerProcess = new FuzerProcess(this);

    SimulatedTargetObserver simulatedTargetObserver = new SimulatedTargetObserver();

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

    @Before
    public void configure() {
        simulatedTargetObserver.setFuzerProcess(fuzerProcess);

        /* Configure the intended mission */
        geoMission = new GeoMission();
        geoMission.setFuzerMode(FuzerMode.fix);
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
            log.error("Error trying to configure mission, returning");
            e.printStackTrace();
            return;
        }
        log.debug("Configured Geo Mission, continuing");

        /* Client side needs to manage geomission references for callback response */
        fuzerMissions.put(geoMission.getGeoId(), geoMission);

        /* Create some reusable test assets */
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

        asset_a.setTdoa_asset_ids(Arrays.asList(new String[]{"B","C","D"}));
        asset_b.setTdoa_asset_ids(Arrays.asList(new String[]{"C","D"}));
        asset_c.setTdoa_asset_ids(Arrays.asList(new String[]{"D"}));
    }

    /* Result callback */
    @Override
    public void result(String geoId, double Xk1, double Xk2, double Xk3, double Xk4) {
        log.debug("Raw Result: GeoId: "+geoId+", Xk1: "+Xk1+", Xk2: "+Xk2+", Xk3: "+Xk3+", Xk4: "+Xk4);
        GeoMission geoMission = fuzerMissions.get(geoId);
        UTMRef utm = new UTMRef(Xk1,Xk2, geoMission.getLatZone(), geoMission.getLonZone());
        LatLng ltln = utm.toLatLng();
        log.debug("Result: Lat: "+ltln.getLat()+", Lon: "+ltln.getLng());
    }

    @Test
    public void testBottom() {
        simulatedTargetObserver.setTrue_lat(-31.98);  // BOTTOM
        simulatedTargetObserver.setTrue_lon(116.000);
        simulatedTargetObserver.setAoa_rand_factor(0.0);
        simulatedTargetObserver.setTdoa_rand_factor(0.0000001);
        simulatedTargetObserver.setRange_rand_factor(200);
        simulatedTargetObserver.setLat_move(0.0); // STATIC
        simulatedTargetObserver.setLon_move(0.0);
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
            Thread thread = fuzerProcess.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testBottom_TwoAssets() {
        simulatedTargetObserver.setTrue_lat(-31.98); // BOTTOM
        simulatedTargetObserver.setTrue_lon(116.000);
        simulatedTargetObserver.setAoa_rand_factor(0.0);
        simulatedTargetObserver.setTdoa_rand_factor(0.0000001);
        simulatedTargetObserver.setRange_rand_factor(200);
        simulatedTargetObserver.setLat_move(0.0); // STATIC
        simulatedTargetObserver.setLon_move(0.0);

        geoMission.setFilterConvergenceResidualThreshold(0.01);

        Map<String, TestAsset> assets = new HashMap<String, TestAsset>()
        {{
            put(asset_a.getId(), asset_a);
            put(asset_b.getId(), asset_b);
            asset_a.setTdoa_asset_ids(Arrays.asList(new String[]{"B"}));
            asset_b.setTdoa_asset_ids(Arrays.asList(new String[]{}));
        }};
        simulatedTargetObserver.setTestAssets(assets);
        simulatedTargetObserver.run();

        try {
            Thread thread = fuzerProcess.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testLeft() {
        simulatedTargetObserver.setTrue_lat(-31.98); // LEFT
        simulatedTargetObserver.setTrue_lon(115.80);
        simulatedTargetObserver.setAoa_rand_factor(0.1);
        simulatedTargetObserver.setRange_rand_factor(50);
        simulatedTargetObserver.setTdoa_rand_factor(0.0000001);
        simulatedTargetObserver.setLat_move(0.0); // STATIC
        simulatedTargetObserver.setLon_move(0.0);

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
            Thread thread = fuzerProcess.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testTopRight() {
        simulatedTargetObserver.setTrue_lat(-31.7); // TOPRIGHT
        simulatedTargetObserver.setTrue_lon(116.08);
        simulatedTargetObserver.setAoa_rand_factor(0.1);
        simulatedTargetObserver.setRange_rand_factor(50);
        simulatedTargetObserver.setTdoa_rand_factor(0.0000001);
        simulatedTargetObserver.setLat_move(0.0); // STATIC
        simulatedTargetObserver.setLon_move(0.0);
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
            Thread thread = fuzerProcess.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testTop() {
        simulatedTargetObserver.setTrue_lat(-31.7); // TOP
        simulatedTargetObserver.setTrue_lon(115.80);
        simulatedTargetObserver.setAoa_rand_factor(0.1);
        simulatedTargetObserver.setRange_rand_factor(50);
        simulatedTargetObserver.setTdoa_rand_factor(0.0000001);
        simulatedTargetObserver.setLat_move(0.0); // STATIC
        simulatedTargetObserver.setLon_move(0.0);
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
            Thread thread = fuzerProcess.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testRight() {
        simulatedTargetObserver.setTrue_lat(-31.895); // RIGHT
        simulatedTargetObserver.setTrue_lon(116.124);
        simulatedTargetObserver.setAoa_rand_factor(0.1);
        simulatedTargetObserver.setRange_rand_factor(50);
        simulatedTargetObserver.setTdoa_rand_factor(0.0000001);
        simulatedTargetObserver.setLat_move(0.0); // STATIC
        simulatedTargetObserver.setLon_move(0.0);
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
            Thread thread = fuzerProcess.start();
            thread.join();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
