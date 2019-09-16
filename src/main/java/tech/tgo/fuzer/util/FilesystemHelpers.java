package tech.tgo.fuzer.util;

/**
 * Filesystem I/O
 * @author (timmyedge)
 */

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.print.Doc;
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
import tech.tgo.fuzer.model.Asset;
import tech.tgo.fuzer.model.GeoMission;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

public class FilesystemHelpers {

    // TODO, get this from application.properties
    public static String workingDirectory = "";

    public static void exportAssetLocation(Document doc, Element dnode, GeoMission geoMission, Asset asset) {
        try
        {
            Element crosshairStyle = doc.createElement("Style");
            crosshairStyle.setAttribute("id", "assetStyle");

            Element crosshairIconStyle = doc.createElement("IconStyle");
            crosshairIconStyle.setAttribute("id", "assetIconStyle");

            Element crosshairIcon = doc.createElement("Icon");

            Element crosshairIconHref = doc.createElement("href");
            crosshairIconHref.appendChild(doc.createTextNode("http://maps.google.com/mapfiles/kml/shapes/cabs.png"));

            crosshairStyle.appendChild(crosshairIconStyle);
            crosshairIcon.appendChild(crosshairIconHref);
            crosshairIconStyle.appendChild(crosshairIcon);
            dnode.appendChild(crosshairStyle);

            Element PFplacemark = doc.createElement("Placemark");
            dnode.appendChild(PFplacemark);

            Element name = doc.createElement("name");
            name.appendChild(doc.createTextNode(asset.getId()));
            PFplacemark.appendChild(name);

            PFplacemark.appendChild(crosshairStyle);

            Element descrip = doc.createElement("description");
            descrip.appendChild(doc.createTextNode("<![CDATA[\n" +
                    "          <p><font color=\"blue\">"+asset.getId()+"\n" +
                    "          <b>Located here</b></font></p>"));
            PFplacemark.appendChild(descrip);

            Element PFpoint = doc.createElement("Point");
            Element coordinates = doc.createElement("coordinates");

            // Format is Lon/Lat
            Text textNode = doc.createTextNode(asset.getCurrent_loc()[1]+ "," + asset.getCurrent_loc()[0]);
            coordinates.appendChild(textNode);
            PFpoint.appendChild(coordinates);

            PFplacemark.appendChild(PFpoint);
        }
        catch(Exception egeo){
            System.out.println("error exporting asset position to kml");egeo.printStackTrace();
        }
    }

