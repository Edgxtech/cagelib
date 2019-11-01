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

/**
 * Extended Kalman Filter Fusion Processor
 * @author Timothy Edge (timmyedge)
 */
public class AlgorithmEKF implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmEKF.class);

    private FuzerListener fuzerListener;

    private GeoMission geoMission;

    Map<Long,Observation> staged_observations = new ConcurrentHashMap<Long,Observation>();

    Map<Long,Observation> observations = new ConcurrentHashMap<Long,Observation>();

    private final AtomicBoolean running = new AtomicBoolean(false);

    double[][] ThiData = { {1,0,1, 0}, {0,1,0,1}, {0,0,0,0}, {0,0,0,0}};
    RealMatrix Thi = new Array2DRowRealMatrix(ThiData);

    double[][] controlData = { {0}, {0}, {0}, {0}};
    RealMatrix B = new Array2DRowRealMatrix(controlData);

    double[][] initCovarData = {{1, 0, 0, 0}, {0, 1, 0 ,0}, {0, 0, 1, 0}, {0, 0, 0 ,1}};
    RealMatrix Pinit = new Array2DRowRealMatrix(initCovarData);

    RealMatrix Qu;
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
     * Create a processor for the given config, observations and client implemented listener
     */
    public AlgorithmEKF(FuzerListener fuzerListener, Map<Long,Observation> observations, GeoMission geoMission)
    {
        this.fuzerListener = fuzerListener;
        this.geoMission = geoMission;

        setObservations(observations);
        initialiseFilter();
    }

    public void initialiseFilter() {
        /* Initialise filter configurable properties */
        double[][] measurementNoiseData = {{geoMission.getFilterMeasurementError()}}; // Smaller for trusted measurements. Guide: {0.01 -> 0.1}
        Rk = new Array2DRowRealMatrix(measurementNoiseData);

        double[][] procNoiseData = geoMission.getFilterProcessNoise();
        Qu = new Array2DRowRealMatrix(procNoiseData);

        /* Initialise filter state */
        List<Asset> assetList = new ArrayList<Asset>(this.geoMission.getAssets().values());
        double[] start_x_y;
        if (assetList.size()>1) {
            Random rand = new Random();
            Asset randAssetA = assetList.get(rand.nextInt(assetList.size()));
            assetList.remove(randAssetA);
            Asset randAssetB = assetList.get(rand.nextInt(assetList.size()));
            log.debug("Finding rudimentary start point between two random observations: " + randAssetA.getId() + "," + randAssetB.getId());

            //start_x_y = findRudimentaryStartPoint(randAssetA, randAssetB, -5000);
            start_x_y = findRudimentaryStartPoint(randAssetA, randAssetB, (Math.random()-0.5)*100000);
        }
        else {
            Asset asset = assetList.get(0);
            double[] asset_utm = Helpers.convertLatLngToUtmNthingEasting(asset.getCurrent_loc()[0],asset.getCurrent_loc()[1]);

            start_x_y = new double[]{asset_utm[1] + 5000, asset_utm[0] - 5000};
        }

        // TEMP
        //start_x_y = new double[]{409679,6491248};
        //start_x_y = new double[]{390679,6631248};
        //start_x_y = new double[]{386645.527,6461159.073};

        log.debug("Filter start point: "+start_x_y[0]+","+start_x_y[1]);
        double[] initStateData = {start_x_y[0], start_x_y[1], 1, 1};

        log.info("Init State Data Easting/Northing: "+initStateData[0]+","+initStateData[1]+",1,1");
        double[] latLonStart = Helpers.convertUtmNthingEastingToLatLng(initStateData[0], initStateData[1], geoMission.getLatZone(), geoMission.getLonZone());
        log.info("Init start point: "+latLonStart[0]+","+latLonStart[1]);
        RealVector Xinit = new ArrayRealVector(initStateData);

        Xk = Xinit;
        Pk = Pinit.scalarMultiply(1000.0);

        // NEW FEATURE HERE
        log.trace("Initialising Stage Observations as current observations");
        setStaged_observations(this.geoMission.observations);
    }

    public void setObservations(Map<Long, Observation> observations) {
        Comparator<Map.Entry<Long, Observation>> valueComparator = new Comparator<Map.Entry<Long, Observation>>() {
            @Override
            public int compare(Map.Entry<Long, Observation> e1, Map.Entry<Long, Observation> e2) {
                ObservationType v1 = e1.getValue().getObservationType();
                ObservationType v2 = e2.getValue().getObservationType();
                return v1.compareTo(v2);
            }
        };

        List<Map.Entry<Long, Observation>> listOfEntries = new ArrayList<Map.Entry<Long, Observation>>(observations.entrySet());
        Collections.sort(listOfEntries, valueComparator);
        LinkedHashMap<Long, Observation> sortedByValue = new LinkedHashMap<Long, Observation>(listOfEntries.size());
        for(Map.Entry<Long, Observation> entry : listOfEntries){
            sortedByValue.put(entry.getKey(), entry.getValue());
        }
        this.observations = sortedByValue;
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

        //temp
        //boolean converged = false;

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

            Xk = Thi.operate(Xk);

            Pk = (Thi.multiply(Pk).multiply(Thi.transpose())).add(Qu);

//            //temp
//            if (!converged) {
//                // Only need process noise during an initial searching state?  NOPE, this is needed IOT innovate
//
//            }
//            else {
//                // I think this is causing the 'drift after converged' bug?? NOPE
//                Pk = (Thi.multiply(Pk).multiply(Thi.transpose())).add(Qu);
//            }

            /* reinitialise innovation vector each time */
            innov = new ArrayRealVector(innovd);
            P_innov = new Array2DRowRealMatrix(P_innovd);

            /* reset */
            filterObservationDTOs.removeAllElements();

            // TEMP
            RealVector nonAoaNextState = null;

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

                    H = recalculateH_TDOA(obs.getX(), obs.getY(), obs.getXb(), obs.getYb(), xk, yk);//.scalarMultiply(-1); // temp scalar mult this oddly seems to fix an issue

                    f_est = Math.sqrt(Math.pow((obs.getX() - xk), 2) + Math.pow(obs.getY() - yk, 2)) - Math.sqrt(Math.pow((obs.getXb() - xk), 2) + Math.pow(obs.getYb() - yk, 2));

                    d = obs.getMeas() * Helpers.SPEED_OF_LIGHT;

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

//                    f_est(i,:) = [atan((y_aoa(i) - Xk(2))/(x_aoa(i) - Xk(1)))*180/pi];
//                    if(Xk(1)<x_aoa(i))
//                        f_est(i,:) = f_est(i,:)+180;
//                    end



                    d = obs.getMeas() * 180 / Math.PI;

                    log.trace("AOA innovation: " + f_est + ", vs d: " + d);
                }

                RealMatrix toInvert = (H.multiply(Pk).multiply(H.transpose()).add(Rk));
                RealMatrix Inverse = (new LUDecomposition(toInvert)).getSolver().getInverse();



                // TODO, new feature
                // aoa processed last
                // if there is a prevailing innovation direction, match aoa innovations towards it
                // if aoa, check current innov, and ensure match the aoa innovation direction.


                /* Measurement balancer */
                if (obs.getObservationType().equals(ObservationType.range)) {
                    K = Pk.multiply(H.transpose()).multiply(Inverse).scalarMultiply(this.geoMission.getFilterRangeBias());
                }
                else if (obs.getObservationType().equals(ObservationType.tdoa)) {
                    K = Pk.multiply(H.transpose()).multiply(Inverse).scalarMultiply(this.geoMission.getFilterTDOABias());
                }
                else {
                    K = Pk.multiply(H.transpose()).multiply(Inverse).scalarMultiply(this.geoMission.getFilterAOABias());
                }

                double rk = d - f_est;

                /* 360-0 Bug Fix */
                // Only required for when two aoa measurements?
