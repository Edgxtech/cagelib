/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package tech.tgo.fuzer;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.LUDecomposition;;


/**
 *
 * @author zenith
 */
public class AlgorithmEKFForDebugging  extends Thread{
    
//public IGuiAppStatic m_app;
    
public double[] x_rssi = {30, 40, 80};  /// Note these are dynamically adjusted by TCPGeoServer as it gets new data.
public double[] y_rssi = {40, 50, 80};
public double[][] r = {{22.36, 10}};  //ssuming a target loc of 50,50

public double[] Xtrue = {80,20};  /// used for testing only 

    double[][] matrixData = { {1,0,1, 0}, {0,1,0,1}, {0,0,0,0}, {0,0,0,0}};
    RealMatrix Thi = new Array2DRowRealMatrix(matrixData);
    
    double[][] controlData = { {0}, {0}, {0}, {0}};
    RealMatrix B = new Array2DRowRealMatrix(controlData);
    
    double[][] procNoiseData = { {0.01, 0, 0, 0}, {0, 0.01, 0 ,0}, {0, 0, 0.01, 0}, {0, 0, 0 ,0.01}};
    RealMatrix Qu = new Array2DRowRealMatrix(procNoiseData);
    
//    double[] initStateData = {60, 30, 0.1, 0.1};
//    RealVector Xinit = new ArrayRealVector(initStateData);
    double[] initStateData = {60, 30, 0.1, 0.1};
    RealVector Xinit = new ArrayRealVector(initStateData);
    
    double[][] initCovarData = {{1, 0, 0, 0}, {0, 1, 0 ,0}, {0, 0, 1, 0}, {0, 0, 0 ,1}};
    RealMatrix Pinit = new Array2DRowRealMatrix(initCovarData);
    
    
    double[][] measurementNoiseData = {{0.3}};
    RealMatrix Rk = new Array2DRowRealMatrix(measurementNoiseData);
    //double Rk = 0.1;
    
public AlgorithmEKFForDebugging(Object[] selected)
{
    //x_rssi = new double[selected.length];
    //y_rssi = new double[selected.length];
    //r = new double[1][selected.length];
}

//public AlgorithmEKFForDebugging(IGuiAppStatic app, Object[] selected)
//{
//    this(selected);
//    m_app = app;
//}
    
