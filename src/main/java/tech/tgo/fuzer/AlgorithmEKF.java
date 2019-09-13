/*
 * Geolocation fusion and tracking, using custom extended kalman filter implementation
 *
 * @author Timothy Edge (timmyedge)
 */
package tech.tgo.fuzer;

import org.apache.commons.math3.filter.MeasurementModel;
import org.apache.commons.math3.filter.ProcessModel;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.LUDecomposition;
import tech.tgo.fuzer.model.FuzerConfig;
import tech.tgo.fuzer.model.FuzerMode;
import tech.tgo.fuzer.model.Observation;
import tech.tgo.fuzer.model.ObservationType;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

import java.util.Iterator;
import java.util.List;

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

    private FuzerConfig fuzerConfig;


//    /* x_ and y_ are the assets positions */
//    public double[] x_rssi;// = {30, 40, 80};  /// Note these are dynamically adjusted by TCPGeoServer as it gets new data.
//    public double[] y_rssi;// = {40, 50, 80};
//    public double[] r;// = {{22.36, 10}};  //ssuming a target loc of 50,50
    List<Observation> observations;

    public double[] Xtrue = {80,20};  /// used for testing only

    double[][] ThiData = { {1,0,1, 0}, {0,1,0,1}, {0,0,0,0}, {0,0,0,0}};
    RealMatrix Thi = new Array2DRowRealMatrix(ThiData);
    
    double[][] controlData = { {0}, {0}, {0}, {0}};
    RealMatrix B = new Array2DRowRealMatrix(controlData);
    
    double[][] procNoiseData = { {0.01, 0, 0, 0}, {0, 0.01, 0 ,0}, {0, 0, 0.01, 0}, {0, 0, 0 ,0.01}};
    RealMatrix Qu = new Array2DRowRealMatrix(procNoiseData);
    
