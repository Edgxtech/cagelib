/*
 * Geolocation fusion and tracking, using custom extended kalman filter implementation
 *
 * @author Timothy Edge (timmyedge)
 */
package tech.tgo.fuzer.thread;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.LUDecomposition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tgo.fuzer.FuzerListener;
import tech.tgo.fuzer.model.GeoMission;
import tech.tgo.fuzer.model.FuzerMode;
import tech.tgo.fuzer.model.Observation;
import tech.tgo.fuzer.model.ObservationType;
import tech.tgo.fuzer.util.Helpers;
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

    double[][] procNoiseData = { {0.01, 0, 0, 0}, {0, 0.01, 0 ,0}, {0, 0, 0.01, 0}, {0, 0, 0 ,0.01}}; // orig: 0.01
    RealMatrix Qu = new Array2DRowRealMatrix(procNoiseData);

    double[][] initCovarData = {{1, 0, 0, 0}, {0, 1, 0 ,0}, {0, 0, 1, 0}, {0, 0, 0 ,1}};
    RealMatrix Pinit = new Array2DRowRealMatrix(initCovarData);

    RealMatrix Rk;

    /* State and Covariance */
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

    boolean restrict_non_aoa = false;

    /*
     * Create an algorithm tracker process for the given config, observations and client implemented listener
     */
    public AlgorithmEKF(FuzerListener fuzerListener, Map<Long,Observation> observations, GeoMission geoMission)
    {
        this.fuzerListener = fuzerListener;
        this.observations = observations;
        this.geoMission = geoMission;

        /* Initialise filter configurable properties */
        double[][] measurementNoiseData = {{geoMission.getFilterMeasurementError()}}; // Smaller for trusted measurements. Guide: {0.01 -> 0.1}
        Rk = new Array2DRowRealMatrix(measurementNoiseData);

        /* Initialise filter state */
        log.debug("Finding rudimentary start point between first two observations");
        double[] start_x_y = findRudimentaryStartPoint(this.observations.values(), 5000);
        log.debug("Filter start point: "+start_x_y[0]+","+start_x_y[1]);
        double[] initStateData = {start_x_y[0], start_x_y[1], 1, 1};
        //double[] initStateData = {406911, 6503338, 0.1, 0.1};  // here temp
        //double[] initStateData = {404032,6469090,1,1}; // goes to wrong branch
        //double[] initStateData = {397775.4666593331,6392539,1,1};

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

        Vector<double[]> AOA_rks = new Vector<double[]>(); // DEV - SAFETY FEATURE for getting correct branches

        long startTime = Calendar.getInstance().getTimeInMillis();

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
            AOA_rks.removeAllElements();

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

                    if (!restrict_non_aoa) {
                        H = recalculateH(obs.getX(), obs.getY(), xk, yk);

                        f_est = Math.sqrt(Math.pow((obs.getX() - xk), 2) + Math.pow(obs.getY() - yk, 2));

                        d = obs.getRange();

                        AOA_rks.add(new double[]{f_est, d});

                        log.trace("RANGE innovation: " + f_est + ", vs d: " + d);
                    }
                } else if (obs.getObservationType().equals(ObservationType.tdoa)) {

                    if (!restrict_non_aoa) {
                        H = recalculateH_TDOA(obs.getX(), obs.getY(), obs.getXb(), obs.getYb(), xk, yk);

                        f_est = Math.sqrt(Math.pow((obs.getX() - xk), 2) + Math.pow(obs.getY() - yk, 2)) - Math.sqrt(Math.pow((obs.getXb() - xk), 2) + Math.pow(obs.getYb() - yk, 2));

                        d = obs.getTdoa() * Helpers.SPEED_OF_LIGHT;

                        AOA_rks.add(new double[]{f_est, d});

                        log.trace("TDOA innovation: " + f_est + ", vs d: " + d);
                    }
                } else if (obs.getObservationType().equals(ObservationType.aoa)) {

                    H = recalculateH_AOA(obs.getX(), obs.getY(), xk, yk);

                    f_est = Math.atan((obs.getY() - yk)/(obs.getX() - xk))*180/Math.PI;
                    //f_est = Math.atan((yk - obs.getY()) / (xk - obs.getX())) * 180 / Math.PI;

                    if (xk < obs.getX()) {
                        f_est = f_est + 180;
                    }

                    // TODO, need something which says if both AOA is forcing filter in a way opposite to any other meas, need to let AOA go for a bit

//                    /* 2nd quadrant */
//                    if (yk > obs.getY() && xk < obs.getX() ) {
//                        f_est = f_est + 180;
//                    }
//                    /* 3rd quadrant */
//                    else if (yk <= obs.getY() && xk < obs.getX() ) {
//                        f_est = - (180 - f_est);
//                    }
//                    /* 4th quadrant */
//                    else if (yk <= obs.getY() && xk > obs.getX() ) {
//                        f_est = (f_est);
//                    }




//                    // This seems to work well for 4th quadrant
//                    if (xk < obs.getX()) {
//                        f_est = -(180 - f_est);
//                    }

                    // This for second quad???
//                    if (yk > obs.getY() && xk < obs.getX() ) {
//                        f_est = f_est + Math.PI;
//                    }


                    if (yk<obs.getY() && xk>=obs.getX()) {
                        f_est = (180 - Math.abs(f_est)) + 180;
                        //f_est = 360 - f_est;
                    }

                    d = obs.getAoa() * 180 / Math.PI;

                    log.trace("AOA innovation: " + f_est + ", vs d: " + d);

                    AOA_rks.add(new double[]{f_est, d});
                }

                double rk = d - f_est;

                // ORIG FILTER EQNS
                RealMatrix toInvert = (H.multiply(Pk).multiply(H.transpose()).add(Rk));
                RealMatrix Inverse = (new LUDecomposition(toInvert)).getSolver().getInverse();

                /* Scaling factor, to reduce impact of TDOA and range measures vs AOA measures */
                if (obs.getObservationType().equals(ObservationType.range)) {
                    K = Pk.multiply(H.transpose()).multiply(Inverse).scalarMultiply(0.1);
                }
                else if (obs.getObservationType().equals(ObservationType.tdoa)) {
                    K = Pk.multiply(H.transpose()).multiply(Inverse).scalarMultiply(0.1);
                }
                else {
                    K = Pk.multiply(H.transpose()).multiply(Inverse);
                }
                //K = Pk.multiply(H.transpose()).multiply(Inverse); //--- original

                double[] HXk = H.operate(Xk).toArray();
                innov = K.scalarMultiply(rk - HXk[0]).getColumnVector(0).add(innov);
                log.trace("Innov: "+innov);

                P_innov = K.multiply(H).multiply(Pk).add(P_innov);


                // TODO, attempt normalising the measurements