    public void run()
    {
        System.out.println("x_rssi length:"+x_rssi.length);
        //System.out.println("r[1]:"+r[0][1]);
        
        geoProcess process = new geoProcess();// = new ProcessModel();    
        geoMeasurement meas = new geoMeasurement();
        
        //KalmanFilter kf = new KalmanFilter(process, meas);

        RealVector Xk = Xinit;
        RealMatrix Pk = Pinit.scalarMultiply(0.02);
        
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
           
            for (int i=0;i<x_rssi.length;i++)
            {
                //double[] Xk = kf.getStateEstimationVector().toArray();
                //System.out.println(Xk[1]);
                xk = Xk.getEntry(0);
                yk = Xk.getEntry(1);
                H = recalculateH(x_rssi[i],y_rssi[i], xk, yk);
                //System.out.println("H3="+H.getData()[0][2]+" H4="+H.getData()[0][3]);
                
                
                //double[] rr = {r[0][i]};
                
                double f_meas = Math.sqrt(Math.pow((x_rssi[i]-Xtrue[0]),2) + Math.pow(y_rssi[i]-Xtrue[1],2));
                double f_est = Math.sqrt(Math.pow((x_rssi[i]-xk),2) + Math.pow(y_rssi[i]-yk,2));
                //double[] rk = {f_meas - f_est};
                double rk = f_meas - f_est;
                //RealVector rr = new ArrayRealVector(rk);
                
                //System.out.println("f_meas="+(f_meas));
                //System.out.println("f_est="+(f_est));
                //System.out.println("rk="+(f_meas - f_est));
                
                //kf.correct(new double[]{r[0][i]});
                //kf.correct(new double[]{f_meas-f_est});
                //double[] Xkp = kf.getStateEstimationVector().toArray();
                
                RealMatrix toInvert = (H.multiply(Pk).multiply(H.transpose()).add(Rk));
                RealMatrix Inverse = (new LUDecomposition(toInvert)).getSolver().getInverse();
                K = Pk.multiply(H.transpose()).multiply(Inverse);//.get;

                //System.out.println("K3="+K.getData()[2][0]+" K4="+K.getData()[3][0]);
                
                //System.out.println("length K:"+K.getRowDimension());
                RealVector Kk = K.getColumnVector(0);
                
                //System.out.println("length K2:"+Kk.length);
                double[] HXk = H.operate(Xk).toArray();
                //K.operate(rk - HXk[0]);
                innov = K.scalarMultiply(rk - HXk[0]).getColumnVector(0).add(innov);
//        
//                //%P_innov = P_innov + H(i,:)'*inv(Rk(i))*H(i,:);
                P_innov = K.multiply(H).multiply(Pk).add(P_innov);
                
            }
            
            Xk = Xk.add(innov);
            Pk = (eye.multiply(Pk)).subtract(P_innov);
            
            
            
            loopCounter++;
                    
            if (loopCounter==100)
            {
                System.out.println("Xk1="+Xk.getEntry(0)+" , Xk2="+Xk.getEntry(1));
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
//class geoProcess implements ProcessModel{
//
//    //RealMatrix Thi = new RealMatrix() ;//= {[1 0 0 0; 0 1 0 0; 0 0 0 0; 0 0 0 0]};
//    double[][] matrixData = { {1,0,1, 0}, {0,1,0,1}, {0,0,0,0}, {0,0,0,0}};
//    RealMatrix Thi = new Array2DRowRealMatrix(matrixData);
//    
//    double[][] controlData = { {0}, {0}, {0}, {0}};
//    RealMatrix B = new Array2DRowRealMatrix(controlData);
//    
//    double[][] procNoiseData = { {0.01, 0, 0, 0}, {0, 0.01, 0 ,0}, {0, 0, 0.01, 0}, {0, 0, 0 ,0.01}};
//    RealMatrix Qu = new Array2DRowRealMatrix(procNoiseData);
//    
//    double[] initStateData = {60, 30, 0.1, 0.1};
//    RealVector Xinit = new ArrayRealVector(initStateData);
//    
//    double[][] initCovarData = {{1, 0, 0, 0}, {0, 1, 0 ,0}, {0, 0, 1, 0}, {0, 0, 0 ,1}};
//    RealMatrix Pinit = new Array2DRowRealMatrix(initCovarData);
//    
//    @Override
//    public RealMatrix getStateTransitionMatrix() {
//        return Thi;
//        //throw new UnsupportedOperationException("Not supported yet.");
//    }
//
//    @Override
//    public RealMatrix getControlMatrix() {
//        return B;
//        //throw new UnsupportedOperationException("Not supported yet.");
//    }
//
//    @Override
//    public RealMatrix getProcessNoise() {
//        return Qu;
//        //throw new UnsupportedOperationException("Not supported yet.");
//    }
//
//    @Override
//    public RealVector getInitialStateEstimate() {
//        return Xinit;
//        //throw new UnsupportedOperationException("Not supported yet.");
//    }
//
//    @Override
//    public RealMatrix getInitialErrorCovariance() {
//        return Pinit;
//        //throw new UnsupportedOperationException("Not supported yet.");
//    }
//    
//    
//}
//
//class geoMeasurement implements MeasurementModel
//{
//    //double[][] matrixData;
////        double R1 = Math.sqrt(Math.pow((x_rssi-Xk1),2) + Math.pow(y_rssi-Xk2,2));
////        
////        double dfdx = -(x_rssi-Xk1)/R1;
////        double dfdy = -(y_rssi-Xk2)/R1;
////        
////        double[][] jacobianData = {{0, 0, dfdx, dfdy}};
//    double[][] initH = {{0, 0, 0, 0}};
//    
//    RealMatrix H = new Array2DRowRealMatrix(initH);
//    
//    double[][] measurementNoiseData = {{0.3}};
//    RealMatrix R = new Array2DRowRealMatrix(measurementNoiseData);
//    
//    public void recalculateH(double x_rssi, double y_rssi, double Xk1, double Xk2)
//    {
//        double R1 = Math.sqrt(Math.pow((x_rssi-Xk1),2) + Math.pow(y_rssi-Xk2,2));
//        
//        double dfdx = -(x_rssi-Xk1)/R1;
//        double dfdy = -(y_rssi-Xk2)/R1;
//        
//        double[][] jacobianData = {{0, 0, dfdx, dfdy}};
//        H = new Array2DRowRealMatrix(jacobianData);
//        //System.out.println("H recalced, dfdx=:"+ H.getEntry(0, 3));
//    }
//    
//    @Override
//    public RealMatrix getMeasurementMatrix() {
//        
//        return H;
//        //throw new UnsupportedOperationException("Not supported yet.");
//    }
//
//    @Override
//    public RealMatrix getMeasurementNoise() {
//        return R;
//        //throw new UnsupportedOperationException("Not supported yet.");
//    }
    
//}