//    double[] initStateData = {60, 30, 0.1, 0.1};
//    RealVector Xinit = new ArrayRealVector(initStateData);
    //double[] initStateData = {60, 30, 1, 1};
    //RealVector Xinit = new ArrayRealVector(initStateData);    /// This is now set below in run()
    /// -31.891551,115.996399 - Perth Area
    
    double[][] initCovarData = {{1, 0, 0, 0}, {0, 1, 0 ,0}, {0, 0, 1, 0}, {0, 0, 0 ,1}};
    RealMatrix Pinit = new Array2DRowRealMatrix(initCovarData);
    
    
    double[][] measurementNoiseData = {{5}};
    RealMatrix Rk = new Array2DRowRealMatrix(measurementNoiseData);
    //double Rk = 0.1;

    /*
     * Create an algorithm tracker process for the given assets
     */
    public AlgorithmEKF(FuzerListener fuzerListener, List<Observation> observations, FuzerConfig fuzerConfig)
    {
        this.fuzerListener = fuzerListener;
        this.observations = observations;
        this.fuzerConfig = fuzerConfig;
    }
    
    public void run()
    {
        System.out.println("Running for # observations:"+observations.size());
        //System.out.println("r[1]:"+r[0][1]);
        
        LatLng init_ltln = new LatLng(-31.891551,115.996399); /// Perth Area
        UTMRef utm = init_ltln.toUTMRef();
        
        double[] initStateData = {utm.getEasting(), utm.getNorthing(), 1, 1};
        RealVector Xinit = new ArrayRealVector(initStateData);
        //geoProcess process = new geoProcess();// = new ProcessModel();    
        //geoMeasurement meas = new geoMeasurement();
        
        //KalmanFilter kf = new KalmanFilter(process, meas);

        RealVector Xk = Xinit;
        RealMatrix Pk = Pinit.scalarMultiply(1000.0);
        
        double[] innovd = {0,0,0,0};
        RealVector innov = new ArrayRealVector(innovd); 
        //double P_innovd = 0;
        double[][] P_innovd = {{0,0,0,0}, {0,0,0,0}, {0,0,0,0}, {0,0,0,0}};
        RealMatrix P_innov = new Array2DRowRealMatrix(P_innovd);
        
        double[][] eyeData = {{1,0,0,0}, {0,1,0,0}, {0,0,1,0}, {0,0,0,1}};
        RealMatrix eye = new Array2DRowRealMatrix(eyeData);
        
        //double[] rr;
        
        RealMatrix H;
        double xk;
        double yk;
        RealMatrix K;


        int loopCounter = 0;
        while(true)
        {   
            //kf.predict();
            
            Xk = Thi.operate(Xk);// + B*uu);
            
            //System.out.println("Xk1="+Xk.toArray()[0]+" Xk2="+Xk.toArray()[1]);
            //Xk = 
            Pk = (Thi.multiply(Pk).multiply(Thi.transpose())).add(Qu);
            //System.out.println("Pk33="+Pk.getData()[2][2]+" Pk44="+Pk.getData()[3][3]);
            
            innov = new ArrayRealVector(innovd); 
            P_innov = new Array2DRowRealMatrix(P_innovd);
           
            //System.out.println("#Devices"+x_rssi.length);
            
            Iterator obsIterator = observations.iterator();
            while (obsIterator.hasNext()) { //x_rssi.length
                Observation obs = (Observation) obsIterator.next();

                    //System.out.println("for"+i+",X_RSSI:"+x_rssi[i]);
                    //System.out.println("X_rssi="+x_rssi[i]+" , y_rssi="+y_rssi[i]);
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
                    K = Pk.multiply(H.transpose()).multiply(Inverse);//.get;

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
            

            if (fuzerConfig.getFuzerMode().equals(FuzerMode.fix)) {
                // TODO, if it has adequately converged, break;
            }
            if (loopCounter==2500)
            {
                fuzerListener.result(fuzerConfig.getGeoId(),fuzerConfig.getTarget(),Xk.getEntry(0),Xk.getEntry(1), Xk.getEntry(2), Xk.getEntry(3));
            }
            if (loopCounter==5000)
            {
                //System.out.println("Xk1="+Xk.getEntry(0)+" , Xk2="+Xk.getEntry(1));
                //m_app.giveGeoResult(geoID,Xk.getEntry(0),Xk.getEntry(1), Xk.getEntry(2), Xk.getEntry(3));   ORIGINAL
                fuzerListener.result(fuzerConfig.getGeoId(),fuzerConfig.getTarget(),Xk.getEntry(0),Xk.getEntry(1), Xk.getEntry(2), Xk.getEntry(3));
                //System.out.println("xk="+kf.getStateEstimation()[0]+" , yk="+kf.getStateEstimation()[1]);
                loopCounter=0;

                if (fuzerConfig.getFuzerMode().equals(FuzerMode.fix)) {
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



    
}

class geoProcess implements ProcessModel {

    //RealMatrix Thi = new RealMatrix() ;//= {[1 0 0 0; 0 1 0 0; 0 0 0 0; 0 0 0 0]};
    double[][] matrixData = { {1,0,1, 0}, {0,1,0,1}, {0,0,0,0}, {0,0,0,0}};
    RealMatrix Thi = new Array2DRowRealMatrix(matrixData);
    
    double[][] controlData = { {0}, {0}, {0}, {0}};
    RealMatrix B = new Array2DRowRealMatrix(controlData);
    
    double[][] procNoiseData = { {0.01, 0, 0, 0}, {0, 0.01, 0 ,0}, {0, 0, 0.01, 0}, {0, 0, 0 ,0.01}};
    RealMatrix Qu = new Array2DRowRealMatrix(procNoiseData);
    
    double[] initStateData = {60, 30, 0.1, 0.1};
    RealVector Xinit = new ArrayRealVector(initStateData);
    
    double[][] initCovarData = {{1, 0, 0, 0}, {0, 1, 0 ,0}, {0, 0, 1, 0}, {0, 0, 0 ,1}};
    RealMatrix Pinit = new Array2DRowRealMatrix(initCovarData);
    
    @Override
    public RealMatrix getStateTransitionMatrix() {
        return Thi;
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public RealMatrix getControlMatrix() {
        return B;
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public RealMatrix getProcessNoise() {
        return Qu;
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public RealVector getInitialStateEstimate() {
        return Xinit;
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public RealMatrix getInitialErrorCovariance() {
        return Pinit;
        //throw new UnsupportedOperationException("Not supported yet.");
    }
    
    
}

class geoMeasurement implements MeasurementModel
{
    //double[][] matrixData;
    //        double R1 = Math.sqrt(Math.pow((x_rssi-Xk1),2) + Math.pow(y_rssi-Xk2,2));
    //
    //        double dfdx = -(x_rssi-Xk1)/R1;
    //        double dfdy = -(y_rssi-Xk2)/R1;
    //
    //        double[][] jacobianData = {{0, 0, dfdx, dfdy}};
    double[][] initH = {{0, 0, 0, 0}};
    
    RealMatrix H = new Array2DRowRealMatrix(initH);
    
    double[][] measurementNoiseData = {{0.3}};
    RealMatrix R = new Array2DRowRealMatrix(measurementNoiseData);
    
    public void recalculateH(double x_rssi, double y_rssi, double Xk1, double Xk2)
    {
        double R1 = Math.sqrt(Math.pow((x_rssi-Xk1),2) + Math.pow(y_rssi-Xk2,2));
        
        double dfdx = -(x_rssi-Xk1)/R1;
        double dfdy = -(y_rssi-Xk2)/R1;
        
        double[][] jacobianData = {{0, 0, dfdx, dfdy}};
        H = new Array2DRowRealMatrix(jacobianData);
        //System.out.println("H recalced, dfdx=:"+ H.getEntry(0, 3));
    }
    
    @Override
    public RealMatrix getMeasurementMatrix() {
        return H;
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public RealMatrix getMeasurementNoise() {
        return R;
        //throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
