package tech.tgo.fuzer;

/**
 * Geolocation fusion and tracking, using custom extended kalman filter implementation
 *
 * @author Timothy Edge (timmyedge)
 */
public interface FuzerListener {
    public void result(String geoId, double a, double b, double c, double d);
}