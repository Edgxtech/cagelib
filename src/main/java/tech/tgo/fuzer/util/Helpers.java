package tech.tgo.fuzer.util;

import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

public class Helpers {

    public static int SPEED_OF_LIGHT = 299792458; // [m/s]

    // THIS IS FOR UPDATING DEVICES LOCATION WHEN GIVEN IN LAT,LON, filter needs to process it in UTM
    //     Returns Northing,Easting
    // Example UTM coords: 6470194.755756934,403548.8617473827
    public static double[] convertLatLngToUtmNthingEasting(double lat, double lng) {
        // convert lat,lng to UTM, then (in the filter, divide the calculated distance by 1000 to put it into [km]

        LatLng ltln = new LatLng(lat,lng);

        UTMRef utm = ltln.toUTMRef();

        return new double[]{utm.getNorthing(),utm.getEasting()};
    }

    public static double[] convertUtmNthingEastingToLatLng(double nthing, double easting, char latZone, int lngZone) {
        UTMRef utm = new UTMRef(nthing,easting,latZone,lngZone);

        LatLng ltln = utm.toLatLng();
        return new double[]{ltln.getLat(),ltln.getLng()};
    }

//    public static double[] createHyperbola(double x1,double y1,double x2,double y2,double t1,double t2,double d) {
//
//        double c = Math.sqrt(Math.pow((x2-x1),2)+Math.pow((y2-y1),2))/2;
//        double a=d/2; double b=Math.sqrt(Math.pow(c,2)-Math.pow(a,2));
//        double ca = (x2-x1)/(2*c); double sa = (y2-y1)/(2*c); // COS and SIN of rot angle
//        for (double t = t1; t<= t2; t += 0.1) {
//            X = a*cosh(t); Y = b*sinh(t); // Hyperbola branch
//            x = (x1+x2)/2 + X*ca - Y*sa; # Rotated and translated
//            y = (y1+y2)/2 + X*sa + Y*ca;
//        }
//        //                result <- hypp(Xa[1],Xa[2],Xb[1],Xb[2],-2,2,f_true)
////                plot(result)
////
////                hypp <- function(x1,y1,x2,y2,t1,t2,d) {
////                    t = seq(t1,t2,by=0.1);
////                    c = sqrt((x2-x1)^2+(y2-y1)^2)/2;
////                    a=d/2; b=sqrt(c^2-a^2);
////                    X = a*cosh(t); Y = b*sinh(t); # Hyperbola branch
////                    ca = (x2-x1)/(2*c); sa = (y2-y1)/(2*c); #COS and SIN of rot angle
////                    x = (x1+x2)/2 + X*ca - Y*sa; # Rotated and translated
////                            y = (y1+y2)/2 + X*sa + Y*ca;
////                    print(paste('Len x',length(x)))
////                    print(paste('Len y',length(y)))
////                    result <- cbind(x,y)
////                    print(result)
////                    return(result)
////                }
//
//
//    }
}
