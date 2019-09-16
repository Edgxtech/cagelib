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
import tech.tgo.fuzer.FuzerListener;
import tech.tgo.fuzer.model.GeoMission;
import tech.tgo.fuzer.model.FuzerMode;
import tech.tgo.fuzer.model.Observation;
import tech.tgo.fuzer.model.ObservationType;
import tech.tgo.fuzer.util.Helpers;
import tech.tgo.fuzer.util.FilesystemHelpers;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class AlgorithmEKF  extends Thread {

    // Has a number of registered devices. each device has one r measurement
    //     The algorithm runs always, but the r measurement is simply adjusted for each device
    // Need to somehow split this into a control ele which allows assets to be registered (in-memory), and measurements to be passed
    // Then need to adjust the compute component (i.e. this component), to only re attempt convergence when substantial new information is provided - IN TRACKING MODE
    // Also need a geo Mode, which stops after convergence.
    // Also need target management, or allow using clients to conduct this

    // Run a thread which loops fast on new data update, then gradually slows down???

    // Need to think through, where is the actual interface point for the network responsible for fetching the data.

    private FuzerListener fuzerListener;

    private GeoMission geoMission;

    //    /* x_ and y_ are the assets positions */

    Map<String,Observation> observations = new HashMap<String,Observation>();

    private final AtomicBoolean running = new AtomicBoolean(true);

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

    /*
     * Create an algorithm tracker process for the given config, observations and client implemented listener
     */
    public AlgorithmEKF(FuzerListener fuzerListener, Map<String,Observation> observations, GeoMission geoMission)
    {
        this.fuzerListener = fuzerListener;
        this.observations = observations;
        this.geoMission = geoMission;
    }

    public void run()
    {
        System.out.println("Running for # observations:"+observations.size());

        LatLng init_ltln = new LatLng(-31.891551,115.996399); /// Perth Area
        UTMRef utm = init_ltln.toUTMRef();

        double[] initStateData = {utm.getEasting(), utm.getNorthing(), 1, 1};
        System.out.println("Init State Data Easting/Northing: "+initStateData[0]+","+initStateData[1]+",1,1");

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

        int loopCounter = 0;
        while(true)
        {
            if (!running.get()) {
                System.out.println("Thread was stopped");
                break;
            }

            Xk = Thi.operate(Xk);// + B*uu);

            //System.out.println("Xk1="+Xk.toArray()[0]+" Xk2="+Xk.toArray()[1]);
            Pk = (Thi.multiply(Pk).multiply(Thi.transpose())).add(Qu);
            //System.out.println("Pk33="+Pk.getData()[2][2]+" Pk44="+Pk.getData()[3][3]);

            innov = new ArrayRealVector(innovd);
            P_innov = new Array2DRowRealMatrix(P_innovd);

            Iterator obsIterator = observations.values().iterator();
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
                    // Optionally, if passing dBm, basic model to use as follows to convert [dBm] to [m]
                    // double d = Math.pow(10,((25-r[i] - 20*Math.log10(2.4*Math.pow(10, 9)) + 147.55)/20));    /// Note 25 = 20dBm transmitter + 5 dB gain on the receive antenna  [dBm]

                    // TODO, Normalise the innovation, so it doesnt overpower the AOA or other measurement types
                    //f_est = f_est / obs.getRange();

                    //System.out.println("RANGE innovatin: "+f_est+", vs d: "+d);

                }
                else if (obs.getObservationType().equals(ObservationType.tdoa)) {

                    H = recalculateH_TDOA(obs.getX(),obs.getY(), obs.getXb(), obs.getYb(), xk, yk);

                    f_est = Math.sqrt(Math.pow((obs.getX() - xk), 2) + Math.pow(obs.getY() - yk, 2)) - Math.sqrt(Math.pow((obs.getXb() - xk), 2) + Math.pow(obs.getYb() - yk, 2));

                    d = obs.getTdoa()*Helpers.SPEED_OF_LIGHT;

                    //System.out.println("TDOA innovation: "+f_est+", vs d: "+d);
                }
                else if (obs.getObservationType().equals(ObservationType.aoa)) {

                    H = recalculateH_AOA(obs.getX(), obs.getY(), xk, yk);

                    f_est = Math.atan((obs.getY() - yk)/(obs.getX() - xk))*180/Math.PI;
                    if (xk<obs.getX()) {
                        f_est = f_est + 180;
                    }

                    d = obs.getAoa()*180/Math.PI;
                }

                double rk = d - f_est;
                //System.out.println("Rk: "+rk);

                RealMatrix toInvert = (H.multiply(Pk).multiply(H.transpose()).add(Rk));
                RealMatrix Inverse = (new LUDecomposition(toInvert)).getSolver().getInverse();
                K = Pk.multiply(H.transpose()).multiply(Inverse);

                double[] HXk = H.operate(Xk).toArray();
                innov = K.scalarMultiply(rk - HXk[0]).getColumnVector(0).add(innov);
                //System.out.println("Innov: "+innov);

                P_innov = K.multiply(H).multiply(Pk).add(P_innov);
            }

            Xk = Xk.add(innov);
            Pk = (eye.multiply(Pk)).subtract(P_innov);

            loopCounter++;


            //TEMP
            //   TODO, control the filter speed in configs - execute timer task on repeating schedule
            if (loopCounter==1 || loopCounter==10 || loopCounter == 100) {
                dispatchResult(Xk);
                //break;
            }

            if (geoMission.getFuzerMode().equals(FuzerMode.fix)) {
                // TODO, if it has adequately converged, break;
            }
            if (loopCounter==200)
            {
                dispatchResult(Xk);
            }
            if (loopCounter==10000)
            {
                dispatchResult(Xk);
            }
            if (loopCounter==100000)
            {
                dispatchResult(Xk);
                loopCounter=0;

                if (geoMission.getFuzerMode().equals(FuzerMode.fix)) {
                    System.out.println("This is a FIX mode run, exiting since we've had MAX iterations already");
                    break;
                }
            }
        }
    }

    public RealMatrix recalculateH(double x_rssi, double y_rssi, double Xk1, double Xk2)
    {
        double R1 = Math.sqrt(Math.pow((x_rssi-Xk1),2) + Math.pow(y_rssi-Xk2,2));

        double dfdx = -(x_rssi-Xk1)/R1;
        double dfdy = -(y_rssi-Xk2)/R1;

        double[][] jacobianData = {{0, 0, dfdx, dfdy}};
        RealMatrix H = new Array2DRowRealMatrix(jacobianData);
        return H;
    }

    public RealMatrix recalculateH_TDOA(double x, double y, double x2, double y2, double Xk1, double Xk2)
    {
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

        double dfdx = (y-Xk2)/R1;  // Note d/d"x" = "y - y_est"/..... on purpose
        double dfdy = -(x-Xk1)/R1;

        double[][] jacobianData = {{0, 0, dfdx, dfdy}};
        RealMatrix H = new Array2DRowRealMatrix(jacobianData);
        return H;
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
            FilesystemHelpers.exportGeoMissionToKml(this.geoMission);
        }
    }

}
