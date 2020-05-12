package tech.tgo.efusion.compute;

import org.apache.commons.math3.linear.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tgo.efusion.EfusionListener;
import tech.tgo.efusion.model.*;
import tech.tgo.efusion.util.ConfigurationException;
import tech.tgo.efusion.util.Helpers;
import tech.tgo.efusion.util.KmlFileHelpers;
import tech.tgo.efusion.util.KmlFileStaticHelpers;

import javax.imageio.spi.ServiceRegistry;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Extended Kalman Filter Fusion Processor
 * @author Timothy Edge (timmyedge)
 */
public class ComputeProcessorAL1 implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ComputeProcessorAL1.class);

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

    List<FilterExecution> filterExecutions = null;
    //FilterExecution filterExecution = null;

    /*
     * Create processor for the given config, observations and client implemented listener
     */
    public ComputeProcessorAL1(EfusionListener efusionListener, Map<Long,Observation> observations, GeoMission geoMission)
    {
        this.efusionListener = efusionListener;
        this.geoMission = geoMission;
        //this.filterExecution = filterExecution;

        setObservations(observations);
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

    public void initialiseFilter() throws Exception {
//        double[][] measurementNoiseData = {{geoMission.getFilterMeasurementError()}}; DEPRECATED
//        Rk = new Array2DRowRealMatrix(measurementNoiseData);

        double[][] procNoiseData = geoMission.getFilterProcessNoise();
        Qu = new Array2DRowRealMatrix(procNoiseData);

        /* Initialise filter state */
        log.debug("Using InitialStateMode: "+geoMission.getInitialStateMode());
        filterExecutions = new ArrayList<FilterExecution>();
        Double[] start_x_y;
        // Specific Initial State
        if (geoMission.getInitialStateMode().equals(InitialStateMode.specified)) {

            //if (this.geoMission.getFilterUseSpecificInitialCondition()!=null && this.geoMission.getFilterUseSpecificInitialCondition()) {
            double[] asset_utm = Helpers.convertLatLngToUtmNthingEasting(this.geoMission.getFilterSpecificInitialLat(), this.geoMission.getFilterSpecificInitialLon());
            start_x_y = new Double[]{asset_utm[1], asset_utm[0]};
            log.debug("Using SPECIFIC initial condition: "+this.geoMission.getFilterSpecificInitialLat()+", "+this.geoMission.getFilterSpecificInitialLon()+" ["+start_x_y[1]+", "+start_x_y[0]+"]");
            filterExecutions.add(new FilterExecution(start_x_y));
        }
        // Random Initial State
        else if (geoMission.getInitialStateMode().equals(InitialStateMode.random)){
            List<Asset> assetList = new ArrayList<Asset>(this.geoMission.getAssets().values());
            if (assetList.size() > 1) {
                Random rand = new Random();
                Asset randAssetA = assetList.get(rand.nextInt(assetList.size()));
                assetList.remove(randAssetA);
                Asset randAssetB = assetList.get(rand.nextInt(assetList.size()));
                //log.debug("Finding rudimentary start point between two random observations: " + randAssetA.getId() + "," + randAssetB.getId());
                start_x_y = Helpers.findRudimentaryStartPoint(randAssetA, randAssetB, (Math.random() - 0.5) * 100000);
                log.debug("Using RANDOM initial condition: near asset(s) ['"+randAssetA.getId() + "' & '" + randAssetB.getId()+"']: "+start_x_y[1]+", "+start_x_y[0]);

                log.debug("Dist between assets: "+Math.sqrt(Math.pow(randAssetA.getCurrent_loc()[0] - randAssetB.getCurrent_loc()[0], 2) + Math.pow(randAssetA.getCurrent_loc()[1] - randAssetB.getCurrent_loc()[1], 2)));
            } else {
                Asset asset = assetList.get(0);
                double[] asset_utm = Helpers.convertLatLngToUtmNthingEasting(asset.getCurrent_loc()[0], asset.getCurrent_loc()[1]);
                start_x_y = new Double[]{asset_utm[1] + 5000, asset_utm[0] - 5000};
                log.debug("Using RANDOM initial condition: near asset ["+asset.getId()+"]: "+start_x_y[1]+", "+start_x_y[0]);
            }
            filterExecutions.add(new FilterExecution(start_x_y));
        }
        else if (geoMission.getInitialStateMode().equals(InitialStateMode.top_right)) {


//            // TEMP, TODO delete
//            Double[] cornerLatLon = Helpers.getCornerLatLon(InitialStateBoxCorner.BOTTOM_RIGHT, geoMission.getAssets().values());
//            if (cornerLatLon==null) {
//                log.error("Error getting corner lat lon for corner BOTTOM_RIGHT");
//                throw new ConfigurationException("Error getting corner lat lon for corner BOTTOM_RIGHT");
//            }
//            filterExecutions.add(new FilterExecution(cornerLatLon));



            Double[] cornerLatLon = Helpers.getCornerLatLon(InitialStateBoxCorner.TOP_RIGHT, geoMission.getAssets().values());
            if (cornerLatLon==null) {
                log.error("Error getting corner lat lon for corner TOP_RIGHT");
                throw new ConfigurationException("Error getting corner lat lon for corner TOP_RIGHT");
            }
            filterExecutions.add(new FilterExecution(cornerLatLon));
        }
        else if (geoMission.getInitialStateMode().equals(InitialStateMode.bottom_right)) {
            Double[] cornerLatLon = Helpers.getCornerLatLon(InitialStateBoxCorner.BOTTOM_RIGHT, geoMission.getAssets().values());
            if (cornerLatLon==null) {
                log.error("Error getting corner lat lon for corner BOTTOM_RIGHT");
                throw new ConfigurationException("Error getting corner lat lon for corner BOTTOM_RIGHT");
            }
            filterExecutions.add(new FilterExecution(cornerLatLon));
        }
        else if (geoMission.getInitialStateMode().equals(InitialStateMode.bottom_left)) {
            Double[] cornerLatLon = Helpers.getCornerLatLon(InitialStateBoxCorner.BOTTOM_LEFT, geoMission.getAssets().values());
            if (cornerLatLon==null) {
                log.error("Error getting corner lat lon for corner BOTTOM_LEFT");
                throw new ConfigurationException("Error getting corner lat lon for corner BOTTOM_LEFT");
            }
            filterExecutions.add(new FilterExecution(cornerLatLon));
        }
        else if (geoMission.getInitialStateMode().equals(InitialStateMode.top_left)) {
            Double[] cornerLatLon = Helpers.getCornerLatLon(InitialStateBoxCorner.TOP_LEFT, geoMission.getAssets().values());
            if (cornerLatLon==null) {
                log.error("Error getting corner lat lon for corner TOP_LEFT");
                throw new ConfigurationException("Error getting corner lat lon for corner TOP_LEFT");
            }
            filterExecutions.add(new FilterExecution(cornerLatLon));
        }
        // NOTE: Box executions only apply to FIX, since TRACKING just uses the previous state
        else if (geoMission.getInitialStateMode().equals(InitialStateMode.box_single_out)) {
            // Use all corners, iterate until first result and exit
            filterExecutions.add(new FilterExecution(Helpers.getCornerLatLon(InitialStateBoxCorner.TOP_RIGHT, geoMission.getAssets().values())));
            filterExecutions.add(new FilterExecution(Helpers.getCornerLatLon(InitialStateBoxCorner.BOTTOM_RIGHT, geoMission.getAssets().values())));
            filterExecutions.add(new FilterExecution(Helpers.getCornerLatLon(InitialStateBoxCorner.BOTTOM_LEFT, geoMission.getAssets().values())));
            filterExecutions.add(new FilterExecution(Helpers.getCornerLatLon(InitialStateBoxCorner.TOP_LEFT, geoMission.getAssets().values())));
        }
        else if (geoMission.getInitialStateMode().equals(InitialStateMode.box_all_out)) {
            // use all corners, report all results
            filterExecutions.add(new FilterExecution(Helpers.getCornerLatLon(InitialStateBoxCorner.BOTTOM_RIGHT, geoMission.getAssets().values())));
            filterExecutions.add(new FilterExecution(Helpers.getCornerLatLon(InitialStateBoxCorner.TOP_LEFT, geoMission.getAssets().values())));
            filterExecutions.add(new FilterExecution(Helpers.getCornerLatLon(InitialStateBoxCorner.TOP_RIGHT, geoMission.getAssets().values())));
            filterExecutions.add(new FilterExecution(Helpers.getCornerLatLon(InitialStateBoxCorner.BOTTOM_LEFT, geoMission.getAssets().values())));
        }
        else {
            throw new ConfigurationException("Could not identify a valid 'Initial State' search strategy, check configuration");
        }

        log.debug("# Filter Executions: "+ filterExecutions.size());
        for (FilterExecution filterExecution : filterExecutions) {
            double[] latLon = Helpers.convertUtmNthingEastingToLatLng(filterExecution.getLatlon()[0],filterExecution.getLatlon()[1], this.geoMission.getLatZone(), this.geoMission.getLonZone());
            log.debug("Start State: lat/lon: "+latLon[0]+","+latLon[1]+" UTM: ["+ filterExecution.getLatlon()[0]+","+ filterExecution.getLatlon()[1]+"]");
        }

        // MOVED to runExecution
//        RealVector Xinit = new ArrayRealVector(start_x_y);
//        Xk = Xinit;
//        Pk = Pinit.scalarMultiply(0.01);  // AL1 Tends to lose itself fail to converge, unless this kept small (i.e. ~0.01). AL0 Originally used 1000

        log.trace("Initialising Stage Observations as current observations, #: "+this.geoMission.observations.size());
        setStaged_observations(this.geoMission.observations);
    }

    public void run() {

        // IF FIX MODE, run all executions, combine results and report
        // IF TRACKING MODE, run the single execution, exit once process manually stopped

        if (this.geoMission.getMissionMode().equals(MissionMode.fix)) {
            // Run each execution
            int j = 0;
            List<GeolocationResult> geolocationResults = new ArrayList<GeolocationResult>();
            for (FilterExecution filterExecution : filterExecutions) {
                log.debug("Running execution: " + (j+1) + " / " + filterExecutions.size());
                GeolocationResult geolocationResult = runFixExecution(filterExecution);
                geolocationResults.add(geolocationResult);

                if (this.geoMission.getInitialStateMode().equals(InitialStateMode.box_single_out)) {
                    if (geolocationResult.getResidual() < this.geoMission.getFilterConvergenceResidualThreshold()) {
                        log.debug("Box Single Out initial search strategy, this result is good enough, exiting and reporting");
                        break;
                    }
                }
                j++;
                log.debug("Finished execution: "+j);
            }
            log.debug("Finished all executions");

            /* Sort by residual_rk */
            List<GeolocationResult> sorted = sortByResidualRk(geolocationResults);
            for (GeolocationResult gr : sorted) {
                log.debug("GeoResult: resk: "+ gr.getResidual_rk()+", "+gr.getLat()+","+gr.getLon()+", elp:"+gr.getElp_long()+", res:"+gr.getResidual());
            }
            ComputeResults computeResults = new ComputeResults();
            computeResults.setGeolocationResult(sorted.get(0));
            sorted.remove(0);
            computeResults.setAdditionalResults(sorted);
            computeResults.setGeoId(this.geoMission.getGeoId());
//            ComputeResults computeResults = new ComputeResults();
//            computeResults.setGeolocationResult(geolocationResults.get(0));
//            computeResults.setAdditionalResults(geolocationResults);
//            computeResults.setGeoId(this.geoMission.getGeoId());

            /* Dispatch Result */
            dispatchResult(computeResults);
        }

        else if (this.geoMission.getMissionMode().equals(MissionMode.track)) {
            // simply just start it up and run always

            // Run each execution
            int j = 0;
            List<GeolocationResult> geolocationResults = new ArrayList<GeolocationResult>();
            FilterExecution filterExecution = filterExecutions.iterator().next();

            /* BEGIN ONLINE LOOP */
            runTrackingExecution(filterExecution);
        }

        log.debug("FINISHED FILTER THREAD");
    }

    // Runs a single FIX execution
    public GeolocationResult runFixExecution(FilterExecution filterExecution) {

        // Set Xk,Pk
        log.debug("Running Fix Execution with init conds: "+filterExecution.getLatlon()[0]+","+filterExecution.getLatlon()[1]);
        RealVector Xinit = new ArrayRealVector(filterExecution.getLatlon());
        Xk = Xinit;
        Pk = Pinit.scalarMultiply(0.01);  // AL1 Tends to lose itself fail to converge, unless this kept small (i.e. ~0.01). AL0 Originally used 1000
        log.debug("Running Fix Execution with Init State: "+Xk);
        log.debug("Running Fix Execution with Init State Covariance: "+Pk);

        log.info("Running for # observations:"+observations.size());
        if (observations.size()==0) {
            log.info("No observations returning");
            return new GeolocationResult();
        }

        running.set(true);

        // May want to just clear the kml output state here, plot the asset locations, instead of full dispatch
        //dispatchResult(Xk);

        FilterStateDTO filterStateDTO = new FilterStateDTO();

        if (this.geoMission.getOutputFilterState()) {
            kmlFileHelpers = new KmlFileHelpers();
            kmlFileHelpers.provisionFilterStateExport();
            log.debug("Provisioned filter state export");
        }
        int filterStateExportCounter = 0;

        // APPLY FOR FIX OR TRACKING EXECUTION CONTROL
        log.debug("Running for Max # filter iterations: "+geoMission.getMaxFilterIterations());
            for (int k=0;k<this.geoMission.getMaxFilterIterations();k++)
            {
                // RUN THROTTLING
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

                filterExecution = runFilterIteration(filterExecution);

                /* A measure of residual changes the filter intends to make */
                double residual = Math.abs(innov.getEntry(0)) + Math.abs(innov.getEntry(1));

                if (residual < this.geoMission.getFilterConvergenceResidualThreshold()) {
                    log.debug("Exiting since this is a FIX Mode run and filter has converged to threshold. Number of iterations: "+k);
                    running.set(false);
                    break;
                }

            } // END FOR MAX ITERATIONS
            log.debug("Finished FIX iterations for this Execution");

            // After the max iterations, OR the desired accuracy level reached, dispatch result??

            /* Export filter state - development debugging */
            if (this.geoMission.getOutputFilterState()) {
                filterStateExportCounter++;
                if (filterStateExportCounter == 10) {
                    // Only if it is changing significantly
                    double residual = Math.abs(innov.getEntry(0)) + Math.abs(innov.getEntry(1));
                    if (residual > 0.5) {
                        filterStateDTO.setFilterObservationDTOs(filterExecution.getFilterObservationDTOs());
                        filterStateDTO.setXk(Xk);
                        kmlFileHelpers.exportAdditionalFilterState(this.geoMission, filterStateDTO, residual);
                        filterStateExportCounter = 0;
                    }
                }
            }

            if (log.isDebugEnabled()) {
                for (FilterObservationDTO obs_state : filterExecution.getFilterObservationDTOs()) {
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

        GeolocationResult geolocationResult = summariseResult(Xk, filterExecution);
        Xk = null;
        Pk=null;
        K=null;
        Thi = new Array2DRowRealMatrix(ThiData);
        Pinit = new Array2DRowRealMatrix(initCovarData);
        setStaged_observations(this.geoMission.observations);

        return geolocationResult;
    }

    public void runTrackingExecution(FilterExecution filterExecution) {

        RealVector Xinit = new ArrayRealVector(filterExecution.getLatlon());
        Xk = Xinit;
        Pk = Pinit.scalarMultiply(0.01);  // AL1 Tends to lose itself fail to converge, unless this kept small (i.e. ~0.01). AL0 Originally used 1000

        log.info("Running for # observations:"+observations.size());
        if (observations.size()==0) {
            log.info("No observations returning");
            return;
        }

        running.set(true);

        FilterStateDTO filterStateDTO = new FilterStateDTO();

        if (this.geoMission.getOutputFilterState()) {
            kmlFileHelpers = new KmlFileHelpers();
            kmlFileHelpers.provisionFilterStateExport();
            log.debug("Provisioned filter state export");
        }
        int filterStateExportCounter = 0;

        long startTime = Calendar.getInstance().getTimeInMillis();

        ComputeResults computeResults = new ComputeResults();
        computeResults.setGeoId(this.geoMission.getGeoId());

        while (true) {

            // RUN THROTTLING
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

            filterExecution = runFilterIteration(filterExecution);

            /* Export filter state - development debugging */
            if (this.geoMission.getOutputFilterState()) {
                filterStateExportCounter++;
                if (filterStateExportCounter == 10) {
                    // Only if it is changing significantly
                    double residual = Math.abs(innov.getEntry(0)) + Math.abs(innov.getEntry(1));
                    if (residual > 0.5) {
                        filterStateDTO.setFilterObservationDTOs(filterExecution.getFilterObservationDTOs());
                        filterStateDTO.setXk(Xk);
                        kmlFileHelpers.exportAdditionalFilterState(this.geoMission, filterStateDTO, residual);
                        filterStateExportCounter = 0;
                    }
                }
            }

            /* Export Result - FOR TRACKING, EXPORT ONLY AFTER CERTAIN TIMINGS */
            if ((Calendar.getInstance().getTimeInMillis() - startTime) > this.geoMission.getDispatchResultsPeriod()) {

                /* A measure of consistency between types of observations */
                double residual_rk = findResidualRk(filterExecution.getFilterObservationDTOs());

                /* A measure of residual changes the filter intends to make */
                double residual = Math.abs(innov.getEntry(0)) + Math.abs(innov.getEntry(1));

                if (residual < this.geoMission.getFilterDispatchResidualThreshold()) {
                    log.debug("Dispatching Result From # Observations: " + this.observations.size());
                    log.debug("Residual Movements: "+residual);
                    log.debug("Residual Measurement Delta: "+residual_rk);
                    log.debug("Residual Innovation: "+innov);
                    log.debug("Covariance: "+Pk);

                    if (log.isDebugEnabled()) {
                        for (FilterObservationDTO obs_state : filterExecution.getFilterObservationDTOs()) {
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

                    GeolocationResult result = summariseResult(Xk, filterExecution);
                    computeResults.setGeolocationResult(result);
                    dispatchResult(computeResults);

                    log.debug("This is a Tracking mode run, using latest observations (as held in staging) and continuing...");

                    /* Resynch latest observations, and reinitialise with current state estimate */
                    log.debug("# Staged observations: "+this.staged_observations.size());
                    setObservations(this.staged_observations);
                }
                else {
                    log.trace("Residual not low enough to export result: "+residual);
                }
            }
        }
    }

    public FilterExecution runFilterIteration(FilterExecution filterExecution)
    {
        //log.debug("Xk: "+Xk);

        Xk = Thi.operate(Xk);

            Pk = (Thi.multiply(Pk).multiply(Thi.transpose())).add(Qu);

            /* reinitialise various collections */
            innov = new ArrayRealVector(innovd);
            P_innov = new Array2DRowRealMatrix(P_innovd);
            filterExecution.getFilterObservationDTOs().removeAllElements();
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


                    RealMatrix toInvert = (H.multiply(Pk).multiply(H.transpose()).add(new Array2DRowRealMatrix(new double[][]{{obs.getMeas_error()}})));
                    Inverse = (new LUDecomposition(toInvert)).getSolver().getInverse();
                }

                K = Pk.multiply(H.transpose()).multiply(Inverse);

                double rk = d - f_est;

                /* RUN 360-0 CONUNDRUM FIX */
                //log.info("RUNNING CONUNDRUM TEST AND FIX");

                RealVector innov_ = K.scalarMultiply(rk).getColumnVector(0);    /// rk - HXk[0]  -removed in AL1

                innov = innov_.add(innov);

                P_innov = K.multiply(H).multiply(Pk).add(P_innov);

                filterExecution.getFilterObservationDTOs().add(new FilterObservationDTO(obs, f_est, innov_));
            }

            Xk = Xk.add(innov);
            Pk = (eye.multiply(Pk)).subtract(P_innov);

            return filterExecution;
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

    /* A measure of difference in contributions of each observation, for relative comparison of disparate optimal estimates */
    public double findResidualRk(Vector<FilterObservationDTO> filterObservationDTOs) {
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
        return residual_rk;
    }

    public GeolocationResult summariseResult(RealVector Xk, FilterExecution filterExecution) {

        double[] latLon = Helpers.convertUtmNthingEastingToLatLng(Xk.getEntry(0),Xk.getEntry(1), this.geoMission.getLatZone(), this.geoMission.getLonZone());

        /* Compute probability ELP */
        double[][] covMatrix=new double[][]{{Pk.getEntry(0,0),Pk.getEntry(0,1)},{Pk.getEntry(1,0),Pk.getEntry(1,1)}};
        double[] evalues = Helpers.getEigenvalues(covMatrix);
        double largestEvalue = Math.max(evalues[0],evalues[1]);
        double smallestEvalue = Math.min(evalues[0],evalues[1]);
        double[] evector = Helpers.getEigenvector(covMatrix, largestEvalue);
        double rot = Math.atan(evector[1] / evector[0]);
        /* This angle is between -pi -> pi, adjust 0->2pi */
        if (rot<0)
            rot = rot + 2*Math.PI;
        /* Ch-square distribution for two degrees freedom: 1.39 equiv 50% (i.e. CEP), 5.991 equiv 95% C.I, 4.605 equiv 90% C.I, 9.210 equiv 99% C.I */
        double half_major_axis_length = Math.sqrt(largestEvalue)*1.39; // Orig used: 2*Math.sqrt(9.210*largestEvalue);
        double half_minor_axis_length = Math.sqrt(smallestEvalue)*1.39;

        /* A measure of the aggreance by all measurements */
        double residual_rk = findResidualRk(filterExecution.getFilterObservationDTOs());

        /* A measure of residual changes the filter intends to make */
        double residual = Math.abs(innov.getEntry(0)) + Math.abs(innov.getEntry(1));

        log.debug("Dispatching Result From # Observations: " + this.observations.size());
        log.debug("Result: "+latLon[0]+","+latLon[1]);
        log.debug("Residual Movements: "+residual);
        log.debug("Residual Measurement Delta: "+residual_rk);
        log.debug("Residual Innovation: "+innov);
        log.debug("Covariance: "+Pk);

        GeolocationResult geolocationResult = new GeolocationResult();
        geolocationResult.setLat(latLon[0]);
        geolocationResult.setLon(latLon[1]);
        geolocationResult.setElp_long(half_major_axis_length*10000);
        geolocationResult.setElp_short(half_minor_axis_length*10000);
        geolocationResult.setElp_rot(rot);
        geolocationResult.setResidual(residual);
        geolocationResult.setResidual_rk(residual_rk);

        return geolocationResult;
    }

    public void dispatchResult(ComputeResults computeResults) {

        this.geoMission.getTarget().setCurrent_loc(new double[]{computeResults.getGeolocationResult().getLat(),computeResults.getGeolocationResult().getLon()});
        this.geoMission.setComputeResults(computeResults);
        //this.efusionListener.result(geoMission.getGeoId(),latLon[0],latLon[1], this.geoMission.getTarget().getElp_major(), this.geoMission.getTarget().getElp_minor(), rot);
        this.efusionListener.result(computeResults);

        if (this.geoMission.getOutputFilterState() && kmlFileHelpers !=null) {
            /* Write non-static file exports, includes filter state data stored in mem */
            kmlFileHelpers.writeCurrentExports(this.geoMission);
        }

        if (this.geoMission.getOutputKml()) {
            KmlFileStaticHelpers.exportGeoMissionToKml(this.geoMission);
        }
    }



//    public void dispatchResult(RealVector Xk) {
//
//        double[] latLon = Helpers.convertUtmNthingEastingToLatLng(Xk.getEntry(0),Xk.getEntry(1), this.geoMission.getLatZone(), this.geoMission.getLonZone());
//        this.geoMission.getTarget().setCurrent_loc(latLon);
//
//        /* Compute probability ELP */
//        double[][] covMatrix=new double[][]{{Pk.getEntry(0,0),Pk.getEntry(0,1)},{Pk.getEntry(1,0),Pk.getEntry(1,1)}};
//        double[] evalues = Helpers.getEigenvalues(covMatrix);
//        double largestEvalue = Math.max(evalues[0],evalues[1]);
//        double smallestEvalue = Math.min(evalues[0],evalues[1]);
//        double[] evector = Helpers.getEigenvector(covMatrix, largestEvalue);
//        /* Alternative is to use this
//         *  RealMatrix J2 = new Array2DRowRealMatrix(covMatrix);
//            EigenDecomposition eig = new EigenDecomposition(J2);
//            double[] evalues = eig.getRealEigenvalues();
//            log.debug("#4 E-values: "+evalues[0]+","+evalues[1]);
//            log.debug("#1 E-vector: "+evector[0]+","+evector[1]);*/
//        double rot = Math.atan(evector[1] / evector[0]);
//        /* This angle is between -pi -> pi, adjust 0->2pi */
//        if (rot<0)
//            rot = rot + 2*Math.PI;
//        /* Ch-square distribution for two degrees freedom: 1.39 equiv 50% (i.e. CEP), 5.991 equiv 95% C.I, 4.605 equiv 90% C.I, 9.210 equiv 99% C.I */
//        double half_major_axis_length = Math.sqrt(largestEvalue)*1.39; // Orig used: 2*Math.sqrt(9.210*largestEvalue);
//        double half_minor_axis_length = Math.sqrt(smallestEvalue)*1.39;
//        this.geoMission.getTarget().setElp_major(half_major_axis_length*10000); /* UTM -> [m]: X10^4 */
//        this.geoMission.getTarget().setElp_minor(half_minor_axis_length*10000);
//        this.geoMission.getTarget().setElp_rot(rot);
//
//        this.efusionListener.result(geoMission.getGeoId(),latLon[0],latLon[1], this.geoMission.getTarget().getElp_major(), this.geoMission.getTarget().getElp_minor(), rot);
//
//        if (this.geoMission.getOutputFilterState() && kmlFileHelpers !=null) {
//            kmlFileHelpers.writeCurrentExports(this.geoMission);
//        }
//
//        if (this.geoMission.getOutputKml()) {
//            KmlFileStaticHelpers.exportGeoMissionToKml(this.geoMission);
//        }
//    }

    public List<GeolocationResult> sortByResidualRk(List<GeolocationResult> geolocationResults) {

        Collections.sort(geolocationResults, (o1, o2) -> (((Double)o1.residual_rk).compareTo((Double)o2.getResidual_rk())));
//        Comparator<GeolocationResult> valueComparator = new Comparator<GeolocationResult>() {
//            @Override
//            public int compare (GeolocationResult g1, GeolocationResult g2){
//                return g1.getResidual_rk().compareTo(g2.getResidual_rk());
//            }
//        };
////            @Override
////            public int compare(Map.Entry<Long, GeolocationResult> e1, Map.Entry<Long, GeolocationResult> e2) {
////                Double v1 = e1.getValue().getResidual_rk();
////                Double v2 = e2.getValue().getResidual_rk();
////                return v1.compareTo(v2);
////            }
//
//        //List<Map.Entry<Long, GeolocationResult>> listOfEntries = new ArrayList<Map.Entry<Long, GeolocationResult>>(geolocationResults);
//        Collections.sort(geolocationResults, valueComparator);
//        List<GeolocationResult> sorted = new ArrayList<GeolocationResult>();
//        for(List<GeolocationResult> entry : geolocationResults){
//            sorted.add(entry.getValue());
//        }
        return geolocationResults;
    }


//    public void reinitialiseFilter() {
//        /* Select two assets by random and use their middle point */
//        Random rand = new Random();
//        List<Asset> assetList = new ArrayList<Asset>(this.geoMission.getAssets().values());
//        Asset randAssetA = assetList.get(rand.nextInt(assetList.size()));
//        assetList.remove(randAssetA);
//        Asset randAssetB = assetList.get(rand.nextInt(assetList.size()));
//        log.debug("Finding rudimentary start point between two random observations: "+randAssetA.getId()+","+randAssetB.getId());
//
//        double[] start_x_y = findRudimentaryStartPoint(randAssetA, randAssetB, -500);
//        log.debug("(Re) Filter start point: "+start_x_y[0]+","+start_x_y[1]);
//        double[] initStateData = {start_x_y[0], start_x_y[1], 1, 1};
//        log.info("(Re) Init State Data Easting/Northing: "+initStateData[0]+","+initStateData[1]+",1,1");
//        double[] latLonStart = Helpers.convertUtmNthingEastingToLatLng(initStateData[0], initStateData[1], geoMission.getLatZone(), geoMission.getLonZone());
//        log.info("(Re) Init start point: "+latLonStart[0]+","+latLonStart[1]);
//        RealVector Xinit = new ArrayRealVector(initStateData);
//        Xk = Xinit;
//    }

    public synchronized void setStaged_observations(Map<Long, Observation> staged_observations) {
        this.staged_observations = staged_observations;
    }

    public boolean isRunning() {
        return this.running.get();
    }

    public void stopThread() {
        this.running.set(false);
    }
}
