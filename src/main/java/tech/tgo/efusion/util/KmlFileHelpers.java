package tech.tgo.efusion.util;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;
import tech.tgo.efusion.model.Asset;
import tech.tgo.efusion.model.GeoMission;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

/**
 * KML Filesystem I/O - (Static Applications)
 * @author Timothy Edge (timmyedge)
 */
public class KmlFileHelpers {

    private static final Logger log = LoggerFactory.getLogger(KmlFileHelpers.class);

    public static void exportGeoMissionToKml(GeoMission geoMission) {
        try
        {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            TransformerFactory tranFactory = TransformerFactory.newInstance();
            Transformer aTransformer = tranFactory.newTransformer();

            Document doc = builder.newDocument();
            Element root = doc.createElement("kml");
            root.setAttribute("xmlns", "http://www.opengis.net/kml/2.2");
            root.setAttribute("xmlns:gx","http://www.google.com/kml/ext/2.2");
            doc.appendChild(root);

            Element dnode = doc.createElement("Document");
            root.appendChild(dnode);

            GeoMission gm = geoMission;

            // For each unique asset
            log.trace("# assets providing measurements: "+gm.getAssets().size());
            Iterator keyIt = gm.getAssets().keySet().iterator();
            while (keyIt.hasNext()) {
                String assetId = (String)keyIt.next();
                log.trace("Creating Asset Point in KML: lat: "+gm.getAssets().get(assetId).getCurrent_loc()[0]+", lon: "+gm.getAssets().get(assetId).getCurrent_loc()[1]);
                exportAssetLocation(doc,dnode,gm.getAssets().get(assetId));
            }

            /* PLOT the measurements */
            if (gm.getShowMeas()) {

                Element style = doc.createElement("Style");
                style.setAttribute("id", "measurementStyle");
                Element measStyle = doc.createElement("LineStyle");
                Element color = doc.createElement("color");
                color.appendChild(doc.createTextNode("ff888888")); //"7fccfc00"));
                Element width = doc.createElement("width");
                width.appendChild(doc.createTextNode("3"));
                measStyle.appendChild(color);
                measStyle.appendChild(width);
                style.appendChild(measStyle);
                dnode.appendChild(style);

                /* Export range measurements */
                exportMeasurementCircles(doc,dnode,geoMission);

                /* Export tdoa measurements */
                exportMeasurementHyperbolas(doc,dnode,geoMission);

                /* Export tdoa measurements */
                exportMeasurementDirections(doc,dnode,geoMission);
            }

            /* PLOT the geo result */
            if (gm.getShowGEOs())
            {
                exportTargetEstimationResult(doc,dnode,geoMission);
            }

            /* PLOT the geo probability ELP result */
            if (geoMission.getShowCEPs())
            {
                exportTargetEstimationCEP(doc,dnode,geoMission);
            }

            /* PLOT the true target loc - for experiment purposes */
            if (gm.getShowTrueLoc())
            {
                exportTargetTrueLocation(doc, dnode, geoMission);
            }

            Source src = new DOMSource(doc);
            Result dest = new StreamResult(new File(geoMission.getProperties().getProperty("working.directory")+"output/"+geoMission.getOutputKmlFilename()));
            aTransformer.transform(src, dest);

            log.debug("[KML Exp Geo] finished KML Geo export - updated map data");
        }
        catch (Exception e)
        {
            log.error(e.getMessage());}
    }

    public static void exportTargetTrueLocation(Document doc, Element dnode, GeoMission geoMission) {
        try {
            if (geoMission.getTarget().getTrue_current_loc()!=null) {
                Element crosshairStyle = doc.createElement("Style");
                crosshairStyle.setAttribute("id", "crosshairStyle");

                Element crosshairIconStyle = doc.createElement("IconStyle");
                crosshairIconStyle.setAttribute("id", "crosshairIconStyle");

                Element crosshairIcon = doc.createElement("Icon");

                Element crosshairIconHref = doc.createElement("href");
                crosshairIconHref.appendChild(doc.createTextNode("http://maps.google.com/mapfiles/kml/pal4/icon47.png"));

                crosshairStyle.appendChild(crosshairIconStyle);
                crosshairIcon.appendChild(crosshairIconHref);
                crosshairIconStyle.appendChild(crosshairIcon);
                dnode.appendChild(crosshairStyle);

                Element PFplacemark = doc.createElement("Placemark");
                dnode.appendChild(PFplacemark);

                Element name = doc.createElement("name");
                name.appendChild(doc.createTextNode(geoMission.getTarget().getName()));
                PFplacemark.appendChild(name);

                PFplacemark.appendChild(crosshairStyle);

                Element descrip = doc.createElement("description");
                descrip.appendChild(doc.createTextNode("<![CDATA[\n" +
                        "          <p><font color=\"red\">" + geoMission.getTarget().getId() + " : " + geoMission.getTarget().getName() + "\n" +
                        "          <b>(True Location) is here</b></font></p>"));
                PFplacemark.appendChild(descrip);

                Element PFpoint = doc.createElement("Point");
                Element coordinates = doc.createElement("coordinates");

                Text textNode = doc.createTextNode(geoMission.getTarget().getTrue_current_loc()[1] + "," + geoMission.getTarget().getTrue_current_loc()[0]);
                coordinates.appendChild(textNode);
                PFpoint.appendChild(coordinates);

                PFplacemark.appendChild(PFpoint);
            }
            else {
                log.debug("Attempted to export true location however none was available");
            }
        } catch (Exception e) {
            log.error("Error exporting true location: " + e.toString());
        }

    }

