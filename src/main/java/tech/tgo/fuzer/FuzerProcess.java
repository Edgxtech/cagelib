package tech.tgo.fuzer;

import tech.tgo.fuzer.model.*;
import tech.tgo.fuzer.thread.AlgorithmEKF;
import tech.tgo.fuzer.util.FilesystemHelpers;
import tech.tgo.fuzer.util.Helpers;
import uk.me.jstott.jcoord.LatLng;
import uk.me.jstott.jcoord.UTMRef;

import java.io.File;
import java.io.Serializable;
import java.util.*;

public class FuzerProcess implements Serializable {

    FuzerListener actionListener;

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
        System.out.println("Adding obs as key: "+obs.getAssetId()+","+obs.getObservationType().name());
        this.observations.put(obs.getAssetId()+","+obs.getObservationType().name(), obs);

        UTMRef assetUtmLoc = new UTMRef(obs.getX(), obs.getY(), this.geoMission.getLatZone(), this.geoMission.getLonZone());
        LatLng asset_ltln = assetUtmLoc.toLatLng();
        Asset asset = new Asset(obs.getAssetId(),new double[]{asset_ltln.getLat(),asset_ltln.getLng()});
        this.geoMission.getAssets().put(obs.getAssetId(),asset);

        if (obs.getObservationType().equals(ObservationType.tdoa)) {
            // There is a second asset to register its location
            assetUtmLoc = new UTMRef(obs.getXb(), obs.getYb(), this.geoMission.getLatZone(), this.geoMission.getLonZone());
            asset_ltln = assetUtmLoc.toLatLng();
            Asset asset_b = new Asset(obs.getAssetId_b(),new double[]{asset_ltln.getLat(),asset_ltln.getLng()});
            this.geoMission.getAssets().put(obs.getAssetId_b(),asset_b);
        }

        if (this.geoMission.showMeas)
        {
            /* RANGE MEASUREMENT */
            if (obs.getObservationType().equals(ObservationType.range)) {
                List<double[]> measurementCircle = new ArrayList<double[]>();
                for (double theta = (1 / 2) * Math.PI; theta <= (5 / 2) * Math.PI; theta += 0.2) {
                    UTMRef utmMeas = new UTMRef(obs.getRange() * Math.cos(theta) + obs.getX(), obs.getRange() * Math.sin(theta) + obs.getY(), this.geoMission.getLatZone(), this.geoMission.getLonZone());
                    LatLng ltln = utmMeas.toLatLng();
                    double[] measPoint = {ltln.getLat(), ltln.getLng()};
                    measurementCircle.add(measPoint);
                }
                this.geoMission.measurementCircles.put(obs.getAssetId(), measurementCircle);
            }

            /* TDOA MEASUREMENT */
            if (obs.getObservationType().equals(ObservationType.tdoa)) {
                List<double[]> measurementHyperbola = new ArrayList<double[]>();
                double c = Math.sqrt(Math.pow((obs.getX()-obs.getXb()),2)+Math.pow((obs.getYb()-obs.getY()),2))/2;
                double a=(obs.getTdoa()* Helpers.SPEED_OF_LIGHT)/2; double b=Math.sqrt(Math.pow(c,2)-Math.pow(a,2));
                double ca = (obs.getXb()-obs.getX())/(2*c); double sa = (obs.getYb()-obs.getY())/(2*c); // COS and SIN of rot angle
                for (double t = -2; t<= 2; t += 0.1) {
                    double X = a*Math.cosh(t); double Y = b*Math.sinh(t); // Hyperbola branch
                    double x = (obs.getX()+obs.getXb())/2 + X*ca - Y*sa; //# Rotated and translated
                    double y = (obs.getY()+obs.getYb())/2 + X*sa + Y*ca;
                    UTMRef utmMeas = new UTMRef(x, y, this.geoMission.getLatZone(), this.geoMission.getLonZone());
                    LatLng ltln = utmMeas.toLatLng();
                    measurementHyperbola.add(new double[]{ltln.getLat(),ltln.getLng()});
                }
                this.geoMission.measurementHyperbolas.put(obs.getAssetId()+"/"+obs.getAssetId_b(), measurementHyperbola);
            }

            /* AOA MEASUREMENT */
            if (obs.getObservationType().equals(ObservationType.aoa)) {
                List<double[]> measurementLine = new ArrayList<double[]>();
                double b = obs.getY() - Math.tan(obs.getAoa())*obs.getX();
                for (double t = obs.getX()-2000; t<= obs.getX()+2000; t += 100) {
                    double y = Math.tan(obs.getAoa())*t + b;
                    UTMRef utmMeas = new UTMRef(t, y, this.geoMission.getLatZone(), this.geoMission.getLonZone());
                    LatLng ltln = utmMeas.toLatLng();
                    double[] measPoint = {ltln.getLat(), ltln.getLng()};
                    measurementLine.add(measPoint);
                }
                this.geoMission.measurementLines.put(obs.getAssetId(), measurementLine);
            }
        }

        // trigger the computation again - if tracking mission type
        if (this.geoMission.getFuzerMode().equals(FuzerMode.track)) {
            restart();
        }
    }

    public void restart() {
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