//                RealMatrix toInvert = (H.multiply(Pk).multiply(H.transpose()).add(Rk));
//                RealMatrix Inverse = (new LUDecomposition(toInvert)).getSolver().getInverse();
//                K = Pk.multiply(H.transpose()).multiply(Inverse);
//
//                double[] HXk = H.operate(Xk).toArray();
//                innov = K.scalarMultiply(rk - HXk[0]).getColumnVector(0).add(innov);
//                log.trace("Innov: "+innov);
//
//                P_innov = K.multiply(H).multiply(Pk).add(P_innov);


                // ATTEMPT AT TRYING TO MATCH MATLAB EQNS
//                RealMatrix toInvert = (H.multiply(Pk).multiply(H.transpose()).add(Rk));
//                RealMatrix Inverse = (new LUDecomposition(toInvert)).getSolver().getInverse();
//                K = Pk.multiply(H.transpose()).multiply(Inverse);
//
//                double[] HXk = H.operate(Xk).toArray();
//                innov = K.scalarMultiply(rk - HXk[0]).getColumnVector(0).add(innov);
//                log.trace("Innov: " + innov);
//
//                P_innov = (K.transpose()).multiply(H).multiply(Pk).add(P_innov);

                // MATLAB EQNS
//                K(i,:) = Pk*H(i,:)'*inv(H(i,:)*Pk*H(i,:)' + Rk(i));          %Pk*H(i,:)'*inv(Rk); T% WORKS SINGLE SENSOR%
//
//                innov = innov + K(i,:)*(r(i,:) - H(i,:)*Xk);
//
//            %P_innov = P_innov + H(i,:)'*inv(Rk(i))*H(i,:);
//                P_innov = P_innov + K(i,:)'*H(i,:)*Pk;
//            }



                // NOTE: Normally this is done after all observation innov's are added together
                //    Doing it here might be required IOT un-normalise the scaling
