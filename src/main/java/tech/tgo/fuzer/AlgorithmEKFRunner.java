/*
 * Geolocation fusion and tracking, using custom extended kalman filter implementation
 *
 * @author Timothy Edge (timmyedge)
 */
package tech.tgo.fuzer;

import org.apache.commons.math3.linear.*;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

public class AlgorithmEKFRunner  extends Thread {

    public String geoID;

    public String target;

    public double[] x_rssi;// = {30, 40, 80};  /// Note these are dynamically adjusted by TCPGeoServer as it gets new data.
    public double[] y_rssi;// = {40, 50, 80};
    public double[] r;// = {{22.36, 10}};  //ssuming a target loc of 50,50

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
    
    public boolean firstIteration = true;
    public Object[] selectedDevices;
    
    public AlgorithmEKFRunner(Object[] selected)
    {
        selectedDevices = selected;
        int numDevices = selected.length;
        x_rssi = new double[numDevices];
        y_rssi = new double[numDevices];
        r = new double[numDevices];

        for (int i=0;i<selected.length;i++)
        {
            x_rssi[i] = 0;
            y_rssi[i] = 0;
            r[i] = 500;   // i.e. a huge rssi value to intialise, if the filter runs using this, it will give back the current position of the node - this is good initialisation behaviour!
        }
    }

//public AlgorithmEKF(IGuiAppStatic app, Object[] selected)
//{
//    this(selected);
//    m_app = app;
//}
    
    public void run()
    {
        System.out.println("x_rssi length:"+x_rssi.length);
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
            
            
            for (int i=0;i<x_rssi.length;i++)
            {
                if (x_rssi[i]!=0)                  //// There is a bit of a bug here in that, if my gps hasn't registered, it's innovations don't register at all. Need to ensure, if GPS no sending data, ask the user to click a point on the map where you are located. This then provides a start position
                {
                    //System.out.println("for"+i+",X_RSSI:"+x_rssi[i]);
                    //System.out.println("X_rssi="+x_rssi[i]+" , y_rssi="+y_rssi[i]);
                    xk = Xk.getEntry(0);
                    yk = Xk.getEntry(1);
                    H = recalculateH(x_rssi[i],y_rssi[i], xk, yk);

                    //double f_meas = Math.sqrt(Math.pow((x_rssi[i]-Xtrue[0]),2) + Math.pow(y_rssi[i]-Xtrue[1],2));  used for debigging only
                    double f_est = Math.sqrt(Math.pow((x_rssi[i]-xk),2) + Math.pow(y_rssi[i]-yk,2));
                    //double[] rk = {f_meas - f_est};
                    
                    //// TODO, if this is the first time r has had a value other than 500, then reset Pk .... actually, do'nt need to , it will take a bit longer to converge but that's cool
                    ////////
                    
                    /// TWO OPTIONS DEPENDING ON WHETHER PASSING [dBm] or [m]
                    //double d = Math.pow(10,((25-r[i] - 20*Math.log10(2.4*Math.pow(10, 9)) + 147.55)/20));    /// Note 25 = 20dBm transmitter + 5 dB gain on the receive antenna
                    double d = r[i];

                    //System.out.println("range, from RSSI="+d+" [m]");
                    
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
    //        
    //                //%P_innov = P_innov + H(i,:)'*inv(Rk(i))*H(i,:);
                    P_innov = K.multiply(H).multiply(Pk).add(P_innov);
                }
                else
                {
                    if(firstIteration)
                    {
                        System.out.println("No GPS data for device #"+selectedDevices[i].toString()+". ignoring it from the filter for now..");
                        firstIteration = false;
                    }
                }
            }
            
            Xk = Xk.add(innov);
            Pk = (eye.multiply(Pk)).subtract(P_innov);
                      
            loopCounter++;
            
                    
            if (loopCounter==5000)
            {

                //System.out.println("Xk1="+Xk.getEntry(0)+" , Xk2="+Xk.getEntry(1));
                //m_app.giveGeoResult(geoID,Xk.getEntry(0),Xk.getEntry(1), Xk.getEntry(2), Xk.getEntry(3));
                //System.out.println("xk="+kf.getStateEstimation()[0]+" , yk="+kf.getStateEstimation()[1]);
                loopCounter=0;
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

