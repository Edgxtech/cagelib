package tech.tgo.efusion.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tgo.efusion.EfusionProcessManager;
import tech.tgo.efusion.model.Observation;
import tech.tgo.efusion.model.ObservationType;

import java.util.*;

/**
 * Simulate new observations following target
 * @author Timothy Edge (timmyedge)
 */
public class SimulatedTargetObserver extends TimerTask {

    private static final Logger log = LoggerFactory.getLogger(SimulatedTargetObserver.class);

    EfusionProcessManager efusionProcessManager;

    double true_lat; double true_lon;

    Map<String,TestAsset> testAssets = new HashMap<String,TestAsset>();

    double range_rand_factor; // = 0; /* Guide: 50 [m] */
    double tdoa_rand_factor; // = 0.0000001; /* Guide: 0.0000001 [sec] */
    double aoa_rand_factor; // = 0; /* Guide: 0.1 [radians] */

    double lat_move; // = 0.001;
    double lon_move; // = 0.001;

    /* Similar mechanism to maintain observation ids for different assets to targets should be implemented in client logic */
    Map<String,Long> assetToObservationIdMapping = new HashMap<String,Long>();

    @Override
    public void run() {

        // Generate lat,lon path of movement according to simple movement model
        true_lat = true_lat + lat_move;
        true_lon = true_lon + lon_move;
        log.debug("Moving Observer, moved target to: "+true_lat+","+true_lon);

        // update GeoMission::Target::TrueLocation
        efusionProcessManager.getGeoMission().getTarget().setTrue_current_loc(new Double[]{true_lat,true_lon});

        double[] utm_coords = Helpers.convertLatLngToUtmNthingEasting(true_lat, true_lon);
        double true_y = utm_coords[0];
        double true_x = utm_coords[1];

        /* for each asset, generate relevant observations */
        log.debug("Regenerating observations from # assets: "+testAssets.keySet().size());
        for (TestAsset asset : testAssets.values()) {
            utm_coords = Helpers.convertLatLngToUtmNthingEasting(asset.getCurrent_loc()[0], asset.getCurrent_loc()[1]);
            double a_y = utm_coords[0];
            double a_x = utm_coords[1];

            try {
                if (asset.getProvide_range()!=null && asset.getProvide_range()) {
                    Long obsId = assetToObservationIdMapping.get(asset.getId()+"_"+ObservationType.range.name());
                    if (obsId==null)
                    {
                        obsId = new Random().nextLong();
                        assetToObservationIdMapping.put(asset.getId()+"_"+ObservationType.range.name(),obsId);
                    }
                    //double meas_range = Math.sqrt(Math.pow(a_y-true_y,2) + Math.pow(a_x-true_x,2)) + Math.random()*range_rand_factor; orig
                    double meas_range = ObservationTestHelpers.getRandomRangeMeasurement(a_y, a_x, true_y, true_x, range_rand_factor);
                    log.debug("Asset: "+asset.getId()+", Meas range: " + meas_range);

                    Observation obs = new Observation(obsId, asset.getId(), asset.getCurrent_loc()[0], asset.getCurrent_loc()[1]);
                    obs.setMeas(meas_range);
                    obs.setObservationType(ObservationType.range);
                    if (range_rand_factor==0.0) {
                        obs.setMeas_error(Helpers.SMALL_DEFAULT_RANGE_MEAS_ERROR); // Force it to use default meas error
                    }
                    else {
                        obs.setMeas_error(range_rand_factor / 10000); // units are in utm. [1 utm equates to 10000m]
                        // TEMP TEST OVERRIDE
                        obs.setMeas_error(0.2);
                    }
                    efusionProcessManager.addObservation(obs);
                }

                if (asset.getProvide_tdoa()!=null && asset.getProvide_tdoa() && asset.getTdoa_asset_ids() != null && !asset.getTdoa_asset_ids().isEmpty()) {
                    /* Second asset that is providing shared tdoa measurement */
                    for (String secondary_asset_id : asset.getTdoa_asset_ids()) {
                        Long obsId = assetToObservationIdMapping.get(asset.getId()+":"+secondary_asset_id+"_"+ObservationType.tdoa.name());
                        if (obsId==null)
                        {
                            obsId = new Random().nextLong();
                            assetToObservationIdMapping.put(asset.getId()+":"+secondary_asset_id+"_"+ObservationType.tdoa.name(),obsId);
                        }

                        TestAsset asset1 = testAssets.get(secondary_asset_id);
                        utm_coords = Helpers.convertLatLngToUtmNthingEasting(asset1.getCurrent_loc()[0], asset1.getCurrent_loc()[1]);
                        double b_y = utm_coords[0];
                        double b_x = utm_coords[1];

                        double meas_tdoa = ObservationTestHelpers.getRandomTdoaMeasurement(a_y, a_x, b_y, b_x, true_y, true_x, tdoa_rand_factor);
                        log.debug("Asset: "+asset.getId()+", 2nd Asset: "+secondary_asset_id+", Meas tdoa: "+meas_tdoa);

                        Observation obs_c = new Observation(obsId, asset.getId(), asset.getCurrent_loc()[0], asset.getCurrent_loc()[1]);
                        obs_c.setAssetId_b(testAssets.get(secondary_asset_id).getId());
                        obs_c.setLat_b(asset1.getCurrent_loc()[0]);
                        obs_c.setLon_b(asset1.getCurrent_loc()[1]);
                        obs_c.setMeas(meas_tdoa); // tdoa in seconds
                        obs_c.setObservationType(ObservationType.tdoa);
                        /* Should be capped at no more than the rand_factor, expressed in utm units
                        *  May expect sensors to report dynamic measurement errors,
                        *  for sim use the upper limit assuming full measurement error */
                        if (tdoa_rand_factor==0.0) {
                            obs_c.setMeas_error(Helpers.SMALL_DEFAULT_TDOA_MEAS_ERROR); // Force it to use default meas error
                        }
                        else {
                            obs_c.setMeas_error(tdoa_rand_factor * Helpers.SPEED_OF_LIGHT / 10000); // units are in utm. [1 utm equates to 10000m]
                            // TEMP TEST OVERRIDE
                            obs_c.setMeas_error(0.2);
                        }
                        efusionProcessManager.addObservation(obs_c);
                    }
                }

                if (asset.getProvide_aoa()!=null && asset.getProvide_aoa()) {
                    Long obsId = assetToObservationIdMapping.get(asset.getId()+"_"+ObservationType.aoa.name());
                    if (obsId==null)
                    {
                        obsId = new Random().nextLong();
                        assetToObservationIdMapping.put(asset.getId()+"_"+ObservationType.aoa.name(),obsId);
                    }
                    double meas_aoa = ObservationTestHelpers.getRandomAoaMeasurement(a_y, a_x, true_y, true_x, aoa_rand_factor);
                    log.debug("Asset: "+asset.getId()+", Meas AOA: "+meas_aoa);

                    Observation obs_d = new Observation(obsId,asset.getId(), asset.getCurrent_loc()[0], asset.getCurrent_loc()[1]);
                    obs_d.setMeas(meas_aoa); // aoa in radians
                    obs_d.setObservationType(ObservationType.aoa);
                    if (aoa_rand_factor==0.0) {
                        obs_d.setMeas_error(Helpers.SMALL_DEFAULT_AOA_MEAS_ERROR); // Force it to use default meas error
                    }
                    else {
                        obs_d.setMeas_error(Math.tan(aoa_rand_factor) * 1000 / 10000); // Assuming target is at 1000m. Units are in utm. [1 utm equates to 10000m]
                        // TEMP TEST OVERRIDE
                        obs_d.setMeas_error(0.2);
                    }
                    efusionProcessManager.addObservation(obs_d);
                }
            }
            catch (Exception e) {
                log.error("Couldn't add all observations for test asset: "+asset.getId());
                e.printStackTrace(); }
        }
    }

