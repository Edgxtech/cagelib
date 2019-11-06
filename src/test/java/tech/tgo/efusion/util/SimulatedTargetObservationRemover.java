package tech.tgo.efusion.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.tgo.efusion.EfusionProcessManager;
import tech.tgo.efusion.model.Observation;
import java.util.*;

/**
 * Remove random simulated observation
 * @author Timothy Edge (timmyedge)
 */
public class SimulatedTargetObservationRemover extends TimerTask {

    private static final Logger log = LoggerFactory.getLogger(SimulatedTargetObservationRemover.class);

    EfusionProcessManager efusionProcessManager;

    @Override
    public void run() {
        log.debug("Removing a random asset, current observations size: "+efusionProcessManager.getGeoMission().getObservations().size());
        Random rand = new Random();
        List<Observation> observations = new ArrayList<Observation>(efusionProcessManager.getGeoMission().getObservations().values());
        Observation obs = observations.get(rand.nextInt(observations.size()));
        //efusionProcessManager.getGeoMission().getObservations().remove(obs.getId());
        try {
            efusionProcessManager.removeObservation(obs);
        }
        catch (Exception e) {
            log.debug("Trouble removing observation");
            e.printStackTrace();
        }
        log.debug("Removed a random asset, new observations size: "+efusionProcessManager.getGeoMission().getObservations().size());
    }

    public EfusionProcessManager getEfusionProcessManager() {
        return efusionProcessManager;
    }

    public void setEfusionProcessManager(EfusionProcessManager efusionProcessManager) {
        this.efusionProcessManager = efusionProcessManager;
    }
}
