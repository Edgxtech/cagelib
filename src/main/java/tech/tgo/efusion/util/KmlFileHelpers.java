package tech.tgo.efusion.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import tech.tgo.efusion.compute.FilterStateDTO;
import tech.tgo.efusion.model.GeoMission;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;

/**
 * KML Filesystem I/O (Non-Static applications)
 * @author Timothy Edge (timmyedge)
 */
public class KmlFileHelpers {

    private static final Logger log = LoggerFactory.getLogger(KmlFileHelpers.class);

    TransformerFactory tranFactory;
    Transformer aTransformer;
    Document doc;
    Element coords1;
    Element coords2;
    Element coords3;

    public KmlFileHelpers() {
        try {
            tranFactory = TransformerFactory.newInstance();
            aTransformer = tranFactory.newTransformer();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void provisionFilterStateExport() {
        try
        {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            doc = builder.newDocument();
            Element root = doc.createElement("kml");
            root.setAttribute("xmlns", "http://www.opengis.net/kml/2.2");
            root.setAttribute("xmlns:gx","http://www.google.com/kml/ext/2.2");
            doc.appendChild(root);

            Element dnode = doc.createElement("Document");
            root.appendChild(dnode);

            /* High */
            Element filterStatePlacemark = doc.createElement("Placemark");
            dnode.appendChild(filterStatePlacemark);

            Element name = doc.createElement("name");
            name.appendChild(doc.createTextNode("High Residual"));
            filterStatePlacemark.appendChild(name);

            Element style = doc.createElement("Style");
            style.setAttribute("id", "style1");
            Element measStyle = doc.createElement("LineStyle");
            Element color = doc.createElement("color");
            color.appendChild(doc.createTextNode("ffffA500"));
            Element width = doc.createElement("width");
            width.appendChild(doc.createTextNode("2"));
            measStyle.appendChild(color);
            measStyle.appendChild(width);
            style.appendChild(measStyle);
            dnode.appendChild(style);

            Element styleUrl = doc.createElement("styleUrl");
            styleUrl.appendChild(doc.createTextNode("#style1"));
            filterStatePlacemark.appendChild(styleUrl);

            Element line = doc.createElement("LineString");
            coords1 = doc.createElement("coordinates");
            line.appendChild(coords1);
            filterStatePlacemark.appendChild(line);


            /* Medium */
            Element filterStatePlacemark2 = doc.createElement("Placemark");
            dnode.appendChild(filterStatePlacemark2);

            Element name2 = doc.createElement("name");
            name2.appendChild(doc.createTextNode("Medium Residual"));
            filterStatePlacemark2.appendChild(name2);

            style = doc.createElement("Style");
            style.setAttribute("id", "style2");
            measStyle = doc.createElement("LineStyle");
            color = doc.createElement("color");
            color.appendChild(doc.createTextNode("ffffD500"));
            measStyle.appendChild(color);
            measStyle.appendChild(width);
            style.appendChild(measStyle);
            dnode.appendChild(style);

            Element styleUrl2 = doc.createElement("styleUrl");
            styleUrl2.appendChild(doc.createTextNode("#style2"));
            filterStatePlacemark2.appendChild(styleUrl2);

            line = doc.createElement("LineString");
            coords2 = doc.createElement("coordinates");
            line.appendChild(coords2);
            filterStatePlacemark2.appendChild(line);


            /* LOW */
            Element filterStatePlacemark3 = doc.createElement("Placemark");
            dnode.appendChild(filterStatePlacemark3);

            Element name3 = doc.createElement("name");
            name3.appendChild(doc.createTextNode("Low Residual"));
            filterStatePlacemark3.appendChild(name3);

            style = doc.createElement("Style");
            style.setAttribute("id", "style3");
            measStyle = doc.createElement("LineStyle");
            color = doc.createElement("color");
            color.appendChild(doc.createTextNode("fffgG500"));
            measStyle.appendChild(color);
            measStyle.appendChild(width);
            style.appendChild(measStyle);
            dnode.appendChild(style);

            Element styleUrl3 = doc.createElement("styleUrl");
            styleUrl3.appendChild(doc.createTextNode("#style3"));
            filterStatePlacemark3.appendChild(styleUrl3);

            line = doc.createElement("LineString");
            coords3 = doc.createElement("coordinates");
            line.appendChild(coords3);
            filterStatePlacemark3.appendChild(line);


            Source src = new DOMSource(doc);
            Result dest = new StreamResult(new File("output/filterState.kml"));
            aTransformer.transform(src, dest);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            log.error(e.getMessage());
        }
    }

    public void exportAdditionalFilterState(GeoMission geoMission, FilterStateDTO filterStateDTO, double residual) {

        try {
            double[] latLon = Helpers.convertUtmNthingEastingToLatLng(filterStateDTO.getXk().getEntry(0),filterStateDTO.getXk().getEntry(1), geoMission.getLatZone(), geoMission.getLonZone());

            if (residual > 15) {
                coords1.appendChild(doc.createTextNode(latLon[1] + "," + latLon[0] + ",0 \n"));
            }
            else if (residual > 5 && residual <= 15) {
                coords2.appendChild(doc.createTextNode(latLon[1] + "," + latLon[0] + ",0 \n"));
            }
            if (residual <= 5) {
                coords3.appendChild(doc.createTextNode(latLon[1] + "," + latLon[0] + ",0 \n"));
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            log.error(e.getMessage());
        }
    }

    public void writeCurrentExports(GeoMission geoMission) {
        try {
            Source src = new DOMSource(doc);
            Result dest = new StreamResult(new File("output/filterState.kml"));
            aTransformer.transform(src, dest);
        }
        catch(Exception e) {
            e.printStackTrace();
            log.error(e.getMessage());
        }
    }
}