    public EfusionProcessManager getEfusionProcessManager() {
        return efusionProcessManager;
    }

    public void setEfusionProcessManager(EfusionProcessManager efusionProcessManager) {
        this.efusionProcessManager = efusionProcessManager;
    }

    public double getTrue_lat() {
        return true_lat;
    }

    public void setTrue_lat(double true_lat) {
        this.true_lat = true_lat;
    }

    public double getTrue_lon() {
        return true_lon;
    }

    public void setTrue_lon(double true_lon) {
        this.true_lon = true_lon;
    }

    public double getRange_rand_factor() {
        return range_rand_factor;
    }

    public void setRange_rand_factor(double range_rand_factor) {
        this.range_rand_factor = range_rand_factor;
    }

    public double getTdoa_rand_factor() {
        return tdoa_rand_factor;
    }

    public void setTdoa_rand_factor(double tdoa_rand_factor) {
        this.tdoa_rand_factor = tdoa_rand_factor;
    }

    public double getAoa_rand_factor() {
        return aoa_rand_factor;
    }

    public void setAoa_rand_factor(double aoa_rand_factor) {
        this.aoa_rand_factor = aoa_rand_factor;
    }

    public double getLat_move() {
        return lat_move;
    }

    public void setLat_move(double lat_move) {
        this.lat_move = lat_move;
    }

    public double getLon_move() {
        return lon_move;
    }

    public void setLon_move(double lon_move) {
        this.lon_move = lon_move;
    }

    public Map<String, TestAsset> getTestAssets() {
        return testAssets;
    }

    public void setTestAssets(Map<String, TestAsset> testAssets) {
        this.testAssets = testAssets;
    }
}
