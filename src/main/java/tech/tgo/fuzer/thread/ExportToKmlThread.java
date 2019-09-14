//package tech.tgo.fuzer.thread;
//
///**
// * Borrowed class
// * @author (timmyedge)
// */
//
//        import java.io.*;
//        import java.io.IOException;
//        import java.sql.ResultSet;
//        import java.sql.Statement;
//        import java.util.ArrayList;
//        import java.util.Enumeration;
//        import java.util.Hashtable;
//        import java.util.Iterator;
//        import java.util.Set;
//        import java.util.concurrent.CopyOnWriteArrayList;
//        import javax.xml.parsers.DocumentBuilder;
//        import javax.xml.parsers.DocumentBuilderFactory;
//        import javax.xml.transform.Result;
//        import javax.xml.transform.Source;
//        import javax.xml.transform.Transformer;
//        import javax.xml.transform.TransformerFactory;
//        import javax.xml.transform.dom.DOMSource;
//        import javax.xml.transform.stream.StreamResult;
//        import org.w3c.dom.Document;
//        import org.w3c.dom.Element;
//        import org.w3c.dom.Text;
//
//public class ExportToKmlThread extends Thread{
//
//    public String workingDirectoryStatic;
//
//    public String geoID;
//
//    public IGuiAppStatic m_app;
//    public String id;
//    public String time;
//    public String alt;
//    public String lat;
//    public String lng;
//    //public float lat;
////public float lng;
//    public String type;
//    public boolean stopped = false;
//    public boolean runOnce = true;
//    //public static void main(String[]args ){
////public ArrayList<String> container;
//    public Hashtable<String,Element> container;
//    public ArrayList<String> measCircleContainer;
//    public ArrayList<String> cepCircleContainer;
//
//    public ArrayList<String> geoPoints;
//
//    public Iterator geoMsnIterator;
//
//    public ExportToKMLGeoData(IGuiAppStatic app, boolean onceOrMany, String geoID)
//    {
//        m_app = app;
//        runOnce = onceOrMany;
//        this.geoID = geoID;
//    }
//
//    public void run()
//    {
//        workingDirectoryStatic = m_app.getWorkingDirectory();
//        //container = new ArrayList<String>();
//        container = new Hashtable<String,Element>();
//        measCircleContainer = new ArrayList<String>();
//        cepCircleContainer = new ArrayList<String>();
//        geoPoints = new ArrayList<String>();
//        Statement stmt;
//        ResultSet rs;
////    try
////    {
////
////    }
////    catch (Exception e1){e1.getMessage();}
//        //ExportToKML KML = new ExportToKML();
//
//        System.out.println("started KML EXPORT THREAD");
//        try
//        {
//            //Class.forName("com.mysql.jdbc.Driver");
//            //String url = "jdbc:mysql://localhost:3306/ghost";
//            //Connection con = DriverManager.getConnection(url, "root", "timbo");
//            //stmt = con.createStatement();
//
//            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
//            DocumentBuilder builder = factory.newDocumentBuilder();
//            TransformerFactory tranFactory = TransformerFactory.newInstance();
//            Transformer aTransformer = tranFactory.newTransformer();
//
//            Document doc = builder.newDocument();
//            Element root = doc.createElement("kml");
//            root.setAttribute("xmlns", "http://earth.google.com/kml/2.1");
//            doc.appendChild(root);
//
//            Element dnode = doc.createElement("Document");
//            root.appendChild(dnode);
//
////        Element rstyle = doc.createElement("Style");
////        rstyle.setAttribute("id", "restaurantStyle");
////
////        Element ristyle = doc.createElement("IconStyle");
////        ristyle.setAttribute("id", "restaurantIcon");
////
////        Element ricon = doc.createElement("Icon");
////
////        Element riconhref = doc.createElement("href");
////        riconhref.appendChild(doc.createTextNode("http://maps.google.com/mapfiles/kml/pal2/icon62.png"));
////
////        rstyle.appendChild(ristyle);
////        ricon.appendChild(riconhref);
////        ristyle.appendChild(ricon);
////        dnode.appendChild(rstyle);
////
////        Element bstyle = doc.createElement("Style");
////        bstyle.setAttribute("id", "barStyle");
////        Element bistyle = doc.createElement("IconStyle");
////        bistyle.setAttribute("id", "barIcon");
////        Element bicon = doc.createElement("Icon");
////        Element biconhref = doc.createElement("href");
////        biconhref.appendChild(doc.createTextNode("http://maps.google.com/mapfiles/kml/pal2/icon26.png"));
////
////        bstyle.appendChild(bistyle);
////        bicon.appendChild(biconhref);
////        bistyle.appendChild(bicon);
////        dnode.appendChild(bstyle);
//
//            while (!stopped)
//            {
//                Hashtable<String,GeoMission> geoMissions = (Hashtable<String,GeoMission>)m_app.getGeoMissions();
//                Set set = geoMissions.keySet();
//                geoMsnIterator = set.iterator();
//                while (geoMsnIterator.hasNext())
//                {
//                    GeoMission gm = (GeoMission)geoMissions.get((String)geoMsnIterator.next());
//
//                    /////// PLOT measurement circles
//                    ///// TODO, plot measurementCircles, if !measurementCircles.isEmpty()
//                    //Hashtable measurementCircles = gm.measurementCircles;
//
//                    //Enumeration circles = measurementCircles.elements();  this was a nice method, but I need the key IOT put outer and coords into hashtable later..
//
//                    Set keys = gm.measurementCircles.keySet();
//                    Iterator keyIt = keys.iterator();
//                    while(keyIt.hasNext())
//                    {
//                        String device = (String)keyIt.next();
//                        ArrayList<double[]> circle = (ArrayList<double[]>)gm.measurementCircles.get(device);
//                        //System.out.println("HERE::"+device+"firstLAT::"+circle.get(0)[0]);
//
//                        if (!circle.isEmpty())   /// This will only be the case when user has ticked 'show Measurements' checkbox on gmt.
//                        {
//                            //if (!(m_app.getMobileDevice(device).currentUTMEasting==0 && m_app.getMobileDevice(device).currentUTMNorthing==0))
//                            //{
//                            try
//                            {
//                                //System.out.println("circle toString:"+circle.toString());
//                                if (!measCircleContainer.contains(device))
//                                {
//                                    System.out.println("CREATING NEW POLYGON");
//                                    /// create new polygon
//                                    Element style = doc.createElement("Style");
//                                    style.setAttribute("id", "measurementStyle");
//
//                                    //Element polyStyle = doc.createElement("PolyStyle");
//                                    Element measStyle = doc.createElement("LineStyle");
//                                    Element color = doc.createElement("color");
//                                    color.appendChild(doc.createTextNode("7fcccc00"));
//
//                                    Element width = doc.createElement("width");
//                                    width.appendChild(doc.createTextNode("3"));
//
//                                    //polyStyle.appendChild(color);
//                                    measStyle.appendChild(color);
//                                    measStyle.appendChild(width);
//
//                                    //style.appendChild(polyStyle);
//                                    style.appendChild(measStyle);
//
//                                    dnode.appendChild(style);
//
//                                    ///// create placemark to represent circle polygon
//                                    //Element polyPlacemark = doc.createElement("Placemark");
//                                    //dnode.appendChild(polyPlacemark);
//                                    Element measPlacemark = doc.createElement("Placemark");
//                                    dnode.appendChild(measPlacemark);
//
//                                    Element name = doc.createElement("name");
//                                    name.appendChild(doc.createTextNode(device));
//                                    //polyPlacemark.appendChild(name);
//                                    measPlacemark.appendChild(name);
//
//                                    Element styleUrl = doc.createElement("styleUrl");
//                                    styleUrl.appendChild(doc.createTextNode("#measurementStyle"));
//
//                                    //polyPlacemark.appendChild(styleUrl);
//                                    measPlacemark.appendChild(styleUrl);
//
//                                    //Element polygon = doc.createElement("Polygon");
//
//
//                                    //Element outer = doc.createElement("outerBoundaryIs");
//
//                                    //Element outerRing = doc.createElement("LinearRing");
//                                    Element line = doc.createElement("LineString");
//                                    Element circleCoords = doc.createElement("coordinates");
//                                    Element oldCircleCoords = doc.createElement("coordinates");
//                                    //KMLCircle kmlCircle = new KMLCircle(outerRing,circleCoords);
//
//                                    //gm.measCircleCoords.put(device,doc.createElement("coordinates"));
//
//                                    /// TODO, inner circle or create line string rather than polygon?
//
//                                    Iterator circlePoints = circle.iterator();
//
//                                    try
//                                    {
//                                        synchronized(circlePoints)
//                                        {
//                                            while (circlePoints.hasNext())
//                                            {
//                                                double[] point = (double[])circlePoints.next();
//                                                //System.out.println("TESTING   lat:"+point[0]+". lng:"+point[1]);
//                                                circleCoords.appendChild(doc.createTextNode(point[1]+","+point[0]+",0 \n"));
//                                            }
//                                        }
//                                    }
//                                    catch(Exception esynch){System.out.println("error iterating over measurement circle, b/c it being updated");
//                                        KMLCircle kmlCircle = new KMLCircle(line,oldCircleCoords);
//                                        gm.measKMLCircles.put(device, kmlCircle);}
//                                    //outerRing.appendChild(circleCoords);
//                                    line.appendChild(circleCoords);
//
//                                    KMLCircle kmlCircle = new KMLCircle(line,circleCoords);
//                                    gm.measKMLCircles.put(device, kmlCircle);
//                                    //outer.appendChild(outerRing);
//                                    //polygon.appendChild(outer);
//
//                                    //polyPlacemark.appendChild(polygon);
//                                    measPlacemark.appendChild(line);
//
//                                    measCircleContainer.add(device);
//                                }
//                                else
//                                {
//                                    /// update old polygon
//                                    System.out.println("DEVICE="+device);
//                                    //Element outerRing  = gm.measKMLCircles.get(device).measurementOuterRing;
//                                    //Element outerRing  = gm.measKMLCircles.get(device).measurementLine;
//                                    Element line  = gm.measKMLCircles.get(device).measurementLine;
//                                    Element oldCircleCoords = gm.measKMLCircles.get(device).measCircleCoords;
//                                    //Element oldCircleCoords = gm.measKMLCircles.get(device).measCircleCoords;
//                                    //outerRing.removeChild(circleCoords);
//                                    line.removeChild(oldCircleCoords);
//                                    Element circleCoords = doc.createElement("coordinates");
//
//
//                                    Iterator circlePoints = circle.iterator();
//
//                                    try
//                                    {
//                                        synchronized(circlePoints)
//                                        {
//                                            while (circlePoints.hasNext())
//                                            {
//                                                double[] point = (double[])circlePoints.next();
//
//                                                //System.out.println("TESTING   lat:"+point[0]+". lng:"+point[1]);
//
//                                                circleCoords.appendChild(doc.createTextNode(point[1]+","+point[0]+",0 \n"));
//                                            }
//                                        }
//                                    }
//                                    catch(Exception esynch){System.out.println("error iterating over measurement circle, b/c it being updated");
//                                        KMLCircle kmlCircle = new KMLCircle(line,oldCircleCoords);
//                                        gm.measKMLCircles.put(device, kmlCircle);}
//                                    //outerRing.appendChild(circleCoords);
//                                    line.appendChild(circleCoords);
//
//                                    KMLCircle kmlCircle = new KMLCircle(line,circleCoords);
//                                    gm.measKMLCircles.put(device, kmlCircle);
//                                }
//                            }
//                            catch(Exception emeas){System.out.println("error exporting meas circle to kml");emeas.printStackTrace();}
//                            //}
//
//                        }
//
//                    }
//
//                    Iterator it = gm.targets.keySet().iterator();
//                    //Iterator it = gm.geoResults.keySet().iterator();
//                    while (it.hasNext())
//                    {
//                        String target = (String)it.next();
//                        Target Target = (Target)gm.targets.get(target);
//
//                        double[] result = gm.targets.get(target).geoResult;
//
//                        //System.out.println("SHOW CEPS?:"+gm.showCEPs);
//                        //if (!cepCircle.isEmpty())                            //// i.e. when user has ticked show CEP(s)
//                        if (gm.showCEPs)
//                        {
//                            try
//                            {
//                                ///////////// PLOT CEP(s) and location results
//                                ////// TODO, pull out cepCircles for each target
//                                ArrayList<double[]> cepCircle = gm.targets.get(target).geoCEPCircle;
//                                //CopyOnWriteArrayList<double[]> cepCirc = (CopyOnWriteArrayList)gm.targets.get(target).geoCEPCircle;//new CopyOnWriteArrayList<double[]>();
//                                //synchronized(cepCircle)
//                                //{
//                                if (!cepCircleContainer.contains(target))
//                                {
//                                    System.out.println("CREATING NEW CEP POLYGON");
//                                    /// create new polygon
//                                    Element style = doc.createElement("Style");
//                                    style.setAttribute("id", "cepStyle");
//
//                                    Element polyStyle = doc.createElement("PolyStyle");
//                                    Element color = doc.createElement("color");
//                                    color.appendChild(doc.createTextNode("7f0000cc"));
//
//                                    //Element colorMode = doc.createElement("colorMode");
//                                    //colorMode.appendChild(doc.createTextNode("random"));
//
//                                    polyStyle.appendChild(color);
//                                    //polyStyle.appendChild(colorMode);
//                                    style.appendChild(polyStyle);
//
//                                    dnode.appendChild(style);
//                                    ///// create placemark to represent circle polygon
//                                    Element polyPlacemark = doc.createElement("Placemark");
//                                    dnode.appendChild(polyPlacemark);
//
//                                    Element name = doc.createElement("name");
//                                    name.appendChild(doc.createTextNode(target+":cep"));
//                                    polyPlacemark.appendChild(name);
//
//                                    Element styleUrl = doc.createElement("styleUrl");
//                                    styleUrl.appendChild(doc.createTextNode("#cepStyle"));
//
//                                    polyPlacemark.appendChild(styleUrl);
//
//                                    Element polygon = doc.createElement("Polygon");
//
//                                    Element outer = doc.createElement("outerBoundaryIs");
//                                    Target.cepOuterRing = doc.createElement("LinearRing");
//                                    Target.cepCircleCoords = doc.createElement("coordinates");
//
//                                    /// TODO, inner circle or create line string rather than polygon?
//
//                                    Iterator circlePoints = cepCircle.iterator();
//                                    while (circlePoints.hasNext())
//                                    {
//                                        double[] point = (double[])circlePoints.next();
//
//                                        Target.cepCircleCoords.appendChild(doc.createTextNode(point[1]+","+point[0]+",0 \n"));
//                                    }
//                                    Target.cepOuterRing.appendChild(Target.cepCircleCoords);
//
//
//                                    outer.appendChild(Target.cepOuterRing);
//                                    polygon.appendChild(outer);
//
//                                    polyPlacemark.appendChild(polygon);
//
//                                    cepCircleContainer.add(target);   /// tODO, possibly put the target id in here instead - to save memory
//                                }
//                                else
//                                {
//                                    /// update old polygon
//                                    Target.cepOuterRing.removeChild(Target.cepCircleCoords);
//                                    Element oldCircleCoords = Target.cepCircleCoords;
//                                    Element cepCircleCoords = doc.createElement("coordinates");
//
//                                    Iterator circlePoints = cepCircle.iterator();
//                                    try
//                                    {
//                                        synchronized(circlePoints)
//                                        {
//                                            while (circlePoints.hasNext())
//                                            {
//                                                double[] point = (double[])circlePoints.next();
//
//                                                cepCircleCoords.appendChild(doc.createTextNode(point[1]+","+point[0]+",0 \n"));
//                                            }
//                                            Target.cepOuterRing.appendChild(cepCircleCoords);
//                                            Target.cepCircleCoords = cepCircleCoords;
//                                        }
//                                    }
//                                    catch(Exception esynch)
//                                    {System.out.println("error iterating through circle points, I'll skip this circle export and hold onto the old circle, should be ok next time(?) TBC");
//                                        Target.cepOuterRing.appendChild(oldCircleCoords);
//                                        Target.cepCircleCoords = oldCircleCoords;}
//
//                                }
//                                //}
//                            }
//                            catch(Exception ecep){System.out.println("error exporting cep circle to kml"); ecep.printStackTrace();}
//                        }
//
//                        /////////////////////////////////////////////////
//                        /////////// PLOT the geo result /////////////////
//                        if (gm.showGEOs)
//                        {
//                            try
//                            {
//                                if (!container.containsKey(target))
//                                {
//                                    System.out.println("init GEO DATA");
//
//                                    Element crosshairStyle = doc.createElement("Style");
//                                    crosshairStyle.setAttribute("id", "crosshairStyle");
//
//                                    Element crosshairIconStyle = doc.createElement("IconStyle");
//                                    crosshairIconStyle.setAttribute("id", "crosshairIconStyle");
//
//                                    Element crosshairIcon = doc.createElement("Icon");
//
//                                    Element crosshairIconHref = doc.createElement("href");
//                                    crosshairIconHref.appendChild(doc.createTextNode(workingDirectoryStatic+"styles/crosshairs3.png"));
//
//                                    crosshairStyle.appendChild(crosshairIconStyle);
//                                    crosshairIcon.appendChild(crosshairIconHref);
//                                    crosshairIconStyle.appendChild(crosshairIcon);
//                                    dnode.appendChild(crosshairStyle);
//                                    ///////////////////////////////////////////////
//
//                                    Element PFplacemark = doc.createElement("Placemark");
//                                    dnode.appendChild(PFplacemark);
//
//                                    Element name = doc.createElement("name");
//                                    name.appendChild(doc.createTextNode(target));
//                                    PFplacemark.appendChild(name);
//
//                                    PFplacemark.appendChild(crosshairStyle);
//
//                                    Element descrip = doc.createElement("description");
//                                    descrip.appendChild(doc.createTextNode("description? timeLastSeen?"));
//                                    PFplacemark.appendChild(descrip);
//
//                                    Element PFpoint = doc.createElement("Point");
//                                    gm.targets.get(target).coordinates = doc.createElement("coordinates");
//                                    Text textNode = doc.createTextNode(result[1]+ "," + result[0]);
//
//                                    gm.targets.get(target).coordTextNode = textNode;
//                                    //gm.targets.get(target).geoResult = result;
//
//                                    gm.targets.get(target).coordinates.appendChild(gm.targets.get(target).coordTextNode);
//                                    PFpoint.appendChild(gm.targets.get(target).coordinates);
//
//                                    PFplacemark.appendChild(PFpoint);
//
//                                    container.put(target,PFplacemark);
//                                }
//                                else
//                                {
//                                    //System.out.println("GEO DATA:"+this.id+":"+this.lat+":"+this.lng);
//
//                                    gm.targets.get(target).coordinates.removeChild(gm.targets.get(target).coordTextNode);
//
//                                    gm.targets.get(target).coordTextNode = doc.createTextNode(result[1]+ "," + result[0]);
//                                    gm.targets.get(target).coordinates.appendChild(gm.targets.get(target).coordTextNode);
//
//                                }
//                            }
//                            catch(Exception egeo){System.out.println("error exporting geo position to kml");egeo.printStackTrace();}
//                        }
//
//                    }
//                }
//
//                Source src = new DOMSource(doc);
//                Result dest = new StreamResult(new File(workingDirectoryStatic+"kmlout/GeoPlaceMarkers.kml"));
//                aTransformer.transform(src, dest);
//
//                if (runOnce)
//                    break;
//
//                System.out.println("[KML Exp Geo] updated map data.....");
//                Thread.currentThread().sleep(500);                                    //// sleep 5 seconds
//            }
//            System.out.println("[S] finished KML Geo export");
//        }
//        catch (Exception e)
//        {
//            System.out.println(e.getMessage());}
//    }
//
//
//}
