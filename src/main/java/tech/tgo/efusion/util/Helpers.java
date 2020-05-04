package tech.tgo.efusion.util;

import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

/**
 * @author Timothy Edge (timmyedge)
 */
public class Helpers {

    public static int SPEED_OF_LIGHT = 299792458; // [m/s]

    /*  Convert lat/lon to UTM northing/easting
    /*  - Filter operates in UTM format coords
    /*  - Returns Northing,Easting
    /*  - Example UTM coords: 6470194.755756934,403548.8617473827 */
    public static double[] convertLatLngToUtmNthingEasting(double lat, double lng) {
        LatLng ltln = new LatLng(lat,lng);
        UTMRef utm = ltln.toUTMRef();
        return new double[]{utm.getNorthing(),utm.getEasting()};
    }

    public static Object[] getUtmLatZoneLonZone(double lat, double lng) {
        LatLng ltln = new LatLng(lat,lng);
        UTMRef utm = ltln.toUTMRef();
        return new Object[]{utm.getLatZone(),utm.getLngZone()};
    }

    public static double[] convertUtmNthingEastingToLatLng(double nthing, double easting, char latZone, int lngZone) {
        UTMRef utm = new UTMRef(nthing,easting,latZone,lngZone);
        LatLng ltln = utm.toLatLng();
        return new double[]{ltln.getLat(),ltln.getLng()};
    }

//    public static double[] getEigenvalues2D(double[][] matrix) {
//        double a = matrix[0][0];
//        double b = matrix[0][1];
//        double e1 = ((a+d) + Math.sqrt( Math.pow(a-d,2) + 4*b*c))/2;
//        double e2 = ((a+d) - Math.sqrt( Math.pow(a-d,2) + 4*b*c))/2;
//        return new double[]{e1,e2};
//    }

    public static double[] getEigenvalues(double[][] matrix) {
        double a = matrix[0][0];
        double b = matrix[0][1];
        double c = matrix[1][0];
        double d = matrix[1][1];
        double e1 = ((a+d) + Math.sqrt( Math.pow(a-d,2) + 4*b*c))/2;
        double e2 = ((a+d) - Math.sqrt( Math.pow(a-d,2) + 4*b*c))/2;
        return new double[]{e1,e2};
        /* NOTE: This also works
            RealMatrix J2 = new Array2DRowRealMatrix(covMatrix);
            EigenDecomposition eig = new EigenDecomposition(J2);
            double[] evaluesC = eig.getRealEigenvalues();
            log.debug("#4 E-values: "+evaluesC[0]+","+evaluesC[1]);
            double[] evectorC = eig.(covMatrix, largestEvalue);
            log.debug("#1 E-vector: "+evector[0]+","+evector[1]);
            RealMatrix V = eig.getV();
            RealMatrix D = eig.getD();
         */
    }
    public static double[] getEigenvector(double[][] matrix, double eigenvalue) {
        double a = matrix[0][0];
        double b = matrix[0][1];
        double c = matrix[1][0];
        double d = matrix[1][1];
        double e = eigenvalue;
        double x = b; double y = e-a;
        double r = Math.sqrt(x*x+y*y);
        if( r > 0) { x /= r; y /= r; }
        else {
            x = e-d; y = c;
            r = Math.sqrt(x*x+y*y);
            if( r > 0) { x /= r; y /= r; }
            else {
                x = 1; y = 0;
            }
        }
        return new double[]{x,y};
//        e = eigenvalue2;
//        x = b; y = e-a;
//        r = Math.sqrt(x*x+y*y);
//        if( r > 0) { x /= r; y /= r; }
//        else {
//            x = e-d; y = c;
//            r = Math.sqrt(x*x+y*y);
//            if( r > 0) { x /= r; y /= r; }
//            else {
//                x = 0; y = 1;
//            }
//        }
//        System.out.println("Eigenvector2: (" + x + "," + y + ")");
//
//
//        double[][] basis = new double[2][2];
//
//        for (double y = -1000; y <= 1000; y++) {
//            for (double x = -1000; x <= 1000; x++) {
//                if (((a-eigenvalue1)*x + b*y == 0) && (c*x + (d-eigenvalue1)*y == 0)) {
//                    System.out.println("Eigenvector1: (" + x + "," + y + ")");
//                    basis[0] = eigenvalue1;
//                }
//            }
//        }
//
//        for (double y = -10; y <= 10; y++) {
//            for (double x = -10; x <= 10; x++) {
//                if (((a-eigenvalue2)*x + b*y == 0) && (c*x + (d-eigenvalue2)*y == 0)) {
//                    System.out.println("Eigenvector2: (" + x + "," + y + ")");
//                    basis[1] = eigenvalue2;
//                }
//            }
//        }
//
//        return basis;
    }
}
