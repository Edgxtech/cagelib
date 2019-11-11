package tech.tgo.efusion;

/**
 * @author Timothy Edge (timmyedge)
 */
public interface EfusionListener {
    public void result(String geoId, double lat, double lon, double cep_elp_maj, double cep_elp_min, double cep_elp_rot);
}