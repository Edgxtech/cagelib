package tech.tgo.fuzer.compute;

import org.apache.commons.math3.linear.RealVector;
import tech.tgo.fuzer.model.Observation;

public class FilterStateDTO {
    Observation obs;
    double f_est;
    RealVector innov;

    public FilterStateDTO(Observation obs, double f_est, RealVector innov) {
        this.obs = obs;
        this.f_est = f_est;
        this.innov = innov;
    }

    public Observation getObs() {
        return obs;
    }

    public void setObs(Observation obs) {
        this.obs = obs;
    }

    public double getF_est() {
        return f_est;
    }

    public void setF_est(double f_est) {
        this.f_est = f_est;
    }

    public RealVector getInnov() {
        return innov;
    }

    public void setInnov(RealVector innov) {
        this.innov = innov;
    }
}
