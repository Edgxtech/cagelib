package tech.tgo.fuzer.util;

import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

public class CoordHelpers {

    // THIS IS FOR UPDATING DEVICES LOCATION WHEN GIVEN IN LAT,LON, filter needs to process it in UTM
    //     Returns Northing,Easting
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
}
