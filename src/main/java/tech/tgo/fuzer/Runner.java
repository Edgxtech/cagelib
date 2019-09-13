package tech.tgo.fuzer;

import tech.tgo.fuzer.model.FuzerConfig;
import tech.tgo.fuzer.model.FuzerMode;
import tech.tgo.fuzer.model.Observation;
import tech.tgo.fuzer.model.ObservationType;

public class Runner implements FuzerListener {

    public static void main(String[] args) {
        Runner runner = new Runner();
        runner.runFuzerTest();
    }

    public void runFuzerTest() {
        FuzerProcess fuzerProcess = new FuzerProcess(this);

        // config the process - Target, Type (Geo/Track), Tolerance's
        FuzerConfig fuzerConfig = new FuzerConfig();
        fuzerConfig.setFuzerMode(FuzerMode.fix);
        fuzerConfig.setTarget("RANDTGT"); // Set this in client logic
        fuzerConfig.setGeoId("RAND-GEOID"); // Set this in client logic
        fuzerProcess.configure(fuzerConfig);

        fuzerProcess.start();

        // Add occassional new measurements to trigger updates
        Observation obs = new Observation(115.95,-31.85);  // -31.891551,115.996399 PERTH AREA
        obs.setRange(1000.0);
        obs.setObservationType(ObservationType.range);
        fuzerProcess.addObservation(obs);

        Observation obs_b = new Observation(115.98,-31.91);
        obs_b.setRange(800.0);
        obs.setObservationType(ObservationType.range);
        fuzerProcess.addObservation(obs);
    }

    @Override
    public void result(String geoId, String target, double x1, double x2, double c, double d) {
        System.out.println("RESULT:::: GeoId: "+geoId+", Target: "+target+", lat: "+x1+", lon: "+x2+", c: "+c+", d: "+d);
    }

    // Callback containing new result pushbacks
    @Override
    public void result(Double val) {
        System.out.println("RESULT:::: "+val);
    }
}
