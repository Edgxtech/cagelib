package tech.tgo.efusion.compute;

import org.apache.commons.math3.linear.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tgo.efusion.EfusionListener;
import tech.tgo.efusion.model.*;
import tech.tgo.efusion.util.Helpers;
import tech.tgo.efusion.util.KmlFileHelpers;
import tech.tgo.efusion.util.KmlFileStaticHelpers;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Extended Kalman Filter Fusion Processor
 * @author Timothy Edge (timmyedge)
 */
public class ComputeProcessorAL0_1 implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ComputeProcessorAL0_1.class);

    private EfusionListener efusionListener;

    private GeoMission geoMission;

    Map<Long,Observation> staged_observations = new ConcurrentHashMap<Long,Observation>();

    Map<Long,Observation> observations = new ConcurrentHashMap<Long,Observation>();

    private final AtomicBoolean running = new AtomicBoolean(false);

    double[][] ThiData = { {1,0}, {0,1}};
    RealMatrix Thi = new Array2DRowRealMatrix(ThiData);

    double[][] controlData = { {0}, {0}};
    RealMatrix B = new Array2DRowRealMatrix(controlData);

    double[][] initCovarData = {{1, 0}, {0, 1}};
    RealMatrix Pinit = new Array2DRowRealMatrix(initCovarData);

    RealMatrix Qu;
    RealMatrix Rk;

    RealVector Xk;
    RealMatrix Pk;

    double[] innovd = {0,0};
    RealVector innov;

    double[][] P_innovd = {{0,0}, {0,0}};
    RealMatrix P_innov;

    double[][] eyeData = {{1,0}, {0,1}};
    RealMatrix eye = new Array2DRowRealMatrix(eyeData);

    RealMatrix H;
    double xk;
    double yk;
    RealMatrix K;

    KmlFileHelpers kmlFileHelpers = null;

    /*
     * Create processor for the given config, observations and client implemented listener
     */
    public ComputeProcessorAL0_1(EfusionListener efusionListener, Map<Long,Observation> observations, GeoMission geoMission)
    {
        this.efusionListener = efusionListener;
        this.geoMission = geoMission;

        setObservations(observations);
        initialiseFilter();
    }

    public void initialiseFilter() {
//        double[][] measurementNoiseData = {{geoMission.getFilterMeasurementError()}}; DEPRECATED
//        Rk = new Array2DRowRealMatrix(measurementNoiseData);

        double[][] procNoiseData = geoMission.getFilterProcessNoise();
        Qu = new Array2DRowRealMatrix(procNoiseData);

        /* Initialise filter state */
        double[] start_x_y;
        if (this.geoMission.getFilterUseSpecificInitialCondition()!=null && this.geoMission.getFilterUseSpecificInitialCondition()) {
            double[] asset_utm = Helpers.convertLatLngToUtmNthingEasting(this.geoMission.getFilterSpecificInitialLat(), this.geoMission.getFilterSpecificInitialLon());
            start_x_y = new double[]{asset_utm[1], asset_utm[0]};
            log.debug("Using SPECIFIC initial condition: "+this.geoMission.getFilterSpecificInitialLat()+", "+this.geoMission.getFilterSpecificInitialLon()+" ["+start_x_y[1]+", "+start_x_y[0]+"]");
        }
        else {
            List<Asset> assetList = new ArrayList<Asset>(this.geoMission.getAssets().values());
            if (assetList.size() > 1) {
                Random rand = new Random();
                Asset randAssetA = assetList.get(rand.nextInt(assetList.size()));
                assetList.remove(randAssetA);
                Asset randAssetB = assetList.get(rand.nextInt(assetList.size()));
                //log.debug("Finding rudimentary start point between two random observations: " + randAssetA.getId() + "," + randAssetB.getId());
                start_x_y = findRudimentaryStartPoint(randAssetA, randAssetB, (Math.random() - 0.5) * 100000);
                log.debug("Using RANDOM initial condition: near asset(s) ['"+randAssetA.getId() + "' & '" + randAssetB.getId()+"']: "+start_x_y[1]+", "+start_x_y[0]);

                log.debug("Dist between assets: "+Math.sqrt(Math.pow(randAssetA.getCurrent_loc()[0] - randAssetB.getCurrent_loc()[0], 2) + Math.pow(randAssetA.getCurrent_loc()[1] - randAssetB.getCurrent_loc()[1], 2)));
            } else {
                Asset asset = assetList.get(0);
                double[] asset_utm = Helpers.convertLatLngToUtmNthingEasting(asset.getCurrent_loc()[0], asset.getCurrent_loc()[1]);
                start_x_y = new double[]{asset_utm[1] + 5000, asset_utm[0] - 5000};
                log.debug("Using RANDOM initial condition: near asset ["+asset.getId()+"]: "+start_x_y[1]+", "+start_x_y[0]);
            }
        }

        log.debug("Filter start state: "+start_x_y[0]+","+start_x_y[1]);
        double[] initStateData = {start_x_y[0], start_x_y[1]};

        RealVector Xinit = new ArrayRealVector(initStateData);
        Xk = Xinit;
        Pk = Pinit.scalarMultiply(0.01);  // AL1 Tends to lose itself fail to converge, unless this kept small (i.e. ~0.01). AL0 Originally used 1000

        log.trace("Initialising Stage Observations as current observations, #: "+this.geoMission.observations.size());
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

        /* Sort such that AOA last, for applying the 360-0 conundrum fix */
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
            kmlFileHelpers = new KmlFileHelpers();
            kmlFileHelpers.provisionFilterStateExport();
            log.debug("Provisioned filter state export");
        }
        int filterStateExportCounter = 0;


        // TODO, for each of four box corner init conditions, or just one corner, run for preconfigured number of iterations
        //     if in 4 box mode, dont exit and report result until all four corners tested
        //     support mode where it can box until getting first result.
        //     support mode where it can simply just run from preconfigured box corner and if fails then too bad, dont get a result.

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

