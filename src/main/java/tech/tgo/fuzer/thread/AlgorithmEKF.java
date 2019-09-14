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
import tech.tgo.fuzer.util.CoordHelpers;
import tech.tgo.fuzer.util.FilesystemHelpers;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
//    public double[] x_rssi;// = {30, 40, 80};  /// Note these are dynamically adjusted by TCPGeoServer as it gets new data.
//    public double[] y_rssi;// = {40, 50, 80};
//    public double[] r;// = {{22.36, 10}};  //ssuming a target loc of 50,50
    //List<Observation> observations;
    Map<String,Observation> observations = new HashMap<String,Observation>();

    private final AtomicBoolean running = new AtomicBoolean(true);

    public double[] Xtrue = {80,20};  /// used for testing only

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
                H = recalculateH(obs.getX(),obs.getY(), xk, yk);   // ORIG-H = recalculateH(x_rssi[i],y_rssi[i], xk, yk);

                double f_est=0.0;
                double d=0.0;

                if (obs.getObservationType().equals(ObservationType.range)) {
                    //double f_meas = Math.sqrt(Math.pow((x_rssi[i]-Xtrue[0]),2) + Math.pow(y_rssi[i]-Xtrue[1],2));  used for debigging only
                    f_est = Math.sqrt(Math.pow((obs.getX() - xk), 2) + Math.pow(obs.getY() - yk, 2));  // ORIG:::  double f_est = Math.sqrt(Math.pow((x_rssi[i]-xk),2) + Math.pow(y_rssi[i]-yk,2));

                    //double[] rk = {f_meas - f_est};

                    //// TODO, if this is the first time r has had a value other than 500, then reset Pk .... actually, do'nt need to , it will take a bit longer to converge but that's cool

                    d = obs.getRange();
                    /// ORIGINAL -  TWO OPTIONS DEPENDING ON WHETHER PASSING [dBm] or [m]
                    ////double d = Math.pow(10,((25-r[i] - 20*Math.log10(2.4*Math.pow(10, 9)) + 147.55)/20));    /// Note 25 = 20dBm transmitter + 5 dB gain on the receive antenna  [dBm]
                    //double d = r[i];  // [m]

                    //System.out.println("range, from RSSI="+d+" [m]");
                }
                else if (obs.getObservationType().equals(ObservationType.tdoa)) {

                    // TODO, innovations here
                }
                else if (obs.getObservationType().equals(ObservationType.aoa)) {

                    // TODO, innovations here
                }

                //double rk = d/1000 - f_est;
                double rk = d - f_est;

                //System.out.println("f_meas="+(f_meas));
                //System.out.println("f_est="+(f_est));
                //System.out.println("rk="+(f_meas - f_est));

                RealMatrix toInvert = (H.multiply(Pk).multiply(H.transpose()).add(Rk));
                RealMatrix Inverse = (new LUDecomposition(toInvert)).getSolver().getInverse();
                K = Pk.multiply(H.transpose()).multiply(Inverse);

                //RealVector Kk = K.getColumnVector(0);

                //System.out.println("length K2:"+Kk.length);
                double[] HXk = H.operate(Xk).toArray();
                //K.operate(rk - HXk[0]);
                innov = K.scalarMultiply(rk - HXk[0]).getColumnVector(0).add(innov);

                ////%P_innov = P_innov + H(i,:)'*inv(Rk(i))*H(i,:);
                P_innov = K.multiply(H).multiply(Pk).add(P_innov);
            }

            Xk = Xk.add(innov);
            Pk = (eye.multiply(Pk)).subtract(P_innov);

            loopCounter++;


            //TEMP
            //   TODO, control the filter speed in configs - execute timer task on repeating schedule
            if (loopCounter<10) {
                dispatchResult(Xk);
                //fuzerListener.result(geoMission.getGeoId(), geoMission.getTarget(),Xk.getEntry(0),Xk.getEntry(1), Xk.getEntry(2), Xk.getEntry(3));
            }

            if (geoMission.getFuzerMode().equals(FuzerMode.fix)) {
                // TODO, if it has adequately converged, break;
            }
            if (loopCounter==2500)
            {
                dispatchResult(Xk);
            }
            if (loopCounter==20000)
            {
                dispatchResult(Xk);
                loopCounter=0;

                if (geoMission.getFuzerMode().equals(FuzerMode.fix)) {
                    System.out.println("This is a FIX mode run, exiting since we've had 5000 iterations already");
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
        //System.out.println("H recalced, dfdx=:"+ H.getEntry(0, 3));
    }

    public void stopThread() {
        this.running.set(false);
    }

    public void dispatchResult(RealVector Xk) {

        //this.geoMission.getTarget().setCurrent_loc(new double[]{Xk.getEntry(0),Xk.getEntry(1)});
        double[] latLon = CoordHelpers.convertUtmNthingEastingToLatLng(Xk.getEntry(0),Xk.getEntry(1), this.geoMission.getLatZone(), this.geoMission.getLonZone());
        this.geoMission.getTarget().setCurrent_loc(latLon);

        this.fuzerListener.result(geoMission.getGeoId(),Xk.getEntry(0),Xk.getEntry(1), Xk.getEntry(2), Xk.getEntry(3));

        // TODO, CEP / VARIANCE
        //varianceCounter++;
        //        variance = (variance*(varianceCounter-1) + (Math.pow(Xk3,2) + Math.pow(Xk4,2))/2)/varianceCounter;
        //        double cep = Math.sqrt(variance);
        //double cep = (Math.abs(Xk3)+Math.abs(Xk4))/2;
        //cep = 20;

        //        if (geoMissions.get(geoID).showCEPs)
        //        {
        //            cepCircle = new ArrayList<double[]>();//removeAll(cepCircle);
        //
        //            //double[] cepPoint;
        //            for (double theta = (1/2)*Math.PI; theta <= (5/2)*Math.PI; theta+= 0.2)
        //            {
        //                UTMRef utmCEP = new UTMRef(cep*Math.cos(theta) + Xk1, cep*Math.sin(theta) + Xk2, latZone, lngZone);
        //                LatLng ltln2 = utmCEP.toLatLng();
        //                double[] cepPoint = {ltln2.getLat(),ltln2.getLng()};
        //                cepCircle.add(cepPoint);
        //            }
        //        }
        //else
        //    cepCircle.removeAll(cepCircle);
        //////cep = Math.sqrt(variance);


        if (this.geoMission.isOutputKml()) {
            // Export or overwrite the kml file
            FilesystemHelpers.exportGeoMissionToKml(this.geoMission);
        }
        if (this.geoMission.isOutputJson()) {
            // Check and use the json output file
        }

    }

}