//                Xk = Xk.add(innov);
//                Pk = (eye.multiply(Pk)).subtract(P_innov);
            }


            // NOTE: Normally this is executed here
            Xk = Xk.add(innov);
            Pk = (eye.multiply(Pk)).subtract(P_innov);
            //log.debug("X: "+Xk.getEntry(0)+","+Xk.getEntry(1)+","+Xk.getEntry(2)+","+Xk.getEntry(3));

            if ((Calendar.getInstance().getTimeInMillis() - startTime) > this.geoMission.getDispatchResultsPeriod()) {

                //restrict_non_aoa = false;

                /* If filter had adequately processed latest observations - prevent dispatching spurious results */
                double residual = Math.abs(Xk.getEntry(2) + Xk.getEntry(3));
                if (residual < this.geoMission.getFilterDispatchResidualThreshold()) {
                    log.debug("Residual: "+residual);

                    // TODO, if there is an AOA with rk still looks ~ opposite sector, try giving it a kick to the correct branch????
                    // i.e. if the filter thinks it converged but looks like its on the wrong branch since at least rks are sustantially off
                    for (double[] rk_data : AOA_rks) {
                        // TODO, should be if all AOA measurements report strange
                        log.debug("Checking rk_data, f_est/d: "+rk_data[0]+","+rk_data[1]);
//                        if ((Math.abs(rk_data[1]-rk_data[0])) > 100) {
//                            log.debug("Trying again since: at least one AOA measurement was indicating substantially different even though filter has converged");
//
////                            // kick it to direct other side (similar distance) of the first asset
////                            Observation firstObs = this.observations.values().iterator().next();
////                            log.debug("Obs: "+(firstObs==null));
////                            log.debug("Obs X: "+firstObs.getX());
////                            log.debug("Filter Xk_x: "+Xk.getEntry(0));
////                            double newEasting = firstObs.getX() + (firstObs.getX() - Xk.getEntry(0));
////                            double newNorthing = firstObs.getY() + (firstObs.getY() - Xk.getEntry(1));
////                            Xk.setEntry(0,newEasting);
////                            Xk.setEntry(1,newNorthing);
////                            Pk = Pinit.scalarMultiply(1000.0);
//                            restrict_non_aoa = true; // just until the next dispatch time period
//                        }
                    }

                    log.debug("DISPATCHING RESULT.. From # Observations: " + this.observations.size());
                    startTime = Calendar.getInstance().getTimeInMillis();

                    dispatchResult(Xk);

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

//        double dfdx = -(x-Xk1)/R1 - (-x2+Xk1)/R2;
//        double dfdy = -(y-Xk2)/R1 - (-y2+Xk2)/R2;
        double dfdx = -(x+Xk1)/R1 - (-x2+Xk1)/R2;
        double dfdy = -(y+Xk2)/R1 - (-y2+Xk2)/R2;

        double[][] jacobianData = {{0, 0, dfdx, dfdy}};
        RealMatrix H = new Array2DRowRealMatrix(jacobianData);
        return H;

        // MATLAB version
//        r(i,:) = f_tdoa_meas(i,:) - f_est(i,:);
//
//        R1 = sqrt((x(1)-Xk(1))^2 + (y(1)-Xk(2))^2);
//        R2 = sqrt((x(i+1)-Xk(1))^2 + (y(i+1)-Xk(2))^2);
//
//        dfdx(i) = -(x(1)+Xk(1))/R1 - (-x(i+1)+Xk(1))/R2;
//        dfdy(i) = -(y(1)+Xk(2))/R1 - (-y(i+1)+Xk(2))/R2;
    }

    public RealMatrix recalculateH_AOA(double x, double y, double Xk1, double Xk2) {

        double R1 = Math.sqrt(Math.pow((x-Xk1),2) + Math.pow((y-Xk2),2));   /// NOTE: using like this the filter converges on AOA much faster
        //double R1 = (Math.pow((x-Xk1),2) + Math.pow((y-Xk2),2));   // NOTE: Can speed up the rate of convergence towards measurement by multiplying by i.e. 0.1 (10 times speed increase)


        double dfdx = (y-Xk2)/R1;  // Note d/d"x" = "y - y_est"/..... on purpose linearisation
        double dfdy = -(x-Xk1)/R1;

        double[][] jacobianData = {{0, 0, dfdx, dfdy}};
        RealMatrix H = new Array2DRowRealMatrix(jacobianData);
        return H;
    }

    public double[] findRudimentaryStartPoint(Collection<Observation> observations, double addition) {
        double x_init=0; double y_init=0;
        Iterator it = observations.iterator();
        if (observations.size()==1) {
            Observation obs = (Observation)it.next();
            x_init = obs.getX() + addition;
            y_init = obs.getY() - addition;
        }
        else if (observations.size()>1) {
            Observation obs_a = (Observation)it.next();
            Observation obs_b = (Observation)it.next();
            x_init = obs_a.getX();
            y_init = obs_a.getY();
            double x_n = obs_b.getX();
            double y_n = obs_b.getY();
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

        if (this.geoMission.getOutputKml()) {
            KmlFileHelpers.exportGeoMissionToKml(this.geoMission);
        }
    }

    public void resetCovariances(){
        log.debug("Resetting covariances, from: "+Pk);
        Pk = Pinit.scalarMultiply(1000.0);
        log.debug("Resetting covariances, to: "+Pk);


        // TODO< reset Xk3/Xk4???

//                    double[] start_x_y = findRudimentaryStartPoint(this.observations.values(), -500);
//                    log.debug(")Re) Filter start point: "+start_x_y[0]+","+start_x_y[1]);
//                    double[] initStateData = {start_x_y[0], start_x_y[1], 1, 1};
//                    log.info("(Re) Init State Data Easting/Northing: "+initStateData[0]+","+initStateData[1]+",1,1");
//                    double[] latLonStart = Helpers.convertUtmNthingEastingToLatLng(initStateData[0], initStateData[1], geoMission.getLatZone(), geoMission.getLonZone());
//                    log.info("(re) Init start point: "+latLonStart[0]+","+latLonStart[1]);
//                    RealVector Xinit = new ArrayRealVector(initStateData);
//                    Xk = Xinit;
    }
}