//            log.info("Pk:");
//                log.info("Pk: "+ Pk);
//
//                log.info("Thi:");
//                log.info("val: "+ Thi);
//
                //log.info("Qu:");
                //log.info("val: "+ Qu);

            Pk = (Thi.multiply(Pk).multiply(Thi.transpose())).add(Qu);

            /* reinitialise various collections */
            innov = new ArrayRealVector(innovd);
            P_innov = new Array2DRowRealMatrix(P_innovd);
            filterObservationDTOs.removeAllElements();
            RealVector nextOtherMeasurementExclusiveState = null;
            Iterator obsIterator = this.observations.values().iterator();

            while (obsIterator.hasNext()) {

                Observation obs = (Observation) obsIterator.next();

                xk = Xk.getEntry(0);
                yk = Xk.getEntry(1);

                double f_est = 0.0;
                double d = 0.0;
                H = null;
                RealMatrix Inverse = null;

                if (obs.getObservationType().equals(ObservationType.range)) {

                    H = recalculateH(obs.getX(), obs.getY(), xk, yk);

                    f_est = Math.sqrt(Math.pow((obs.getX() - xk), 2) + Math.pow(obs.getY() - yk, 2));

                    d = obs.getMeas();

                    log.trace("RANGE innovation: " + f_est + ", vs d: " + d);

                    RealMatrix toInvert = (H.multiply(Pk).multiply(H.transpose()).add(new Array2DRowRealMatrix(new double[][]{{obs.getMeas_error()}})));
                    Inverse = (new LUDecomposition(toInvert)).getSolver().getInverse();
                }
                else if (obs.getObservationType().equals(ObservationType.tdoa)) {

                    H = recalculateH_TDOA(obs.getX(), obs.getY(), obs.getXb(), obs.getYb(), xk, yk);//.scalarMultiply(-1); // temp scalar mult this oddly seems to fix an issue

                    f_est = Math.sqrt(Math.pow((obs.getX() - xk), 2) + Math.pow(obs.getY() - yk, 2)) - Math.sqrt(Math.pow((obs.getXb() - xk), 2) + Math.pow(obs.getYb() - yk, 2));

                    d = obs.getMeas() * Helpers.SPEED_OF_LIGHT;

                    log.trace("TDOA innovation: " + f_est + ", vs d: " + d);

                    //log.info("Pk: "+ Pk);
                    //log.debug("H: "+H);

                    RealMatrix toInvert = (H.multiply(Pk).multiply(H.transpose()).add(new Array2DRowRealMatrix(new double[][]{{obs.getMeas_error()}})));
                    Inverse = (new LUDecomposition(toInvert)).getSolver().getInverse();

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

                    //log.info("Pk: "+ Pk);
                    //log.debug("H: "+H);


                    RealMatrix toInvert = (H.multiply(Pk).multiply(H.transpose()).add(new Array2DRowRealMatrix(new double[][]{{obs.getMeas_error()}})));
                    Inverse = (new LUDecomposition(toInvert)).getSolver().getInverse();
                }



                K = Pk.multiply(H.transpose()).multiply(Inverse);  //.scalarMultiply(this.geoMission.getFilterRangeBias());
                //log.info("K:"+K);
                //log.info(K.toString());

                double rk = d - f_est;

                //log.info("RUNNING CONUNDRUM TEST");
                /* '360-0 Conundrum' adjustment. NOTE: AOA types always processed last due to sorting during setObservations */
                if (obs.getObservationType().equals(ObservationType.aoa)) {

                    if (innov.getEntry(0) != 0.0) {
                        if (nextOtherMeasurementExclusiveState == null) {
                            nextOtherMeasurementExclusiveState = Xk.add(innov);
                        }

                        /* gradient from obs to prevailing pressure direction */
                        double pressure_angle = Math.atan((nextOtherMeasurementExclusiveState.getEntry(1) - obs.getY()) / (nextOtherMeasurementExclusiveState.getEntry(0) - obs.getX())) * 180 / Math.PI;
                        //log.debug("P-ang: "+pressure_angle+", f_est: "+f_est+", Pressure: "+nonAoaNextState+", INNOV: "+innov);

                        // QUADRANT specific
//                        if (nextOtherMeasurementExclusiveState.getEntry(0) < obs.getX() && nextOtherMeasurementExclusiveState.getEntry(1) >= obs.getY()) {
//                            pressure_angle = 180 - pressure_angle;
//                        }
//
//                        if (nextOtherMeasurementExclusiveState.getEntry(0) < obs.getX() && nextOtherMeasurementExclusiveState.getEntry(1) < obs.getY()) {
//                            pressure_angle = 180 + Math.abs(pressure_angle);
//                        }
//
//                        if (nextOtherMeasurementExclusiveState.getEntry(0) < obs.getX() && nextOtherMeasurementExclusiveState.getEntry(1) < obs.getY()) {
//                            pressure_angle = -Math.abs(pressure_angle);
//                        }


                        // ORIGINAL METHOD USED
//                        if (nextOtherMeasurementExclusiveState.getEntry(0) < obs.getX()) {
//                            pressure_angle = pressure_angle + 180;
//                        }
//
//                        if (nextOtherMeasurementExclusiveState.getEntry(1) < obs.getY() && nextOtherMeasurementExclusiveState.getEntry(0) >= obs.getX()) {
//                            pressure_angle = 360 - Math.abs(pressure_angle);
//                        }
//
//                        // This may only be useful for low numbers of predom AOA scenarios, and only for TRACK MODE runs (TBD)
//                        /* For the case where the prevailing direction is above the intended innovation direction of the measurement in question */
//                        if (Math.abs(pressure_angle) > Math.abs(f_est)) {
//                            /* 1st to 4th quadrant, choose positive or negative angle respectively */
//                            if (Xk.getEntry(1) > obs.getY() && Xk.getEntry(0) > obs.getX()) {
//                                rk = Math.abs(rk);
//                            } else if (Xk.getEntry(1) > obs.getY() && Xk.getEntry(0) < obs.getX()) {
//                                rk = -Math.abs(rk);
//                            } else if (Xk.getEntry(1) < obs.getY() && Xk.getEntry(0) < obs.getX()) {
//                                rk = Math.abs(rk);
//                            } else if (Xk.getEntry(1) < obs.getY() && Xk.getEntry(0) > obs.getX()) {
//                                rk = -Math.abs(rk);
//                            }
//                        }
//                        /* For the case where the prevailing direction is below the intended innovation direction of the measurement in question */
//                        else {
//                            if (Xk.getEntry(1) > obs.getY() && Xk.getEntry(0) > obs.getX()) {
//                                rk = -Math.abs(rk);
//                            } else if (Xk.getEntry(1) > obs.getY() && Xk.getEntry(0) < obs.getX()) {
//                                rk = Math.abs(rk);
//                            } else if (Xk.getEntry(1) < obs.getY() && Xk.getEntry(0) < obs.getX()) {
//                                rk = -Math.abs(rk);
//                            } else if (Xk.getEntry(1) < obs.getY() && Xk.getEntry(0) > obs.getX()) {
//                                rk = Math.abs(rk);
//                            }
//                        }
                    }
                }

                //double[] HXk = H.operate(Xk).toArray();  /// REMOVED in AL1
                RealVector innov_ = K.scalarMultiply(rk).getColumnVector(0);    /// rk - HXk[0]  -removed in AL1

                innov = innov_.add(innov);

                P_innov = K.multiply(H).multiply(Pk).add(P_innov);
                //log.info("P_innov:"+P_innov);

//                log.info("K:");
//                log.info("val: "+ K);
//
//                log.info("H:");
//                log.info("val: "+ H);
//
//                log.info("KH:");
//                log.info("val: "+ K.multiply(H));
//
//                log.info("KHPk:");
//                log.info("val: "+ K.multiply(H).multiply(Pk));

                filterObservationDTOs.add(new FilterObservationDTO(obs, f_est, innov_));
            }

            Xk = Xk.add(innov);
            Pk = (eye.multiply(Pk)).subtract(P_innov);
            //log.debug("Pk: "+Pk);

            /* Export filter state - development debugging */
            if (this.geoMission.getOutputFilterState()) {
                filterStateExportCounter++;
                if (filterStateExportCounter == 10) {
                    // Only if it is changing significantly
                    double residual = Math.abs(innov.getEntry(0)) + Math.abs(innov.getEntry(1));
                    if (residual > 0.5) {
                        filterStateDTO.setFilterObservationDTOs(filterObservationDTOs);
                        filterStateDTO.setXk(Xk);
                        kmlFileHelpers.exportAdditionalFilterState(this.geoMission, filterStateDTO, residual);
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
                double residual = Math.abs(innov.getEntry(0)) + Math.abs(innov.getEntry(1));

                //double variance_sum = Pk.getEntry(0,0) + Pk.getEntry(1,1);
                //log.debug("Variance Sum: " + variance_sum);

                if (residual < this.geoMission.getFilterDispatchResidualThreshold()) {
                    log.debug("Dispatching Result From # Observations: " + this.observations.size());
                    log.debug("Residual Movements: "+residual);
                    //log.debug("Residual Variance: "+variance_sum);
                    log.debug("Residual Measurement Delta: "+residual_rk);
                    log.debug("Residual Innovation: "+innov);
                    log.debug("Covariance: "+Pk);

                    if (log.isDebugEnabled()) {
                        for (FilterObservationDTO obs_state : filterObservationDTOs) {
                            double f_est_adj = obs_state.getF_est();
                            if (obs_state.getObs().getObservationType().equals(ObservationType.tdoa)) {
                                f_est_adj = f_est_adj / Helpers.SPEED_OF_LIGHT;
                            }
                            else if (obs_state.getObs().getObservationType().equals(ObservationType.aoa)) {
                                f_est_adj = f_est_adj * Math.PI / 180;
                            }
                            log.debug("Observation utilisation: assets: ["+obs_state.getObs().getAssetId()+"/"+obs_state.getObs().getAssetId_b()+"_"+obs_state.getObs().getObservationType().name()+":"+obs_state.getObs().getMeas()+"] [meas], f_est(adj): " + f_est_adj + ", innov: "+obs_state.getInnov()+", MeasError: "+obs_state.getObs().getMeas_error());
                        }
                    }

                    startTime = Calendar.getInstance().getTimeInMillis();

                    dispatchResult(Xk);

                    if (geoMission.getMissionMode().equals(MissionMode.fix)) {
                        // if (residual < this.geoMission.getFilterConvergenceResidualThreshold()) {
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
                    }
                }
                else {
                    log.trace("Residual not low enough to export result: "+residual);
                }
            }
        }
    }

    public RealMatrix recalculateH(double x_rssi, double y_rssi, double Xk1, double Xk2) {

        double R1 = Math.sqrt(Math.pow((x_rssi-Xk1),2) + Math.pow(y_rssi-Xk2,2));

        double dfdx = -(x_rssi-Xk1)/R1;
        double dfdy = -(y_rssi-Xk2)/R1;

        double[][] jacobianData = {{dfdx, dfdy}};
        RealMatrix H = new Array2DRowRealMatrix(jacobianData);
        return H;
    }

    public RealMatrix recalculateH_TDOA(double x, double y, double x2, double y2, double Xk1, double Xk2) {

        double R1 = Math.sqrt(Math.pow((x-Xk1),2) + Math.pow(y-Xk2,2));
        double R2 = Math.sqrt(Math.pow((x2-Xk1),2) + Math.pow(y2-Xk2,2));

        double dfdx = (-x+Xk1)/R1 - (-x2+Xk1)/R2;
        double dfdy = (-y+Xk2)/R1 - (-y2+Xk2)/R2;

        double[][] jacobianData = {{dfdx, dfdy}};
        RealMatrix H = new Array2DRowRealMatrix(jacobianData);
        return H;
    }

    public RealMatrix recalculateH_AOA(double x, double y, double Xk1, double Xk2) {

        double R1 = Math.sqrt(Math.pow((x-Xk1),2) + Math.pow((y-Xk2),2)); // Note: better performance using sqrt

        double dfdx = (y-Xk2)/R1;  // Note d/d"x" = "y - y_est"/..... on purpose linearisation
        double dfdy = -(x-Xk1)/R1;

        double[][] jacobianData = {{dfdx, dfdy}};
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

        /* Compute probability ELP */
        double[][] covMatrix=new double[][]{{Pk.getEntry(0,0),Pk.getEntry(0,1)},{Pk.getEntry(1,0),Pk.getEntry(1,1)}};
        double[] evalues = Helpers.getEigenvalues(covMatrix);
        double largestEvalue = Math.max(evalues[0],evalues[1]);
        double smallestEvalue = Math.min(evalues[0],evalues[1]);
        double[] evector = Helpers.getEigenvector(covMatrix, largestEvalue);
        /* Alternative is to use this
         *  RealMatrix J2 = new Array2DRowRealMatrix(covMatrix);
            EigenDecomposition eig = new EigenDecomposition(J2);
            double[] evalues = eig.getRealEigenvalues();
            log.debug("#4 E-values: "+evalues[0]+","+evalues[1]);
            log.debug("#1 E-vector: "+evector[0]+","+evector[1]);*/
        double rot = Math.atan(evector[1] / evector[0]);
        /* This angle is between -pi -> pi, adjust 0->2pi */
        if (rot<0)
            rot = rot + 2*Math.PI;
        /* Ch-square distribution for two degrees freedom: 1.39 equiv 50% (i.e. CEP), 5.991 equiv 95% C.I, 4.605 equiv 90% C.I, 9.210 equiv 99% C.I */
        double half_major_axis_length = Math.sqrt(largestEvalue)*1.39; // Orig used: 2*Math.sqrt(9.210*largestEvalue);
        double half_minor_axis_length = Math.sqrt(smallestEvalue)*1.39;
        this.geoMission.getTarget().setElp_major(half_major_axis_length*10000); /* UTM -> [m]: X10^4 */
        this.geoMission.getTarget().setElp_minor(half_minor_axis_length*10000);
        this.geoMission.getTarget().setElp_rot(rot);

        this.efusionListener.result(geoMission.getGeoId(),latLon[0],latLon[1], this.geoMission.getTarget().getElp_major(), this.geoMission.getTarget().getElp_minor(), rot);

        if (this.geoMission.getOutputFilterState() && kmlFileHelpers !=null) {
            kmlFileHelpers.writeCurrentExports(this.geoMission);
        }

        if (this.geoMission.getOutputKml()) {
            KmlFileStaticHelpers.exportGeoMissionToKml(this.geoMission);
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