//                if (obs.getObservationType().equals(ObservationType.aoa)) {
//                    rk = Math.abs(rk); // Always innovate anticlockwise
//                }
                if (obs.getObservationType().equals(ObservationType.aoa)) {
                    /* Only apply this rule if there is a prevailing pressure */
                    if (innov.getEntry(0) != 0.0) {

                        if (nonAoaNextState == null) {
                            // Set this once for each loop
                            nonAoaNextState = Xk.add(innov);
                        }
//                    if (nonAoaNextState.getEntry(3) > obs.getY()) {  NOPE
//                        rk = Math.abs(rk); // innovate anticlockwise
//                    }
//                    else {
//                        rk = -Math.abs(rk); // innovate clockwise
//                    }


//                    if (Xk.getEntry(3) < obs.getY()) {
//                        if (innov.getEntry(2) > 0) {
//                            rk = Math.abs(rk); // innovate anticlockwise
//                        } else {
//                            rk = -Math.abs(rk); // innovate clockwise
//                        }
//                    }
//                    else {
//                        if (innov.getEntry(2) > 0) {
//                            rk = -Math.abs(rk); // innovate anticlockwise
//                        } else {
//                            rk = Math.abs(rk); // innovate clockwise
//                        }
//                    }

//                    double m_ox = Math.tan(f_est);
//
//                    double fxp = m_ox*nonAoaNextState.getEntry(2); // fxp = y(k+1) + mox * x_pressure       ////   nonAoaNextState.getEntry(3) +
//                    log.debug("Xk: "+Xk);
//                    log.debug("Mox: "+m_ox+", fxp: "+fxp+", Yp: "+innov.getEntry(3)+", Pressure: "+nonAoaNextState+", INNOV: "+innov);
//
//                    if (Math.abs(nonAoaNextState.getEntry(3)) > Math.abs(fxp)) {
//                        if (Xk.getEntry(1) > obs.getY() && Xk.getEntry(0) > obs.getX()) {
//                            // 1st quad - A
//                            rk = Math.abs(rk); // innovate anticlockwise
//                            log.debug("1Q");
//                        }
//                        else if (Xk.getEntry(1) > obs.getY() && Xk.getEntry(0) < obs.getX()) {
//                            // 2nd quad - C
//                            rk = -Math.abs(rk); // innovate clockwise
//                            log.debug("2Q");
//                        }
//                        else if (Xk.getEntry(1) < obs.getY() && Xk.getEntry(0) < obs.getX()) {
//                            // 3rd quad - A
//                            rk = Math.abs(rk); // innovate anticlockwise
//                            log.debug("3Q");
//                        }
//                        else if (Xk.getEntry(1) < obs.getY() && Xk.getEntry(0) > obs.getX()) {
//                            // 4th quad - C
//                            rk = -Math.abs(rk); // innovate clockwise
//                            log.debug("4Q");
//                        }
//                    }

                        double pressure_angle = Math.atan((nonAoaNextState.getEntry(2) - obs.getY()) / (nonAoaNextState.getEntry(0) - obs.getX())) * 180 / Math.PI; // gradient from obs to current state (so a line projection can be made)
                        //log.debug("P-ang: "+pressure_angle+", f_est: "+f_est+", Yp: "+innov.getEntry(3)+", Pressure: "+nonAoaNextState+", INNOV: "+innov);
                        if (nonAoaNextState.getEntry(0) < obs.getX()) {
                            pressure_angle = pressure_angle + 180;
                        }

                        if (nonAoaNextState.getEntry(1) < obs.getY() && nonAoaNextState.getEntry(0) >= obs.getX()) {
                            pressure_angle = 360 - Math.abs(pressure_angle);
                        }
                        //log.debug("P-ang (adjusted): "+pressure_angle);


                        if (Math.abs(pressure_angle) > Math.abs(f_est)) {
                            if (Xk.getEntry(1) > obs.getY() && Xk.getEntry(0) > obs.getX()) {
                                // 1st quad - A
                                rk = Math.abs(rk); // innovate anticlockwise
                                //log.debug("1Q");
                            } else if (Xk.getEntry(1) > obs.getY() && Xk.getEntry(0) < obs.getX()) {
                                // 2nd quad - C
                                rk = -Math.abs(rk); // innovate clockwise
                                //log.debug("2Q");
                            } else if (Xk.getEntry(1) < obs.getY() && Xk.getEntry(0) < obs.getX()) {
                                // 3rd quad - A
                                rk = Math.abs(rk); // innovate anticlockwise
                                //log.debug("3Q");
                            } else if (Xk.getEntry(1) < obs.getY() && Xk.getEntry(0) > obs.getX()) {
                                // 4th quad - C
                                rk = -Math.abs(rk); // innovate clockwise
                                //log.debug("4Q");
                            }
                        } else {
                            if (Xk.getEntry(1) > obs.getY() && Xk.getEntry(0) > obs.getX()) {
                                // 1st quad - A
                                rk = -Math.abs(rk); // innovate anticlockwise
                                //log.debug("1Q");
                            } else if (Xk.getEntry(1) > obs.getY() && Xk.getEntry(0) < obs.getX()) {
                                // 2nd quad - C
                                rk = Math.abs(rk); // innovate clockwise
                                //log.debug("2Q");
                            } else if (Xk.getEntry(1) < obs.getY() && Xk.getEntry(0) < obs.getX()) {
                                // 3rd quad - A
                                rk = -Math.abs(rk); // innovate anticlockwise
                                //log.debug("3Q");
                            } else if (Xk.getEntry(1) < obs.getY() && Xk.getEntry(0) > obs.getX()) {
                                // 4th quad - C
                                rk = Math.abs(rk); // innovate clockwise
                                //log.debug("4Q");
                            }
                        }
                    }
                }

                double[] HXk = H.operate(Xk).toArray();
                RealVector innov_ = K.scalarMultiply(rk - HXk[0]).getColumnVector(0);

                innov = innov_.add(innov);

                P_innov = K.multiply(H).multiply(Pk).add(P_innov);

                filterObservationDTOs.add(new FilterObservationDTO(obs, f_est, innov_));
            }

            Xk = Xk.add(innov);
            Pk = (eye.multiply(Pk)).subtract(P_innov);

            /* Export filter state - development debugging */
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

                /* A measure of residual changes the filter intends to make */
                double residual = Math.abs(innov.getEntry(2)) + Math.abs(innov.getEntry(3));

                if (residual < this.geoMission.getFilterDispatchResidualThreshold()) {
                    log.debug("Dispatching Result From # Observations: " + this.observations.size());
                    log.debug("Residual Movements: "+residual);
                    log.debug("Residual Measurement Delta: "+residual_rk);
                    log.debug("Residual Innovation: "+innov);
                    log.debug("Pk: "+Pk);

                    if (log.isDebugEnabled()) {
                        for (FilterObservationDTO obs_state : filterObservationDTOs) {
                            double f_est_adj = obs_state.getF_est();
                            if (obs_state.getObs().getObservationType().equals(ObservationType.tdoa)) {
                                f_est_adj = f_est_adj / Helpers.SPEED_OF_LIGHT;
                            }
                            else if (obs_state.getObs().getObservationType().equals(ObservationType.aoa)) {
                                f_est_adj = f_est_adj * Math.PI / 360;
                            }
                            log.debug("Observation utilisation: assets:"+obs_state.getObs().getAssetId()+"/"+obs_state.getObs().getAssetId_b()+", type: "+obs_state.getObs().getObservationType().name()+", f_est(adj): " + f_est_adj + ",d: " + obs_state.getObs().getMeas()+", innov: "+obs_state.getInnov());
                        }
                    }

                    startTime = Calendar.getInstance().getTimeInMillis();

                    dispatchResult(Xk);

                    if (geoMission.getFuzerMode().equals(FuzerMode.fix)) {
                        if (residual < this.geoMission.getFilterConvergenceResidualThreshold()) {
                            log.debug("Exiting since this is a FIX Mode run and filter has converged to threshold");
                            running.set(false);
                            break;
                        }
                    }
                    else {
                        log.debug("This is a Tracking mode run, using latest observations (as held in staging) and continuing...");

                        /* Resynch latest observations, and reinitialise with current state estimate */
                        log.debug("# Staged observations: "+this.staged_observations.size());
                        setObservations(this.staged_observations);

                        //resetCovariances();

                        //temp
                        //converged=true;
                    }
                }
                else {
                    log.debug("Residual not low enough to export result: "+residual);
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

        /*  MATHCODE
            f_meas(i,:) = [sqrt((x(1)-X_true(1,k))^2 + (y(1)-X_true(2,k))^2) - sqrt((x(i+1)-X_true(1,k))^2 + (y(i+1)-X_true(2,k))^2)] + 1*(0.5-rand);
            f_est(i,:) = [(sqrt((x(1)-Xk(1))^2 + (y(1)-Xk(2))^2) - sqrt((x(i+1)-Xk(1))^2 + (y(i+1)-Xk(2))^2))];
            r(i,:) = f_meas(i,:) - f_est(i,:);

            R1 = sqrt((x(1)-Xk(1))^2 + (y(1)-Xk(2))^2);
            R2 = sqrt((x(i+1)-Xk(1))^2 + (y(i+1)-Xk(2))^2);

            dfdx(i) = -(x(1)+Xk(1))/R1 - (-x(i+1)+Xk(1))/R2;
            dfdy(i) = -(y(1)+Xk(2))/R1 - (-y(i+1)+Xk(2))/R2;
         */

        double R1 = Math.sqrt(Math.pow((x-Xk1),2) + Math.pow(y-Xk2,2));
        double R2 = Math.sqrt(Math.pow((x2-Xk1),2) + Math.pow(y2-Xk2,2));

//        double dfdx = -(x-Xk1)/R1 - (-x2+Xk1)/R2;  //ORIGINAL
//        double dfdy = -(y-Xk2)/R1 - (-y2+Xk2)/R2;

        double dfdx = (-x+Xk1)/R1 - (-x2+Xk1)/R2; // Equivalent (DON'T EDIT, Seems to always work)
        double dfdy = (-y+Xk2)/R1 - (-y2+Xk2)/R2;

//        double dfdx = (-x+Xk1)/(R1-R2) + (x2-Xk1)/(R1-R2); // Example form, doesnt work
//        double dfdy = (-y+Xk2)/(R1-R2) + (y2-Xk2)/(R1-R2);

        double[][] jacobianData = {{0, 0, dfdx, dfdy}};
        RealMatrix H = new Array2DRowRealMatrix(jacobianData);
        return H;
    }

    public RealMatrix recalculateH_AOA(double x, double y, double Xk1, double Xk2) {

        double R1 = Math.sqrt(Math.pow((x-Xk1),2) + Math.pow((y-Xk2),2));   // Note: better performance using sqrt
        //double R1 = Math.pow((x-Xk1),2) + Math.pow((y-Xk2),2);

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
            x_init = asset_a_utm[1] + addition;
            y_init = asset_a_utm[0] - addition;
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

        /* Compute probability ELP */
        double[][] covMatrix=new double[][]{{Pk.getEntry(0,0),Pk.getEntry(0,1)},{Pk.getEntry(1,0),Pk.getEntry(1,1)}};
        double[] evalues = Helpers.getEigenvalues(covMatrix);
        double largestEvalue = Math.max(evalues[0],evalues[1]);
        double smallestEvalue = Math.min(evalues[0],evalues[1]);
        double[] evector = Helpers.getEigenvector(covMatrix, largestEvalue);
        double rot = Math.atan(evector[1] / evector[0]);
        double major = 2*Math.sqrt(9.210*largestEvalue); // 5.991 equiv 95% C.I, 4.605 equiv 90% C.I, 9.210 equiv 99% C.I
        double minor = 2*Math.sqrt(9.210*smallestEvalue);
        this.geoMission.getTarget().setElp_major(major);
        this.geoMission.getTarget().setElp_minor(minor);
        this.geoMission.getTarget().setElp_rot(rot);

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

    public synchronized void setStaged_observations(Map<Long, Observation> staged_observations) {
        this.staged_observations = staged_observations;
    }
}
