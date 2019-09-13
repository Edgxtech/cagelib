package tech.tgo.fuzer;

public interface FuzerListener {

    //m_app.giveGeoResult(geoID,Xk.getEntry(0),Xk.getEntry(1), Xk.getEntry(2), Xk.getEntry(3));
    public void result(String geoId, String target, double a, double b, double c, double d);

    public void result(Double val);
}