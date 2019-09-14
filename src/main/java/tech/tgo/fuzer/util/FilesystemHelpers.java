package tech.tgo.fuzer.util;

/**
 * Filesystem I/O
 * @author (timmyedge)
 */

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;
import tech.tgo.fuzer.model.GeoMission;
import tech.tgo.fuzer.model.KMLCircle;

public class FilesystemHelpers {

    // TODO, get this from application.properties
    public static String workingDirectory = "";

    public static void exportGeoMissionToKml(GeoMission geoMission) {
        System.out.println("started KML EXPORT THREAD");
        try
        {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            TransformerFactory tranFactory = TransformerFactory.newInstance();
            Transformer aTransformer = tranFactory.newTransformer();

            Document doc = builder.newDocument();
            Element root = doc.createElement("kml");
            //root.setAttribute("xmlns", "http://earth.google.com/kml/2.1");  -- TODO, get precise kml output type to use here
            //<kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">
            root.setAttribute("xmlns", "http://www.opengis.net/kml/2.2");
            doc.appendChild(root);

            Element dnode = doc.createElement("Document");
            root.appendChild(dnode);

            GeoMission gm = geoMission;

//            Iterator it = gm.targets.keySet().iterator();
//            while (it.hasNext())
//            {
//                String target_key = (String)it.next();
//                Target target = (Target)gm.targets.get(target_key);

            //Target target = geoMission.getT

                //double[] result = target.geoResult;

                /////////////////////////////////////////////////
                /////////// PLOT the measurements /////////////////
                if (gm.showMeas) {
                    Set keys = gm.measurementCircles.keySet();
                    Iterator keyIt = keys.iterator();
                    while (keyIt.hasNext()) {
                        String device = (String) keyIt.next();
                        ArrayList<double[]> circle = (ArrayList<double[]>) gm.measurementCircles.get(device);
                        //System.out.println("HERE::"+device+"firstLAT::"+circle.get(0)[0]);

                        if (!circle.isEmpty())   /// This will only be the case when user has ticked 'show Measurements' checkbox on gmt.
                        {
                            try {
                                System.out.println("CREATING NEW MEAS CIRCLE in KML");

                                Element style = doc.createElement("Style");
                                style.setAttribute("id", "measurementStyle");

                                //Element polyStyle = doc.createElement("PolyStyle");
                                Element measStyle = doc.createElement("LineStyle");
                                Element color = doc.createElement("color");
                                color.appendChild(doc.createTextNode("7fcccc00"));

                                Element width = doc.createElement("width");
                                width.appendChild(doc.createTextNode("3"));

                                //polyStyle.appendChild(color);
                                measStyle.appendChild(color);
                                measStyle.appendChild(width);

                                //style.appendChild(polyStyle);
                                style.appendChild(measStyle);

                                dnode.appendChild(style);

                                ///// create placemark to represent circle polygon
                                Element measPlacemark = doc.createElement("Placemark");
                                dnode.appendChild(measPlacemark);

                                Element name = doc.createElement("name");
                                name.appendChild(doc.createTextNode(device));
                                measPlacemark.appendChild(name);

                                Element styleUrl = doc.createElement("styleUrl");
                                styleUrl.appendChild(doc.createTextNode("#measurementStyle"));

                                measPlacemark.appendChild(styleUrl);

                                Element line = doc.createElement("LineString");
                                Element circleCoords = doc.createElement("coordinates");
                                Element oldCircleCoords = doc.createElement("coordinates");

                                /// TODO, inner circle or create line string rather than polygon?

                                Iterator circlePoints = circle.iterator();

                                try {
                                    synchronized (circlePoints) {
                                        while (circlePoints.hasNext()) {
                                            double[] point = (double[]) circlePoints.next();
                                            //System.out.println("TESTING   lat:"+point[0]+". lng:"+point[1]);
                                            circleCoords.appendChild(doc.createTextNode(point[1] + "," + point[0] + ",0 \n"));
                                        }
                                    }
                                } catch (Exception esynch) {
                                    System.out.println("error iterating over measurement circle, b/c it being updated");
                                    KMLCircle kmlCircle = new KMLCircle(line, oldCircleCoords);
                                    gm.measKMLCircles.put(device, kmlCircle);
                                }
                                line.appendChild(circleCoords);

                                KMLCircle kmlCircle = new KMLCircle(line, circleCoords);
                                gm.measKMLCircles.put(device, kmlCircle);

                                measPlacemark.appendChild(line);
                            } catch (Exception emeas) {
                                System.out.println("error exporting meas circle to kml");
                                emeas.printStackTrace();
                            }
                        }
                    }
                }

                /////////////////////////////////////////////////
                /////////// PLOT the geo result /////////////////
                if (gm.showGEOs)
                {
                    try
                    {
                        System.out.println("Creating GEO Point in KML");

                        Element crosshairStyle = doc.createElement("Style");
                        crosshairStyle.setAttribute("id", "crosshairStyle");

                        Element crosshairIconStyle = doc.createElement("IconStyle");
                        crosshairIconStyle.setAttribute("id", "crosshairIconStyle");

                        Element crosshairIcon = doc.createElement("Icon");

                        Element crosshairIconHref = doc.createElement("href");
                        crosshairIconHref.appendChild(doc.createTextNode(workingDirectory+ "styles/target.png"));

                        crosshairStyle.appendChild(crosshairIconStyle);
                        crosshairIcon.appendChild(crosshairIconHref);
                        crosshairIconStyle.appendChild(crosshairIcon);
                        dnode.appendChild(crosshairStyle);
                        ///////////////////////////////////////////////

                        Element PFplacemark = doc.createElement("Placemark");
                        dnode.appendChild(PFplacemark);

                        Element name = doc.createElement("name");
                        name.appendChild(doc.createTextNode(geoMission.getTarget().getName()));   /// ORIGN - target_key
                        PFplacemark.appendChild(name);

                        PFplacemark.appendChild(crosshairStyle);

                        Element descrip = doc.createElement("description");
                        descrip.appendChild(doc.createTextNode("description? timeLastSeen?"));
                        PFplacemark.appendChild(descrip);

                        Element PFpoint = doc.createElement("Point");
                        //target.coordinates = doc.createElement("coordinates");  ORIG
                        Element coordinates = doc.createElement("coordinates");

                        // Fomrat is Lon/Lat
                        Text textNode = doc.createTextNode(geoMission.getTarget().getCurrent_loc()[0]+ "," + geoMission.getTarget().getCurrent_loc()[1]);

                        //target.coordTextNode = textNode;   DEPRECATED

                        //target.coordinates.appendChild(target.coordTextNode);   ORIG
                        coordinates.appendChild(textNode);    // target.coordTextNode)    ORIG
                        PFpoint.appendChild(coordinates);   //target.    ORIG

                        PFplacemark.appendChild(PFpoint);
                    }
                    catch(Exception egeo){System.out.println("error exporting geo position to kml");egeo.printStackTrace();}
                }
            //}

            Source src = new DOMSource(doc);
            Result dest = new StreamResult(new File(workingDirectory+"output/"+geoMission.getOutputKmlFilename()));
            aTransformer.transform(src, dest);

            System.out.println("[KML Exp Geo] updated map data.....");

            System.out.println("[S] finished KML Geo export");
        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());}
    }
}