    public static void exportTargetEstimationResult(Document doc, Element dnode, GeoMission geoMission) {
        try
        {
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

            Text textNode = doc.createTextNode(geoMission.getTarget().getCurrent_loc()[1]+ "," + geoMission.getTarget().getCurrent_loc()[0]);
            coordinates.appendChild(textNode);
            PFpoint.appendChild(coordinates);

            PFplacemark.appendChild(PFpoint);
        }
        catch(Exception egeo){
            log.trace("error exporting geo position to kml");
            egeo.printStackTrace();
        }
    }

    public static void exportTargetEstimationCEP(Document doc, Element dnode, GeoMission geoMission) {
        try {
            try
            {
                List<double[]> geometryCoords = new ArrayList<double[]>();

                double[] utm_target_loc = Helpers.convertLatLngToUtmNthingEasting(geoMission.getTarget().getCurrent_loc()[0], geoMission.getTarget().getCurrent_loc()[1]);
                log.debug("UTM Target Loc: "+utm_target_loc[0]+", "+utm_target_loc[1]);

                double[][] M_rot = new double[][]{{Math.cos(geoMission.getTarget().getElp_rot()), -Math.sin(geoMission.getTarget().getElp_rot())}, {Math.cos(geoMission.getTarget().getElp_rot()), Math.sin(geoMission.getTarget().getElp_rot())}};

                for (double theta = (1 / 2) * Math.PI; theta <= (5 / 2) * Math.PI; theta += 0.2) {
                    double a = geoMission.getTarget().getElp_major() * Math.cos(theta);
                    double b = geoMission.getTarget().getElp_minor() * Math.sin(theta);

                    double x = M_rot[0][0] * (a) + M_rot[0][1] * (b);
                    double y = M_rot[1][0] * (a) + M_rot[1][1] * (b);

                    UTMRef utmMeas = new UTMRef(x + utm_target_loc[1], y + utm_target_loc[0], geoMission.getLatZone(), geoMission.getLonZone());
                    LatLng ltln = utmMeas.toLatLng();
                    double[] measPoint = {ltln.getLat(), ltln.getLng()};
                    geometryCoords.add(measPoint);
                }

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

                Iterator circlePoints = geometryCoords.iterator();
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
            catch(Exception ecep){log.trace("error exporting cep circle to kml"); ecep.printStackTrace();}
        } catch (Exception e) {
            log.error(e.toString());
            e.printStackTrace();
        }
    }

    public static void exportAssetLocation(Document doc, Element dnode, Asset asset) {
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

            Text textNode = doc.createTextNode(asset.getCurrent_loc()[1]+ "," + asset.getCurrent_loc()[0]);
            coordinates.appendChild(textNode);
            PFpoint.appendChild(coordinates);

            PFplacemark.appendChild(PFpoint);
        }
        catch(Exception egeo){
            log.error("error exporting asset position to kml");egeo.printStackTrace();
        }
    }

