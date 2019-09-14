package tech.tgo.fuzer;

import tech.tgo.fuzer.model.GeoMission;
import tech.tgo.fuzer.model.Observation;
import tech.tgo.fuzer.model.ObservationType;
import tech.tgo.fuzer.thread.AlgorithmEKF;
import tech.tgo.fuzer.util.FilesystemHelpers;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FuzerProcess implements Serializable {

    FuzerListener actionListener;

    //List<Observation> observations = new ArrayList<Observation>();
    Map<String,Observation> observations = new HashMap<String,Observation>();

    GeoMission geoMission;

    AlgorithmEKF algorithmEKF;

    public FuzerProcess(FuzerListener actionListener) {
        this.actionListener = actionListener;
    }

    public void configure(GeoMission geoMission) throws Exception {
        this.geoMission = geoMission;

        if (geoMission.isOutputKml()) {
            System.out.println("Creating new file as: "+FilesystemHelpers.workingDirectory+"output/"+geoMission.getOutputKmlFilename());
            File kmlOutput = new File(FilesystemHelpers.workingDirectory+"output/"+geoMission.getOutputKmlFilename());
            kmlOutput.createNewFile();
        }
    }

    public void addObservation(Observation obs) throws Exception {
        // TODO, input validation

        // Restricted to hold only one observation per asset per type
        this.observations.put(obs.getAssetId()+","+obs.getObservationType().name(), obs);

        if (this.geoMission.showMeas)
        {
            /* RANGE MEASUREMENT */
            if (obs.getObservationType().equals(ObservationType.range)) {
                List<double[]> measurementCircle = new ArrayList<double[]>();
                for (double theta = (1 / 2) * Math.PI; theta <= (5 / 2) * Math.PI; theta += 0.2) {
                    UTMRef utmMeas = new UTMRef(obs.getRange() * Math.cos(theta) + obs.getX(), obs.getRange() * Math.sin(theta) + obs.getY(), this.geoMission.getLatZone(), this.geoMission.getLonZone());
                    LatLng ltln = utmMeas.toLatLng();
                    System.out.println("TESTING WHILE UPDATING CONT: lat:" + ltln.getLat() + ", " + ltln.getLng());
                    double[] measPoint = {ltln.getLat(), ltln.getLng()};
                    measurementCircle.add(measPoint);
                }
                this.geoMission.measurementMetres.put(obs.getAssetId(), obs.getRange());
                this.geoMission.measurementCircles.put(obs.getAssetId(), measurementCircle);
            }

            /* TDOA MEASUREMENT */
            if (obs.getObservationType().equals(ObservationType.tdoa)) {

            }

            /* AOA MEASUREMENT */
            if (obs.getObservationType().equals(ObservationType.aoa)) {

            }
        }

        // trigger the computation again - for tracking only
        restart();
    }

    public void restart() {
        //stop
        //algorithmEKF.interrupt();
        algorithmEKF.stopThread();

        start();
    }

    public void start() {
        // stop currently active thread if any, but preserve its state if it was running

        // run a convergence thread here using current obs
        algorithmEKF = new AlgorithmEKF(this.actionListener, this.observations, this.geoMission);
        algorithmEKF.start();

        // TODO, also need a method for managing/expiring old observations
    }
}