package tech.edgx.cage;

import tech.edgx.cage.compute.ComputeResults;

public interface CageListener {
    public void result(String geoId, double lat, double lon, double cep_elp_maj, double cep_elp_min, double cep_elp_rot);
    public void result(ComputeResults computeResults);
}