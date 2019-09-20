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
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class AlgorithmEKF implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmEKF.class);

    private FuzerListener fuzerListener;

    private GeoMission geoMission;

//    private Long ekf_filter_throttle = null;
//
//    private Double ekf_filter_convergence_threshold = null;

    Map<Long,Observation> observations = new ConcurrentHashMap<Long,Observation>();

    private final AtomicBoolean running = new AtomicBoolean(false);

    double[][] ThiData = { {1,0,1, 0}, {0,1,0,1}, {0,0,0,0}, {0,0,0,0}};
    RealMatrix Thi = new Array2DRowRealMatrix(ThiData);

    double[][] controlData = { {0}, {0}, {0}, {0}};
    RealMatrix B = new Array2DRowRealMatrix(controlData);

    double[][] procNoiseData = { {0.01, 0, 0, 0}, {0, 0.01, 0 ,0}, {0, 0, 0.01, 0}, {0, 0, 0 ,0.01}};
    RealMatrix Qu = new Array2DRowRealMatrix(procNoiseData);

    double[][] initCovarData = {{1, 0, 0, 0}, {0, 1, 0 ,0}, {0, 0, 1, 0}, {0, 0, 0 ,1}};
    RealMatrix Pinit = new Array2DRowRealMatrix(initCovarData);

    double[][] measurementNoiseData = {{5}};
    RealMatrix Rk = new Array2DRowRealMatrix(measurementNoiseData);


    /// NEW FROM HERE
    RealVector Xk;
    RealMatrix Pk;

    RealVector innov;
    RealMatrix P_innov;

    RealMatrix eye;

    RealMatrix H;
    double xk;
    double yk;
    RealMatrix K;

    /*
     * Create an algorithm tracker process for the given config, observations and client implemented listener
     */
    public AlgorithmEKF(FuzerListener fuzerListener, Map<Long,Observation> observations, GeoMission geoMission)
    {
        this.fuzerListener = fuzerListener;
        this.observations = observations;
        this.geoMission = geoMission;
    }

    public void setObservations(Map<Long, Observation> observations) {
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

        //TODO, pick a start point that is as orthogonal to all sensor positions as possible
        // alt start point : -31.86609796014695, Lon: 115.9948057818586
        //LatLng init_ltln = new LatLng(-31.86609796014695,115.9948057818586); /// Perth Area
        //LatLng init_ltln = new LatLng(-31.86653552023262,116.114399401754);
        //LatLng init_ltln = new LatLng(-31.891551,115.996399); /// Perth Area
        //UTMRef utm = init_ltln.toUTMRef();
        //double[] initStateData = {utm.getEasting(), utm.getNorthing(), 1, 1};


        /* Initialise filter state */
        log.debug("Finding rudimentary start point between first two observations");
        double[] start_x_y = findRudimentaryStartPoint(this.observations.values());
        double[] initStateData = {start_x_y[0], start_x_y[1], 1, 1};

        log.info("Init State Data Easting/Northing: "+initStateData[0]+","+initStateData[1]+",1,1");

        RealVector Xinit = new ArrayRealVector(initStateData);

        RealVector Xk = Xinit;
        RealMatrix Pk = Pinit.scalarMultiply(1000.0);

        double[] innovd = {0,0,0,0};
        RealVector innov = new ArrayRealVector(innovd);
        double[][] P_innovd = {{0,0,0,0}, {0,0,0,0}, {0,0,0,0}, {0,0,0,0}};
        RealMatrix P_innov = new Array2DRowRealMatrix(P_innovd);

        double[][] eyeData = {{1,0,0,0}, {0,1,0,0}, {0,0,1,0}, {0,0,0,1}};
        RealMatrix eye = new Array2DRowRealMatrix(eyeData);

        RealMatrix H;
        double xk;
        double yk;
        RealMatrix K;

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

            innov = new ArrayRealVector(innovd);   //redundant - NO, this is aboslutely required here otherwise filter bounces all over the place
            P_innov = new Array2DRowRealMatrix(P_innovd);

            // NOTE: observations is dynamically updated for tracking mode missions
            Iterator obsIterator = this.observations.values().iterator();
            while (obsIterator.hasNext()) {

                Observation obs = (Observation) obsIterator.next();

                xk = Xk.getEntry(0);
                yk = Xk.getEntry(1);

                double f_est=0.0;
                double d=0.0;
                H = null;

                if (obs.getObservationType().equals(ObservationType.range)) {

                    H = recalculateH(obs.getX(),obs.getY(), xk, yk);

                    f_est = Math.sqrt(Math.pow((obs.getX() - xk), 2) + Math.pow(obs.getY() - yk, 2));

                    d = obs.getRange();

                    log.trace("RANGE innovation: "+f_est+", vs d: "+d);

                }
                else if (obs.getObservationType().equals(ObservationType.tdoa)) {

                    H = recalculateH_TDOA(obs.getX(),obs.getY(), obs.getXb(), obs.getYb(), xk, yk);

                    f_est = Math.sqrt(Math.pow((obs.getX() - xk), 2) + Math.pow(obs.getY() - yk, 2)) - Math.sqrt(Math.pow((obs.getXb() - xk), 2) + Math.pow(obs.getYb() - yk, 2));

                    d = obs.getTdoa()*Helpers.SPEED_OF_LIGHT;

                    log.trace("TDOA innovation: "+f_est+", vs d: "+d);
                }
                else if (obs.getObservationType().equals(ObservationType.aoa)) {

                    H = recalculateH_AOA(obs.getX(), obs.getY(), xk, yk);

                    f_est = Math.atan((obs.getY() - yk)/(obs.getX() - xk))*180/Math.PI;

                    if (xk<obs.getX()) {
                        f_est = f_est + 180;
                    }
                    if (yk<obs.getY() && xk>=obs.getX()) {
                        f_est = (180 - Math.abs(f_est)) + 180;
                    }

                    d = obs.getAoa()*180/Math.PI;

                    log.trace("AOA innovation: "+f_est+", vs d: "+d);
                }

                double rk = d - f_est;

                RealMatrix toInvert = (H.multiply(Pk).multiply(H.transpose()).add(Rk));
                RealMatrix Inverse = (new LUDecomposition(toInvert)).getSolver().getInverse();
                K = Pk.multiply(H.transpose()).multiply(Inverse);

                double[] HXk = H.operate(Xk).toArray();
                innov = K.scalarMultiply(rk - HXk[0]).getColumnVector(0).add(innov);
                log.trace("Innov: "+innov);

                P_innov = K.multiply(H).multiply(Pk).add(P_innov);
            }

            Xk = Xk.add(innov);
            Pk = (eye.multiply(Pk)).subtract(P_innov);

            if ((Calendar.getInstance().getTimeInMillis() - startTime) > this.geoMission.getDispatchResultsPeriod()) {
                log.debug("DISPATCHING RESULT.. From # Observations: "+this.observations.size());
                startTime = Calendar.getInstance().getTimeInMillis();

                dispatchResult(Xk);

                if (geoMission.getFuzerMode().equals(FuzerMode.fix)) {
                    double residual = Math.abs(Xk.getEntry(2) + Xk.getEntry(3));
                    if (residual < this.geoMission.getFilterConvergenceResidualThreshold()) {
                        log.debug("Exiting since this is a FIX Mode run and filter has converged to threshold");
                        running.set(false);
                        break;
                    }
                }
                else {
                    log.debug("This is a Tracking mode run, continuing...");
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

        double dfdx = -(x-Xk1)/R1 - (-x2+Xk1)/R2;
        double dfdy = -(y-Xk2)/R1 - (-y2+Xk2)/R2;

        double[][] jacobianData = {{0, 0, dfdx, dfdy}};
        RealMatrix H = new Array2DRowRealMatrix(jacobianData);
        return H;
    }

    public RealMatrix recalculateH_AOA(double x, double y, double Xk1, double Xk2) {

        double R1 = Math.sqrt(Math.pow((x-Xk1),2) + Math.pow((y-Xk2),2));

        double dfdx = (y-Xk2)/R1;  // Note d/d"x" = "y - y_est"/..... on purpose linearisation
        double dfdy = -(x-Xk1)/R1;

        double[][] jacobianData = {{0, 0, dfdx, dfdy}};
        RealMatrix H = new Array2DRowRealMatrix(jacobianData);
        return H;
    }

    public double[] findRudimentaryStartPoint(Collection<Observation> observations) {
        double x_init=0; double y_init=0;
        Iterator it = observations.iterator();
        if (observations.size()==1) {
            Observation obs = (Observation)it.next();
            x_init = obs.getX() + 500;
            y_init = obs.getY() + 500;
        }
        else if (observations.size()>1) {
            Observation obs = (Observation)it.next();
            for (int i = 0; i < observations.size(); i++) {
                if (i == 0) {
                    x_init = obs.getX();
                    y_init = obs.getY();
                } else if (i == 2) {
                    double x_n = obs.getX();
                    double y_n = obs.getX();
                    x_init = x_init + (x_init - x_n) / 2;
                    y_init = y_init + (y_init - y_n) / 2;
                }
            }
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

        if (this.geoMission.isOutputKml()) {
            KmlFileHelpers.exportGeoMissionToKml(this.geoMission);
        }
    }
}