    public static void exportMeasurementCircles(Document doc, Element dnode, GeoMission geoMission) {
        ////// PLOT RANGE MEASUREMENTS
        Set keys = geoMission.measurementCircles.keySet();
        System.out.println("# measurement circles: "+keys.size());
        Iterator keyIt = keys.iterator();
        while (keyIt.hasNext()) {
            String assetId = (String) keyIt.next();
            System.out.println("Creating kml for asset: "+assetId);
            ArrayList<double[]> circle = (ArrayList<double[]>) geoMission.measurementCircles.get(assetId);

            System.out.println("This asset has measurement circle data? "+!circle.isEmpty());
            if (!circle.isEmpty())
            {
                try {
                    System.out.println("CREATING NEW MEAS CIRCLE in KML");

                    Element style = doc.createElement("Style");
                    style.setAttribute("id", "measurementStyle");

                    Element measStyle = doc.createElement("LineStyle");
                    Element color = doc.createElement("color");
                    color.appendChild(doc.createTextNode("7fcccc00"));

                    Element width = doc.createElement("width");
                    width.appendChild(doc.createTextNode("3"));

                    measStyle.appendChild(color);
                    measStyle.appendChild(width);

                    style.appendChild(measStyle);

                    dnode.appendChild(style);

                    Element measPlacemark = doc.createElement("Placemark");
                    dnode.appendChild(measPlacemark);

                    Element name = doc.createElement("name");
                    name.appendChild(doc.createTextNode("Range:"+assetId));
                    measPlacemark.appendChild(name);

                    Element styleUrl = doc.createElement("styleUrl");
                    styleUrl.appendChild(doc.createTextNode("#measurementStyle"));

                    measPlacemark.appendChild(styleUrl);

                    Element line = doc.createElement("LineString");
                    Element circleCoords = doc.createElement("coordinates");

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
                    }
                    line.appendChild(circleCoords);

                    measPlacemark.appendChild(line);
                } catch (Exception emeas) {
                    System.out.println("error exporting meas circle to kml");
                    emeas.printStackTrace();
                }
            }
        }
    }

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
            root.setAttribute("xmlns", "http://www.opengis.net/kml/2.2");
            root.setAttribute("xmlns:gx","http://www.google.com/kml/ext/2.2"); // alt "xmlns", "http://earth.google.com/kml/2.1"
            doc.appendChild(root);

            Element dnode = doc.createElement("Document");
            root.appendChild(dnode);

            GeoMission gm = geoMission;

            // For each unique asset
            System.out.println("# assets providing measurements: "+gm.getAssets().size());
            Iterator keyIt = gm.getAssets().keySet().iterator();
            while (keyIt.hasNext()) {
                String assetId = (String)keyIt.next();
                System.out.println("Creating Asset Point in KML: for asset id: "+assetId);
                System.out.println("Creating Asset Point in KML: lat: "+gm.getAssets().get(assetId).getCurrent_loc()[0]+", lon: "+gm.getAssets().get(assetId).getCurrent_loc()[1]);
                exportAssetLocation(doc,dnode,geoMission,gm.getAssets().get(assetId));
            }

            /////////////////////////////////////////////////
            /////////// PLOT the measurements /////////////////
            if (gm.showMeas) {

                exportMeasurementCircles(doc,dnode,geoMission);
                ////// PLOT RANGE MEASUREMENTS
//                Set keys = gm.measurementCircles.keySet();
//                System.out.println("# measurement circles: "+keys.size());
//                keyIt = keys.iterator();
//                while (keyIt.hasNext()) {
//                    String assetId = (String) keyIt.next();
//                    System.out.println("Creating kml for asset: "+assetId);
//                    ArrayList<double[]> circle = (ArrayList<double[]>) gm.measurementCircles.get(assetId);
//
//                    System.out.println("This asset has measurement circle data? "+!circle.isEmpty());
//                    if (!circle.isEmpty())
//                    {
//                        try {
//                            System.out.println("CREATING NEW MEAS CIRCLE in KML");
//
//                            Element style = doc.createElement("Style");
//                            style.setAttribute("id", "measurementStyle");
//
//                            Element measStyle = doc.createElement("LineStyle");
//                            Element color = doc.createElement("color");
//                            color.appendChild(doc.createTextNode("7fcccc00"));
//
//                            Element width = doc.createElement("width");
//                            width.appendChild(doc.createTextNode("3"));
//
//                            measStyle.appendChild(color);
//                            measStyle.appendChild(width);
//
//                            style.appendChild(measStyle);
//
//                            dnode.appendChild(style);
//
//                            ///// create placemark to represent circle polygon
//                            Element measPlacemark = doc.createElement("Placemark");
//                            dnode.appendChild(measPlacemark);
//
//                            Element name = doc.createElement("name");
//                            name.appendChild(doc.createTextNode("Range:"+assetId));
//                            measPlacemark.appendChild(name);
//
//                            Element styleUrl = doc.createElement("styleUrl");
//                            styleUrl.appendChild(doc.createTextNode("#measurementStyle"));
//
//                            measPlacemark.appendChild(styleUrl);
//
//                            Element line = doc.createElement("LineString");
//                            Element circleCoords = doc.createElement("coordinates");
//                            Element oldCircleCoords = doc.createElement("coordinates");
//
//                            /// TODO, inner circle or create line string rather than polygon?
//
//                            Iterator circlePoints = circle.iterator();
//
//                            try {
//                                synchronized (circlePoints) {
//                                    while (circlePoints.hasNext()) {
//                                        double[] point = (double[]) circlePoints.next();
//                                        //System.out.println("TESTING   lat:"+point[0]+". lng:"+point[1]);
//                                        circleCoords.appendChild(doc.createTextNode(point[1] + "," + point[0] + ",0 \n"));
//                                    }
//                                }
//                            } catch (Exception esynch) {
//                                System.out.println("error iterating over measurement circle, b/c it being updated");
//                            }
//                            line.appendChild(circleCoords);
//
//                            measPlacemark.appendChild(line);
//                        } catch (Exception emeas) {
//                            System.out.println("error exporting meas circle to kml");
//                            emeas.printStackTrace();
//                        }
//                    }
//                }

                ////// PLOT TDOA MEASUREMENTS
                Set keys = gm.measurementHyperbolas.keySet();
                System.out.println("# measurement hyperbolas: "+keys.size());
                keyIt = keys.iterator();
                while (keyIt.hasNext()) {
                    String assetId = (String) keyIt.next();
                    System.out.println("Creating kml for asset: "+assetId);
                    ArrayList<double[]> hyperbola = (ArrayList<double[]>) gm.measurementHyperbolas.get(assetId);

                    System.out.println("This asset has measurement hyperbola data? "+!hyperbola.isEmpty());
                    if (!hyperbola.isEmpty())
                    {
                        try {
                            System.out.println("CREATING NEW MEAS Hyperbola in KML");

                            Element style = doc.createElement("Style");
                            style.setAttribute("id", "measurementStyle");

                            Element measStyle = doc.createElement("LineStyle");
                            Element color = doc.createElement("color");
                            color.appendChild(doc.createTextNode("7fccfc00"));

                            Element width = doc.createElement("width");
                            width.appendChild(doc.createTextNode("3"));

                            measStyle.appendChild(color);
                            measStyle.appendChild(width);

                            style.appendChild(measStyle);

                            dnode.appendChild(style);

                            Element measPlacemark = doc.createElement("Placemark");
                            dnode.appendChild(measPlacemark);

                            Element name = doc.createElement("name");
                            name.appendChild(doc.createTextNode("TDOA:"+assetId));
                            measPlacemark.appendChild(name);

                            Element styleUrl = doc.createElement("styleUrl");
                            styleUrl.appendChild(doc.createTextNode("#measurementStyle"));

                            measPlacemark.appendChild(styleUrl);

                            Element line = doc.createElement("LineString");
                            Element circleCoords = doc.createElement("coordinates");
                            Element oldCircleCoords = doc.createElement("coordinates");

                            /// TODO, inner circle or create line string rather than polygon?

                            Iterator hyperbolaPoints = hyperbola.iterator();

                            try {
                                synchronized (hyperbolaPoints) {
                                    while (hyperbolaPoints.hasNext()) {
                                        double[] point = (double[]) hyperbolaPoints.next();
                                        circleCoords.appendChild(doc.createTextNode(point[1] + "," + point[0] + ",0 \n"));
                                    }
                                }
                            } catch (Exception esynch) {
                                System.out.println("error iterating over measurement hyperbola, b/c it being updated");
                            }
                            line.appendChild(circleCoords);

                            measPlacemark.appendChild(line);
                        } catch (Exception emeas) {
                            System.out.println("error exporting meas hyperbola to kml");
                            emeas.printStackTrace();
                        }
                    }
                }

                ////// PLOT AOA MEASUREMENTS
                keys = gm.measurementLines.keySet();
                System.out.println("# measurement lines: "+keys.size());
                keyIt = keys.iterator();
                while (keyIt.hasNext()) {
                    String assetId = (String) keyIt.next();
                    System.out.println("Creating AOA kml for asset: "+assetId);
                    ArrayList<double[]> aoa_line = (ArrayList<double[]>) gm.measurementLines.get(assetId);

                    System.out.println("This asset has measurement line data? "+!aoa_line.isEmpty());
                    if (!aoa_line.isEmpty())
                    {
                        try {
                            System.out.println("CREATING NEW MEAS Line in KML");

                            Element style = doc.createElement("Style");
                            style.setAttribute("id", "measurementStyle");

                            Element measStyle = doc.createElement("LineStyle");
                            Element color = doc.createElement("color");
                            color.appendChild(doc.createTextNode("7fcffc00"));

                            Element width = doc.createElement("width");
                            width.appendChild(doc.createTextNode("3"));

                            measStyle.appendChild(color);
                            measStyle.appendChild(width);

                            style.appendChild(measStyle);

                            dnode.appendChild(style);

                            Element measPlacemark = doc.createElement("Placemark");
                            dnode.appendChild(measPlacemark);

                            Element name = doc.createElement("name");
                            name.appendChild(doc.createTextNode("AOA:"+assetId));
                            measPlacemark.appendChild(name);

                            Element styleUrl = doc.createElement("styleUrl");
                            styleUrl.appendChild(doc.createTextNode("#measurementStyle"));

                            measPlacemark.appendChild(styleUrl);

                            Element line = doc.createElement("LineString");
                            Element circleCoords = doc.createElement("coordinates");
                            Element oldCircleCoords = doc.createElement("coordinates");

                            Iterator linePoints = aoa_line.iterator();

                            try {
                                synchronized (linePoints) {
                                    while (linePoints.hasNext()) {
                                        double[] point = (double[]) linePoints.next();
                                        circleCoords.appendChild(doc.createTextNode(point[1] + "," + point[0] + ",0 \n"));
                                    }
                                }
                            } catch (Exception esynch) {
                                System.out.println("error iterating over measurement line, b/c it being updated");
                            }
                            line.appendChild(circleCoords);

                            measPlacemark.appendChild(line);
                        } catch (Exception emeas) {
                            System.out.println("error exporting meas line to kml");
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
                    crosshairIconHref.appendChild(doc.createTextNode("http://maps.google.com/mapfiles/kml/shapes/earthquake.png"));

                    crosshairStyle.appendChild(crosshairIconStyle);
                    crosshairIcon.appendChild(crosshairIconHref);
                    crosshairIconStyle.appendChild(crosshairIcon);
                    dnode.appendChild(crosshairStyle);
                    ///////////////////////////////////////////////

                    Element PFplacemark = doc.createElement("Placemark");
                    dnode.appendChild(PFplacemark);

                    Element name = doc.createElement("name");
                    name.appendChild(doc.createTextNode(geoMission.getTarget().getName()));
                    PFplacemark.appendChild(name);

                    PFplacemark.appendChild(crosshairStyle);

                    Element descrip = doc.createElement("description");
                    descrip.appendChild(doc.createTextNode("<![CDATA[\n" +
                            "          <p><font color=\"red\">"+geoMission.getTarget().getId()+" : "+geoMission.getTarget().getName()+"\n" +
                            "          <b>Located here</b></font></p>"));
                    PFplacemark.appendChild(descrip);

                    Element PFpoint = doc.createElement("Point");
                    Element coordinates = doc.createElement("coordinates");

                    // Format is Lon/Lat
                    Text textNode = doc.createTextNode(geoMission.getTarget().getCurrent_loc()[1]+ "," + geoMission.getTarget().getCurrent_loc()[0]);
                    coordinates.appendChild(textNode);
                    PFpoint.appendChild(coordinates);

                    PFplacemark.appendChild(PFpoint);
                }
                catch(Exception egeo){
                    System.out.println("error exporting geo position to kml");egeo.printStackTrace();
                }
            }


            ///////////////////////////////////////////////////
            ///////////// PLOT the geo CEP result /////////////////
            if (geoMission.showCEPs)
            {
                try
                {
                    List<double[]> cepCircle = new ArrayList<double[]>();

                    double[] utm_target_loc = Helpers.convertLatLngToUtmNthingEasting(geoMission.getTarget().getCurrent_loc()[0],geoMission.getTarget().getCurrent_loc()[1]);

                    for (double theta = (1/2)*Math.PI; theta <= (5/2)*Math.PI; theta+= 0.2)
                    {
                        UTMRef utmCEP = new UTMRef(geoMission.getTarget().getCurrent_cep()*Math.cos(theta) + utm_target_loc[1], geoMission.getTarget().getCurrent_cep()*Math.sin(theta) + utm_target_loc[0], geoMission.getLatZone(), geoMission.getLonZone());
                        LatLng ltln2 = utmCEP.toLatLng();
                        double[] cepPoint = {ltln2.getLat(),ltln2.getLng()};
                        cepCircle.add(cepPoint);
                    }

                    System.out.println("CREATING NEW CEP POLYGON");
                    /// create new polygon
                    Element style = doc.createElement("Style");
                    style.setAttribute("id", "cepStyle");

                    Element polyStyle = doc.createElement("PolyStyle");
                    Element color = doc.createElement("color");
                    color.appendChild(doc.createTextNode("3f2002e4"));

                    polyStyle.appendChild(color);
                    style.appendChild(polyStyle);

                    dnode.appendChild(style);
                    Element polyPlacemark = doc.createElement("Placemark");
                    dnode.appendChild(polyPlacemark);

                    Element name = doc.createElement("name");
                    name.appendChild(doc.createTextNode(geoMission.getTarget().getId()+":cep"));
                    polyPlacemark.appendChild(name);

                    Element styleUrl = doc.createElement("styleUrl");
                    styleUrl.appendChild(doc.createTextNode("#cepStyle"));

                    polyPlacemark.appendChild(styleUrl);

                    Element polygon = doc.createElement("Polygon");

                    Element outer = doc.createElement("outerBoundaryIs");
                    Element cepOuterRing = doc.createElement("LinearRing");
                    Element cepCircleCoords = doc.createElement("coordinates");

                    Iterator circlePoints = cepCircle.iterator();
                    while (circlePoints.hasNext())
                    {
                        double[] point = (double[])circlePoints.next();

                        cepCircleCoords.appendChild(doc.createTextNode(point[1]+","+point[0]+",0 \n"));
                    }
                    cepOuterRing.appendChild(cepCircleCoords);

                    outer.appendChild(cepOuterRing);
                    polygon.appendChild(outer);

                    polyPlacemark.appendChild(polygon);
                }
                catch(Exception ecep){System.out.println("error exporting cep circle to kml"); ecep.printStackTrace();}
            }


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

