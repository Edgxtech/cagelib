package tech.tgo.fuzer.util;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Timothy Edge (timmyedge)
 */
public class TestMaths {

    private static final Logger log = LoggerFactory.getLogger(TestMaths.class);

    @Test
    public void testEvectorFinder() {
        double[][] covMatrix = new double[][]{{1500, -50}, {50, 1900}};
        double[] evalues = Helpers.getEigenvalues(covMatrix);
        log.debug("Eigenvalues: "+evalues[0]+","+evalues[1]);

        double largestEvalue = Math.max(evalues[0],evalues[1]);
        log.debug("Largest evalue: "+largestEvalue);
        double smallestEvalue = Math.min(evalues[0],evalues[1]);
        double[] evector = Helpers.getEigenvector(covMatrix, largestEvalue);
        log.debug("Eigenvector: "+evector[0]+","+evector[1]);

        double rot = Math.atan(evector[1] / evector[0]);

        double major = 2*Math.sqrt(5.991*largestEvalue);
        double minor = 2*Math.sqrt(5.991*smallestEvalue);
        log.debug("Major: "+major+", minor: "+minor);

        exportTargetEstimationEllipse(-32,115.9, major, minor, rot);
    }

    public static void exportTargetEstimationEllipse(double lat, double lon, double major, double minor, double rot) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            TransformerFactory tranFactory = TransformerFactory.newInstance();
            Transformer aTransformer = tranFactory.newTransformer();

            Document doc = builder.newDocument();
            Element root = doc.createElement("kml");
            root.setAttribute("xmlns", "http://www.opengis.net/kml/2.2");
            root.setAttribute("xmlns:gx", "http://www.google.com/kml/ext/2.2");
            doc.appendChild(root);

            Element dnode = doc.createElement("Document");
            root.appendChild(dnode);

            Object[] latLonZones = Helpers.getUtmLatZoneLonZone(lat, lon);

            List<double[]> geometryCoords = new ArrayList<double[]>();

            double[] utm_target_loc = Helpers.convertLatLngToUtmNthingEasting(lat, lon);
            log.debug("UTM Target Loc: "+utm_target_loc[0]+", "+utm_target_loc[1]);

            double[][] M_rot = new double[][]{{Math.cos(rot), -Math.sin(rot)}, {Math.cos(rot), Math.sin(rot)}};

            for (double theta = (1 / 2) * Math.PI; theta <= (5 / 2) * Math.PI; theta += 0.2) {
                double a = major * Math.cos(theta);
                double b = minor * Math.sin(theta);
                log.debug("a: "+a+", b: "+b);

                double x = M_rot[0][0] * (a) + M_rot[0][1] * (b);
                double y = M_rot[1][0] * (a) + M_rot[1][1] * (b);

                UTMRef utmMeas = new UTMRef(x + utm_target_loc[1], y + utm_target_loc[0], (char) latLonZones[0], (int) latLonZones[1]);
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
            name.appendChild(doc.createTextNode("test:cep"));
            polyPlacemark.appendChild(name);

            Element styleUrl = doc.createElement("styleUrl");
            styleUrl.appendChild(doc.createTextNode("#cepStyle"));

            polyPlacemark.appendChild(styleUrl);

            Element polygon = doc.createElement("Polygon");

            Element outer = doc.createElement("outerBoundaryIs");
            Element cepOuterRing = doc.createElement("LinearRing");
            Element cepCircleCoords = doc.createElement("coordinates");

            Iterator circlePoints = geometryCoords.iterator();
            while (circlePoints.hasNext()) {
                double[] point = (double[]) circlePoints.next();

                cepCircleCoords.appendChild(doc.createTextNode(point[1] + "," + point[0] + ",0 \n"));
            }
            cepOuterRing.appendChild(cepCircleCoords);

            outer.appendChild(cepOuterRing);
            polygon.appendChild(outer);

            polyPlacemark.appendChild(polygon);

            Source src = new DOMSource(doc);
            Result dest = new StreamResult(new File("output/test.kml"));
            aTransformer.transform(src, dest);
        } catch (Exception e) {
            log.trace("error exporting cep circle to kml");
            e.printStackTrace();
        }
    }
}