    /* Plot Range estimates */
    public static void exportMeasurementCircles(Document doc, Element dnode, GeoMission geoMission) {
        Set keys = geoMission.circlesToShow;
        log.trace("# measurement circles: "+keys.size());
        Iterator keyIt = keys.iterator();
        while (keyIt.hasNext()) {
            Long ele = (Long) keyIt.next();
            log.trace("Creating kml for ele: "+ele);
            List<double[]> circle = (ArrayList<double[]>) geoMission.getObservations().get(ele).getCircleGeometry();

            log.trace("This asset has measurement circle data? "+!circle.isEmpty());
            if (!circle.isEmpty())
            {
                try {
                    Element measPlacemark = doc.createElement("Placemark");
                    dnode.appendChild(measPlacemark);

                    Element name = doc.createElement("name");
                    name.appendChild(doc.createTextNode("RANGE:"+geoMission.getObservations().get(ele).getAssetId()));
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
                                circleCoords.appendChild(doc.createTextNode(point[1] + "," + point[0] + ",0 \n"));
                            }
                        }
                    } catch (Exception esynch) {
                        log.trace("error iterating over measurement circle, b/c it being updated");
                    }
                    line.appendChild(circleCoords);

                    measPlacemark.appendChild(line);
                } catch (Exception emeas) {
                    log.error("error exporting meas circle to kml");
                    emeas.printStackTrace();
                }
            }
        }
    }

    /* Plot TDOA measurements */
    public static void exportMeasurementHyperbolas(Document doc, Element dnode, GeoMission geoMission) {
        Set keys = geoMission.hyperbolasToShow;
        log.trace("# measurement hyperbolas: "+keys.size());
        Iterator keyIt = keys.iterator();
        while (keyIt.hasNext()) {
            Long ele = (Long) keyIt.next();
            log.trace("Creating kml for ele: "+ele);
            List<double[]> hyperbola = (ArrayList<double[]>) geoMission.getObservations().get(ele).getHyperbolaGeometry();

            log.trace("This asset has measurement hyperbola data? "+!hyperbola.isEmpty());
            if (!hyperbola.isEmpty())
            {
                try {
                    Element measPlacemark = doc.createElement("Placemark");
                    dnode.appendChild(measPlacemark);

                    Element name = doc.createElement("name");
                    name.appendChild(doc.createTextNode("TDOA:"+geoMission.getObservations().get(ele).getAssetId()+"/"+geoMission.getObservations().get(ele).getAssetId_b()));
                    measPlacemark.appendChild(name);

                    Element styleUrl = doc.createElement("styleUrl");
                    styleUrl.appendChild(doc.createTextNode("#measurementStyle"));

                    measPlacemark.appendChild(styleUrl);

                    Element line = doc.createElement("LineString");
                    Element circleCoords = doc.createElement("coordinates");

                    Iterator hyperbolaPoints = hyperbola.iterator();

                    try {
                        synchronized (hyperbolaPoints) {
                            while (hyperbolaPoints.hasNext()) {
                                double[] point = (double[]) hyperbolaPoints.next();
                                circleCoords.appendChild(doc.createTextNode(point[1] + "," + point[0] + ",0 \n"));
                            }
                        }
                    } catch (Exception esynch) {
                        log.debug("error iterating over measurement hyperbola, b/c it being updated");
                    }
                    line.appendChild(circleCoords);

                    measPlacemark.appendChild(line);
                } catch (Exception emeas) {
                    log.error("error exporting meas hyperbola to kml");
                    emeas.printStackTrace();
                }
            }
        }
    }

    /* Plot AOA measurements */
    public static void exportMeasurementDirections(Document doc, Element dnode, GeoMission geoMission) {
        Set keys = geoMission.linesToShow;
        log.trace("# measurement lines: "+keys.size());
        Iterator keyIt = keys.iterator();
        while (keyIt.hasNext()) {
            Long ele = (Long) keyIt.next();
            log.trace("Creating AOA kml for ele: "+ele);
            List<double[]> aoa_line = (ArrayList<double[]>) geoMission.getObservations().get(ele).getLineGeometry();

            if (!aoa_line.isEmpty())
            {
                try {
                    log.trace("CREATING NEW MEAS Line in KML");

                    Element measPlacemark = doc.createElement("Placemark");
                    dnode.appendChild(measPlacemark);

                    Element name = doc.createElement("name");
                    name.appendChild(doc.createTextNode("AOA:"+geoMission.getObservations().get(ele).getAssetId()));
                    measPlacemark.appendChild(name);

                    Element styleUrl = doc.createElement("styleUrl");
                    styleUrl.appendChild(doc.createTextNode("#measurementStyle"));

                    measPlacemark.appendChild(styleUrl);

                    Element line = doc.createElement("LineString");
                    Element circleCoords = doc.createElement("coordinates");

                    Iterator linePoints = aoa_line.iterator();

                    try {
                        synchronized (linePoints) {
                            while (linePoints.hasNext()) {
                                double[] point = (double[]) linePoints.next();
                                circleCoords.appendChild(doc.createTextNode(point[1] + "," + point[0] + ",0 \n"));
                            }
                        }
                    } catch (Exception esynch) {
                        log.trace("error iterating over measurement line, b/c it being updated");
                    }
                    line.appendChild(circleCoords);

                    measPlacemark.appendChild(line);
                } catch (Exception emeas) {
                    log.error("error exporting meas line to kml");
                    emeas.printStackTrace();
                }
            }
        }
    }
}

