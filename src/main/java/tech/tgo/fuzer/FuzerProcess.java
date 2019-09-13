package tech.tgo.fuzer;

import tech.tgo.fuzer.model.FuzerConfig;
import tech.tgo.fuzer.model.Observation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FuzerProcess implements Serializable {

    FuzerListener actionListener;

    List<Observation> observations = new ArrayList<Observation>();

    FuzerConfig fuzerConfig;

    AlgorithmEKF algorithmEKF;

    public FuzerProcess(FuzerListener actionListener) {
        this.actionListener = actionListener;
    }

    public void configure(FuzerConfig fuzerConfig) {
        this.fuzerConfig = fuzerConfig;
    }

    public void addObservation(Observation obs) {
        this.observations.add(obs);

        // trigger the computation again - for tracking only
        restart();
    }

    public void restart() {
        //stop
        algorithmEKF.interrupt();

        start();
    }

    public void start() {
        // stop currently active thread if any, but preserve its state if it was running

        // run a convergence thread here using current obs
        algorithmEKF = new AlgorithmEKF(this.actionListener, this.observations, this.fuzerConfig);
        algorithmEKF.start();

        // TODO, also need a method for managing/expiring old observations
    }
}