///////////////////////////////////////////////////
///////////// PLOT the geo CEP result /////////////////
//                if (gm.showCEPs)
//                        {
//                        try
//                        {
//                        ////// TODO, pull out cepCircles for each target
//                        ArrayList<double[]> cepCircle = target.geoCEPCircle;
//        if (!cepCircleContainer.contains(target))
//        {
//        System.out.println("CREATING NEW CEP POLYGON");
//        /// create new polygon
//        Element style = doc.createElement("Style");
//        style.setAttribute("id", "cepStyle");
//
//        Element polyStyle = doc.createElement("PolyStyle");
//        Element color = doc.createElement("color");
//        color.appendChild(doc.createTextNode("7f0000cc"));
//
//        polyStyle.appendChild(color);
//        style.appendChild(polyStyle);
//
//        dnode.appendChild(style);
//        Element polyPlacemark = doc.createElement("Placemark");
//        dnode.appendChild(polyPlacemark);
//
//        Element name = doc.createElement("name");
//        name.appendChild(doc.createTextNode(target+":cep"));
//        polyPlacemark.appendChild(name);
//
//        Element styleUrl = doc.createElement("styleUrl");
//        styleUrl.appendChild(doc.createTextNode("#cepStyle"));
//
//        polyPlacemark.appendChild(styleUrl);
//
//        Element polygon = doc.createElement("Polygon");
//
//        Element outer = doc.createElement("outerBoundaryIs");
//        target.cepOuterRing = doc.createElement("LinearRing");
//        target.cepCircleCoords = doc.createElement("coordinates");
//
//        /// TODO, inner circle or create line string rather than polygon?
//
//        Iterator circlePoints = cepCircle.iterator();
//        while (circlePoints.hasNext())
//        {
//        double[] point = (double[])circlePoints.next();
//
//        target.cepCircleCoords.appendChild(doc.createTextNode(point[1]+","+point[0]+",0 \n"));
//        }
//        target.cepOuterRing.appendChild(target.cepCircleCoords);
//
//
//        outer.appendChild(target.cepOuterRing);
//        polygon.appendChild(outer);
//
//        polyPlacemark.appendChild(polygon);
//
//        cepCircleContainer.add(target_key);
//        }
//        else
//        {
//        /// update old polygon
//        target.cepOuterRing.removeChild(target.cepCircleCoords);
//        Element oldCircleCoords = target.cepCircleCoords;
//        Element cepCircleCoords = doc.createElement("coordinates");
//
//        Iterator circlePoints = cepCircle.iterator();
//        try
//        {
//synchronized(circlePoints)
//        {
//        while (circlePoints.hasNext())
//        {
//        double[] point = (double[])circlePoints.next();
//
//        cepCircleCoords.appendChild(doc.createTextNode(point[1]+","+point[0]+",0 \n"));
//        }
//        target.cepOuterRing.appendChild(cepCircleCoords);
//        target.cepCircleCoords = cepCircleCoords;
//        }
//        }
//        catch(Exception esynch)
//        {
//        System.out.println("error iterating through circle points, I'll skip this circle export and hold onto the old circle, should be ok next time(?) TBC");
//        target.cepOuterRing.appendChild(oldCircleCoords);
//        target.cepCircleCoords = oldCircleCoords;
//        }
//        }
//        }
//        catch(Exception ecep){System.out.println("error exporting cep circle to kml"); ecep.printStackTrace();}
//        }



// Exmaple that works with OSX marble
//<?xml version="1.0" encoding="UTF-8"?>
//<kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">
//<Document>
//<name>Route</name>
//<Folder>
//<name>Route Request</name>
//<Placemark>
//<Point>
//<coordinates>138.5986300000,-34.9286600000,59.0000000000</coordinates>
//</Point>
//<ExtendedData>
//<Data name="routingVisited">
//<value>false</value>
//</Data>
//</ExtendedData>
//</Placemark>
//<Placemark>
//<Point>
//<coordinates>-74.2654200000,40.5067700000,20.0000000000</coordinates>
//</Point>
//<ExtendedData>
//<Data name="routingVisited">
//<value>false</value>
//</Data>
//</ExtendedData>
//</Placemark>
//</Folder>
//</Document>
//</kml>