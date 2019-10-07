/*
 * Geolocation fusion and tracking, using custom extended kalman filter implementation
 *
 * @author Timothy Edge (timmyedge)
 */
package tech.tgo.fuzer.compute;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.LUDecomposition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tgo.fuzer.FuzerListener;
import tech.tgo.fuzer.model.*;
import tech.tgo.fuzer.util.Helpers;
import tech.tgo.fuzer.util.KmlFileExporter;
import tech.tgo.fuzer.util.KmlFileHelpers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class AlgorithmEKF implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmEKF.class);

    private FuzerListener fuzerListener;

    private GeoMission geoMission;

    Map<Long,Observation> observations = new ConcurrentHashMap<Long,Observation>();

    private final AtomicBoolean running = new AtomicBoolean(false);

    double[][] ThiData = { {1,0,1, 0}, {0,1,0,1}, {0,0,0,0}, {0,0,0,0}};
    RealMatrix Thi = new Array2DRowRealMatrix(ThiData);

    double[][] controlData = { {0}, {0}, {0}, {0}};
    RealMatrix B = new Array2DRowRealMatrix(controlData);

    double[][] procNoiseData = { {0.01, 0, 0, 0}, {0, 0.01 ,0, 0}, {0, 0, 0.01, 0}, {0, 0, 0 ,0.01}}; // orig: 0.01 eye
    RealMatrix Qu = new Array2DRowRealMatrix(procNoiseData);

    double[][] initCovarData = {{1, 0, 0, 0}, {0, 1, 0 ,0}, {0, 0, 1, 0}, {0, 0, 0 ,1}};
    RealMatrix Pinit = new Array2DRowRealMatrix(initCovarData);

    RealMatrix Rk;

    RealVector Xk;
    RealMatrix Pk;

    double[] innovd = {0,0,0,0};
    RealVector innov;

    double[][] P_innovd = {{0,0,0,0}, {0,0,0,0}, {0,0,0,0}, {0,0,0,0}};
    RealMatrix P_innov;

    double[][] eyeData = {{1,0,0,0}, {0,1,0,0}, {0,0,1,0}, {0,0,0,1}};
    RealMatrix eye = new Array2DRowRealMatrix(eyeData);

    RealMatrix H;
    double xk;
    double yk;
    RealMatrix K;

    KmlFileExporter kmlFileExporter = null;

    /*
     * Create an algorithm tracker process for the given config, observations and client implemented listener
     */
    public AlgorithmEKF(FuzerListener fuzerListener, Map<Long,Observation> observations, GeoMission geoMission)
    {
        this.fuzerListener = fuzerListener;
        this.observations = observations;
        this.geoMission = geoMission;
    }

    public void initialiseFilter() {
        /* Initialise filter configurable properties */
        double[][] measurementNoiseData = {{geoMission.getFilterMeasurementError()}}; // Smaller for trusted measurements. Guide: {0.01 -> 0.1}
        Rk = new Array2DRowRealMatrix(measurementNoiseData);

        /* Initialise filter state */
        List<Asset> assetList = new ArrayList<Asset>(this.geoMission.getAssets().values());
        double[] start_x_y;
        if (assetList.size()>1) {
            Random rand = new Random();
            Asset randAssetA = assetList.get(rand.nextInt(assetList.size()));
            assetList.remove(randAssetA);
            Asset randAssetB = assetList.get(rand.nextInt(assetList.size()));
            log.debug("Finding rudimentary start point between two random observations: " + randAssetA.getId() + "," + randAssetB.getId());

            start_x_y = findRudimentaryStartPoint(randAssetA, randAssetB, -5000);
        }
        else {
            Asset asset = assetList.get(0);
            double[] asset_utm = Helpers.convertLatLngToUtmNthingEasting(asset.getCurrent_loc()[0],asset.getCurrent_loc()[1]);

            start_x_y = new double[]{asset_utm[1] + 5000, asset_utm[0] - 5000};
        }
        log.debug("Filter start point: "+start_x_y[0]+","+start_x_y[1]);
        double[] initStateData = {start_x_y[0], start_x_y[1], 1, 1};

        log.info("Init State Data Easting/Northing: "+initStateData[0]+","+initStateData[1]+",1,1");
        double[] latLonStart = Helpers.convertUtmNthingEastingToLatLng(initStateData[0], initStateData[1], geoMission.getLatZone(), geoMission.getLonZone());
        log.info("Init start point: "+latLonStart[0]+","+latLonStart[1]);
        RealVector Xinit = new ArrayRealVector(initStateData);

        Xk = Xinit;
        Pk = Pinit.scalarMultiply(1000.0);
    }

    public synchronized void setObservations(Map<Long, Observation> observations) {
        this.observations = observations;
    }

    public void run()
    {
        log.info("Running for # observations:"+observations.size());

        if (observations.size()==0) {
            log.info("No observations returning");
            return;
        }

        running.set(true);

        dispatchResult(Xk);

        Vector<FilterObservationDTO> filterObservationDTOs = new Vector<FilterObservationDTO>();

        FilterStateDTO filterStateDTO = new FilterStateDTO();

        long startTime = Calendar.getInstance().getTimeInMillis();

        if (this.geoMission.getOutputFilterState()) {
            kmlFileExporter = new KmlFileExporter();
            kmlFileExporter.provisionFilterStateExport();
            log.debug("Provisioned filter state export");
        }
        int filterStateExportCounter = 0;


        while(true)
        {
            if (!running.get()) {
                log.debug("Thread was stopped");
                break;
            }

            if (this.geoMission.getFilterThrottle()!=null) {
                try {
                    log.trace("Throttling for miliseconds: "+this.geoMission.getFilterThrottle());
                    Thread.sleep(this.geoMission.getFilterThrottle());
                }
                catch(InterruptedException ie) {
                    log.warn("Error throttling filter");
                }
            }

            Xk = Thi.operate(Xk);// + B*uu);

            log.trace("Xk1="+Xk.toArray()[0]+" Xk2="+Xk.toArray()[1]);
            Pk = (Thi.multiply(Pk).multiply(Thi.transpose())).add(Qu);

            /* reinitialise innovation vector each time */
            innov = new ArrayRealVector(innovd);
            P_innov = new Array2DRowRealMatrix(P_innovd);

            /* reset */
            filterObservationDTOs.removeAllElements();

            /* observations collection is dynamically updated for tracking mode missions */
            Iterator obsIterator = this.observations.values().iterator();
            while (obsIterator.hasNext()) {

                Observation obs = (Observation) obsIterator.next();

                xk = Xk.getEntry(0);
                yk = Xk.getEntry(1);

                double f_est = 0.0;
                double d = 0.0;
                H = null;

                if (obs.getObservationType().equals(ObservationType.range)) {

                    H = recalculateH(obs.getX(), obs.getY(), xk, yk);

                    f_est = Math.sqrt(Math.pow((obs.getX() - xk), 2) + Math.pow(obs.getY() - yk, 2));

                    d = obs.getMeas();

                    log.trace("RANGE innovation: " + f_est + ", vs d: " + d);
                }
                else if (obs.getObservationType().equals(ObservationType.tdoa)) {

                    H = recalculateH_TDOA(obs.getX(), obs.getY(), obs.getXb(), obs.getYb(), xk, yk);

                    f_est = Math.sqrt(Math.pow((obs.getX() - xk), 2) + Math.pow(obs.getY() - yk, 2)) - Math.sqrt(Math.pow((obs.getXb() - xk), 2) + Math.pow(obs.getYb() - yk, 2));

                    d = obs.getMeas() * Helpers.SPEED_OF_LIGHT;

//                    // TEMP    DEVELOPMENTAL
//                    if (d<0) {
//                        //f_est = Math.sqrt(Math.pow((obs.getXb() - xk), 2) + Math.pow(obs.getYb() - yk, 2)) - Math.sqrt(Math.pow((obs.getX() - xk), 2) + Math.pow(obs.getY() - yk, 2));  NOPE
//                        //H = H.scalarMultiply(-1); NOPE
//                        obs.setCrossed_border(true);
//                    }

                    log.trace("TDOA innovation: " + f_est + ", vs d: " + d);
                }
                else if (obs.getObservationType().equals(ObservationType.aoa)) {

                    H = recalculateH_AOA(obs.getX(), obs.getY(), xk, yk);

                    f_est = Math.atan((obs.getY() - yk)/(obs.getX() - xk))*180/Math.PI;

                    if (xk<obs.getX()) {
                        f_est = f_est + 180;
                    }

                    if (yk<obs.getY() && xk>=obs.getX()) {
                        f_est = 360 - Math.abs(f_est);
                    }

                    d = obs.getMeas() * 180 / Math.PI;

                    log.trace("AOA innovation: " + f_est + ", vs d: " + d);
                }

                RealMatrix toInvert = (H.multiply(Pk).multiply(H.transpose()).add(Rk));
                RealMatrix Inverse = (new LUDecomposition(toInvert)).getSolver().getInverse();

                /* Measurement balancer */
                if (obs.getObservationType().equals(ObservationType.range)) {
                    K = Pk.multiply(H.transpose()).multiply(Inverse).scalarMultiply(1);
                }
                else if (obs.getObservationType().equals(ObservationType.tdoa)) {
                    K = Pk.multiply(H.transpose()).multiply(Inverse).scalarMultiply(1);
                }
                else {
                    K = Pk.multiply(H.transpose()).multiply(Inverse).scalarMultiply(1);
                }

                double rk = d - f_est;

                // TODO uncommetn to fix o-360 border crossing bug
                if (obs.getObservationType().equals(ObservationType.aoa)) {
                    rk = Math.abs(rk); // Always innovate anticlockwise
                }



                double[] HXk = H.operate(Xk).toArray();
                RealVector innov_ = K.scalarMultiply(rk - HXk[0]).getColumnVector(0);

                // TEMP test
//                if (obs.getObservationType().equals(ObservationType.range)) {
//                    if (Math.abs(innov_.getEntry(3)) < 5E-10) {
//                        innov_.setEntry(3,0);
//                        innov_.setEntry(2,0);
//                    }
//                }

                innov = innov_.add(innov);

                P_innov = K.multiply(H).multiply(Pk).add(P_innov);

                filterObservationDTOs.add(new FilterObservationDTO(obs, f_est, innov_));
            }

            Xk = Xk.add(innov);
            Pk = (eye.multiply(Pk)).subtract(P_innov);


            /* Export filter state */
            if (this.geoMission.getOutputFilterState()) {
                filterStateExportCounter++;
                if (filterStateExportCounter == 10) {

                    // Only if it is changing significantly
                    double residual = Math.abs(innov.getEntry(2)) + Math.abs(innov.getEntry(3));

                    if (residual > 0.5) {
                        filterStateDTO.setFilterObservationDTOs(filterObservationDTOs);
                        filterStateDTO.setXk(Xk);
                        kmlFileExporter.exportAdditionalFilterState(this.geoMission, filterStateDTO, residual);
                        filterStateExportCounter = 0;
                    }
                }
            }

            /* Export Result */
            if ((Calendar.getInstance().getTimeInMillis() - startTime) > this.geoMission.getDispatchResultsPeriod()) {

                /* A measure of consistency between types of observations */
                double residual_rk = 0.0;
                for (FilterObservationDTO obs_state: filterObservationDTOs) {
                    if (obs_state.getObs().getObservationType().equals(ObservationType.range)) {
                        residual_rk += (double) Math.abs(obs_state.getF_est() - obs_state.getObs().getMeas()) / 1000;
                    }
                    else if (obs_state.getObs().getObservationType().equals(ObservationType.tdoa)) {
                        residual_rk += (double) Math.abs(obs_state.getF_est() - obs_state.getObs().getMeas()) / 1000;
                    }
                    else if (obs_state.getObs().getObservationType().equals(ObservationType.aoa)) {
                        residual_rk += (double) Math.abs(obs_state.getF_est() - obs_state.getObs().getMeas()) / 360;
                    }
                }
                residual_rk = residual_rk / this.observations.size();


                // TODO, gradually increase R here??


                /* If filter had adequately processed latest observations - prevent dispatching spurious results */
                double residual = Math.abs(innov.getEntry(2)) + Math.abs(innov.getEntry(3));
                //log.debug("Residual: "+residual);

                if (residual < this.geoMission.getFilterDispatchResidualThreshold()) {
                    log.debug("Dispatching Result From # Observations: " + this.observations.size());
                    log.debug("Residual Movements: "+residual);
                    log.debug("Residual Measurement Delta: "+residual_rk);
                    log.debug("Residual Innovation: "+innov);

                    if (log.isDebugEnabled()) {
                        for (FilterObservationDTO obs_state : filterObservationDTOs) {
                            log.debug("Observation utilisation: type: "+obs_state.getObs().getObservationType().name()+", f_est: " + obs_state.getF_est() + ",d: " + obs_state.getObs().getMeas()+", innov: "+obs_state.getInnov());
                            log.debug("Innov: "+obs_state.getInnov().getEntry(3));

                            //DEPRECATE THIS:  TMEP
                            if (obs_state.getObs().getObservationType().equals(ObservationType.tdoa) && obs_state.getObs().getCrossed_border() != null) { /// TODO deprecate
                                log.debug("AOA meas Crossed the Zero border: "+obs_state.getObs().getCrossed_border());
                                // TODO, consider reset covariances?
                                //reinitialiseFilter();
                            }
                        }
                    }

                    startTime = Calendar.getInstance().getTimeInMillis();

                    //if (residual_rk < 100) {
                    dispatchResult(Xk);
//                    }
//                    else {
//                        log.debug("Reinitialising filter and not dispatching result since residual_rk >  threshold: "+residual_rk);
//                        reinitialiseFilter();
//                    }

                    if (geoMission.getFuzerMode().equals(FuzerMode.fix)) {
                        if (residual < this.geoMission.getFilterConvergenceResidualThreshold()) {
                            log.debug("Exiting since this is a FIX Mode run and filter has converged to threshold");
                            running.set(false);
                            break;
                        }
                    } else {
                        log.debug("This is a Tracking mode run, continuing...");
                    }
                }
            }
        }
    }

    public RealMatrix recalculateH(double x_rssi, double y_rssi, double Xk1, double Xk2) {

        double R1 = Math.sqrt(Math.pow((x_rssi-Xk1),2) + Math.pow(y_rssi-Xk2,2));

        double dfdx = -(x_rssi-Xk1)/R1;
        double dfdy = -(y_rssi-Xk2)/R1;

        double[][] jacobianData = {{0, 0, dfdx, dfdy}};
        RealMatrix H = new Array2DRowRealMatrix(jacobianData);
        return H;
    }

    public RealMatrix recalculateH_TDOA(double x, double y, double x2, double y2, double Xk1, double Xk2) {

        double R1 = Math.sqrt(Math.pow((x-Xk1),2) + Math.pow(y-Xk2,2));
        double R2 = Math.sqrt(Math.pow((x2-Xk1),2) + Math.pow(y2-Xk2,2));

        double dfdx = -(x+Xk1)/R1 - (-x2+Xk1)/R2;
        double dfdy = -(y+Xk2)/R1 - (-y2+Xk2)/R2;

        double[][] jacobianData = {{0, 0, dfdx, dfdy}};
        RealMatrix H = new Array2DRowRealMatrix(jacobianData);
        return H;
    }

    public RealMatrix recalculateH_AOA(double x, double y, double Xk1, double Xk2) {

        double R1 = Math.sqrt(Math.pow((x-Xk1),2) + Math.pow((y-Xk2),2));   // Note: better performance using sqrt

        double dfdx = (y-Xk2)/R1;  // Note d/d"x" = "y - y_est"/..... on purpose linearisation
        double dfdy = -(x-Xk1)/R1;

        double[][] jacobianData = {{0, 0, dfdx, dfdy}};
        RealMatrix H = new Array2DRowRealMatrix(jacobianData);
        return H;
    }

    public double[] findRudimentaryStartPoint(Asset asset_a, Asset asset_b, double addition) {
        double x_init=0; double y_init=0;
        double[] asset_a_utm = Helpers.convertLatLngToUtmNthingEasting(asset_a.getCurrent_loc()[0],asset_a.getCurrent_loc()[1]);
        double[] asset_b_utm = Helpers.convertLatLngToUtmNthingEasting(asset_b.getCurrent_loc()[0],asset_b.getCurrent_loc()[1]);
        if (asset_b == null) {
            x_init = asset_a_utm[1] - addition;
            y_init = asset_a_utm[0] + addition;
        }
        else {
            x_init = asset_a_utm[1];
            y_init = asset_a_utm[0];
            double x_n = asset_b_utm[1];
            double y_n = asset_b_utm[0];
            x_init = x_init + (x_init - x_n) / 2;
            y_init = y_init + (y_init - y_n) / 2;
        }
        return new double[]{x_init,y_init};
    }

    public boolean isRunning() {
        return this.running.get();
    }

    public void stopThread() {
        this.running.set(false);
    }

    public void dispatchResult(RealVector Xk) {

        double[] latLon = Helpers.convertUtmNthingEastingToLatLng(Xk.getEntry(0),Xk.getEntry(1), this.geoMission.getLatZone(), this.geoMission.getLonZone());
        this.geoMission.getTarget().setCurrent_loc(latLon);

        this.fuzerListener.result(geoMission.getGeoId(),Xk.getEntry(0),Xk.getEntry(1), Xk.getEntry(2), Xk.getEntry(3));

        // TODO, CEP / VARIANCE
//        varianceCounter++;
//        variance = (variance*(varianceCounter-1) + (Math.pow(Xk3,2) + Math.pow(Xk4,2))/2)/varianceCounter;
//        double cep = Math.sqrt(variance);
//        double cep = (Math.abs(Xk3)+Math.abs(Xk4))/2;
        double cep = 1500;
        this.geoMission.getTarget().setCurrent_cep(cep);

        if (this.geoMission.getOutputFilterState() && kmlFileExporter!=null) {
            kmlFileExporter.writeCurrentExports(this.geoMission);
        }

        if (this.geoMission.getOutputKml()) {
            KmlFileHelpers.exportGeoMissionToKml(this.geoMission);
        }
    }

    public void reinitialiseFilter() {
        /* Select two assets by random and use their middle point */
        Random rand = new Random();
        List<Asset> assetList = new ArrayList<Asset>(this.geoMission.getAssets().values());
        Asset randAssetA = assetList.get(rand.nextInt(assetList.size()));
        assetList.remove(randAssetA);
        Asset randAssetB = assetList.get(rand.nextInt(assetList.size()));
        log.debug("Finding rudimentary start point between two random observations: "+randAssetA.getId()+","+randAssetB.getId());

        double[] start_x_y = findRudimentaryStartPoint(randAssetA, randAssetB, -500);
        log.debug("(Re) Filter start point: "+start_x_y[0]+","+start_x_y[1]);
        double[] initStateData = {start_x_y[0], start_x_y[1], 1, 1};
        log.info("(Re) Init State Data Easting/Northing: "+initStateData[0]+","+initStateData[1]+",1,1");
        double[] latLonStart = Helpers.convertUtmNthingEastingToLatLng(initStateData[0], initStateData[1], geoMission.getLatZone(), geoMission.getLonZone());
        log.info("(Re) Init start point: "+latLonStart[0]+","+latLonStart[1]);
        RealVector Xinit = new ArrayRealVector(initStateData);
        Xk = Xinit;
    }

    public void resetCovariances(){
        log.debug("Resetting covariances, from: "+Pk);
        Pk = Pinit.scalarMultiply(1000.0);
        log.debug("Resetting covariances, to: "+Pk);
    }
}
