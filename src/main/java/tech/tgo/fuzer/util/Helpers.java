package tech.tgo.fuzer.util;

import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

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
}
