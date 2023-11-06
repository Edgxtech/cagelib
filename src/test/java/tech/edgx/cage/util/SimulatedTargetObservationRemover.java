package tech.edgx.cage.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.edgx.cage.CageProcessManager;
import tech.edgx.cage.model.Observation;

import java.util.*;

/**
 * Remove random simulated observation
 */
public class SimulatedTargetObservationRemover extends TimerTask {

    private static final Logger log = LoggerFactory.getLogger(SimulatedTargetObservationRemover.class);

    CageProcessManager cageProcessManager;

    @Override
    public void run() {
        log.debug("Removing a random asset, current observations size: "+ cageProcessManager.getGeoMission().getObservations().size());
        Random rand = new Random();
        List<Observation> observations = new ArrayList<Observation>(cageProcessManager.getGeoMission().getObservations().values());
        Observation obs = observations.get(rand.nextInt(observations.size()));
        //efusionProcessManager.getGeoMission().getObservations().remove(obs.getId());
        try {
            cageProcessManager.removeObservation(obs);
        }
        catch (Exception e) {
            log.debug("Trouble removing observation");
            e.printStackTrace();
        }
        log.debug("Removed a random asset, new observations size: "+ cageProcessManager.getGeoMission().getObservations().size());
    }

    public CageProcessManager getCageProcessManager() {
        return cageProcessManager;
    }

    public void setCageProcessManager(CageProcessManager cageProcessManager) {
        this.cageProcessManager = cageProcessManager;
    }
}
