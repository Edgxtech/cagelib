package tech.tgo.fuzer;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tgo.fuzer.util.MovingTargetObserver;
import tech.tgo.fuzer.model.FuzerMode;
import tech.tgo.fuzer.model.GeoMission;
import tech.tgo.fuzer.model.Target;
import tech.tgo.fuzer.util.ConfigurationException;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

public class MovingTargetITs implements FuzerListener {

    private static final Logger log = LoggerFactory.getLogger(MovingTargetITs.class);

    Map<String,GeoMission> fuzerMissions = new HashMap<String,GeoMission>();

    FuzerProcess fuzerProcess = new FuzerProcess(this);

    MovingTargetObserver movingTargetObserver = new MovingTargetObserver();

    Timer timer = new Timer();

    /* Some common asset coords to reuse */
    double[] asset_a_coords = new double[]{-31.9, 115.98};
    double[] asset_b_coords = new double[]{-31.88, 115.97};

    @Before
    public void configure() {
        movingTargetObserver.setFuzerProcess(fuzerProcess);

        /* Configure the intended mission */
        GeoMission geoMission = new GeoMission();
        geoMission.setFuzerMode(FuzerMode.track);
        geoMission.setTarget(new Target("<TGT_ID>","<TGT-NAME>"));
        geoMission.setGeoId("<GEOID>");
        geoMission.setShowMeas(true);
        geoMission.setShowCEPs(true);
        geoMission.setShowGEOs(true);
        geoMission.setOutputKml(true);
        geoMission.setOutputKmlFilename("geoOutput.kml");
        geoMission.setShowTrueLoc(true);

        /* These configs are available for optional override */
        geoMission.setDispatchResultsPeriod(new Long(1000)); // Default: 1000
        geoMission.setFilterThrottle(null); // Default is null
        geoMission.setFilterConvergenceResidualThreshold(0.01); // Default: 0.01

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
    }

    /* Show output */
    @Override
    public void result(String geoId, double Xk1, double Xk2, double Xk3, double Xk4) {
        log.debug("RAW RESULT: GeoId: "+geoId+", Xk1: "+Xk1+", Xk2: "+Xk2+", Xk3: "+Xk3+", Xk4: "+Xk4);
        GeoMission geoMission = fuzerMissions.get(geoId);
        UTMRef utm = new UTMRef(Xk1,Xk2, geoMission.getLatZone(), geoMission.getLonZone());
        LatLng ltln = utm.toLatLng();
        log.debug("Result: Lat: "+ltln.getLat()+", Lon: "+ltln.getLng());
    }

    @Test
    public void testMoverNorthEast() {
        movingTargetObserver.setTrue_lat(-31.929670455929934);  /// LEFT LEFT
        movingTargetObserver.setTrue_lon(115.79549188891419);
        movingTargetObserver.setAoa_rand_factor(0.1);
        movingTargetObserver.setRange_rand_factor(50);
        movingTargetObserver.setTdoa_rand_factor(0.0000001);
        movingTargetObserver.setLat_move(0.005);
        movingTargetObserver.setLon_move(0.005);
        movingTargetObserver.setAsset_a_coords(asset_a_coords);
        movingTargetObserver.setAsset_b_coords(asset_b_coords);
        timer.scheduleAtFixedRate(movingTargetObserver,0,999);

        try {
            fuzerProcess.start();

            Thread.sleep(20000);

            timer.cancel();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testMoverSouthWest() {
        movingTargetObserver.setTrue_lat(-31.7); // TOPRIGHT
        movingTargetObserver.setTrue_lon(116.08);
    }

    @Test
    public void testMoverNorth() {
        movingTargetObserver.setTrue_lat(-31.99); // BOTTOM
        movingTargetObserver.setTrue_lon(115.95);
    }
}
