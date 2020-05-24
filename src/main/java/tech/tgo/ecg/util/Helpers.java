package tech.tgo.ecg.util;

import tech.tgo.ecg.model.InitialStateBoxCorner;
import tech.tgo.ecg.model.Asset;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Timothy Edge (timmyedge)
 */
public class Helpers {

    public static int SPEED_OF_LIGHT = 299792458; // [m/s]

    /* Measurement errors, default, used for unrealistic no-sim-error scenarios, need at least a small amount of error to function */
    public static double SMALL_DEFAULT_RANGE_MEAS_ERROR = 0.25;
    public static double SMALL_DEFAULT_TDOA_MEAS_ERROR = 0.25;
    public static double SMALL_DEFAULT_AOA_MEAS_ERROR = 0.25;


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

    public static double[] getEigenvalues(double[][] matrix) {
        double a = matrix[0][0];
        double b = matrix[0][1];
        double c = matrix[1][0];
        double d = matrix[1][1];
        double e1 = ((a+d) + Math.sqrt( Math.pow(a-d,2) + 4*b*c))/2;
        double e2 = ((a+d) - Math.sqrt( Math.pow(a-d,2) + 4*b*c))/2;
        return new double[]{e1,e2};
        /* NOTE: also works
            RealMatrix J2 = new Array2DRowRealMatrix(covMatrix);
            EigenDecomposition eig = new EigenDecomposition(J2);
            double[] evaluesC = eig.getRealEigenvalues();
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
    }

    public static Double[] findRudimentaryStartPoint(Asset asset_a, Asset asset_b, double addition) {
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
        return new Double[]{x_init,y_init};
    }

    public static Double[] getCornerLatLon(InitialStateBoxCorner corner, Collection<Asset> assets) {
        double standardUTMOffset = 5000;
        List<Double> lats = new ArrayList<Double>();
        List<Double> lons = new ArrayList<Double>();
        for (Asset asset : assets) {
            double[] utm = Helpers.convertLatLngToUtmNthingEasting(asset.getCurrent_loc()[0],asset.getCurrent_loc()[1]);
            lats.add(utm[0]);
            lons.add(utm[1]);
        }
        if (corner.equals(InitialStateBoxCorner.TOP_RIGHT)) {
            return new Double[]{Collections.max(lons) + standardUTMOffset, Collections.max(lats) + standardUTMOffset};
        }
        else if (corner.equals(InitialStateBoxCorner.BOTTOM_RIGHT)) {
            return new Double[]{Collections.min(lons) - standardUTMOffset, Collections.max(lats) + standardUTMOffset};
        }
        else if (corner.equals(InitialStateBoxCorner.BOTTOM_LEFT)) {
            return new Double[]{Collections.min(lons) - standardUTMOffset, Collections.min(lats) - standardUTMOffset};
        }
        else if (corner.equals(InitialStateBoxCorner.TOP_LEFT)) {
            return new Double[]{Collections.max(lons) + standardUTMOffset, Collections.min(lats) - standardUTMOffset}; //+ standardUTMOffset
            //return new Double[]{Helpers.convertLatLngToUtmNthingEasting(-34.9,138.0)[0],Helpers.convertLatLngToUtmNthingEasting(-34.9,138.0)[1]};
        }
        else {
            return null;
        }
    }
}
