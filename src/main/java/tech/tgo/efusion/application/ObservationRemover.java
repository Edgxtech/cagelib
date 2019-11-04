//package tech.tgo.fuzer.application;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import tech.tgo.fuzer.EfusionProcessManager;
//import tech.tgo.fuzer.model.Observation;
//
//import java.util.ArrayList;
//import java.util.Iterator;
//import java.util.List;
//import java.util.TimerTask;
//
///* Just to simulate receiving new observations */
//public class ObservationRemover extends TimerTask {
//
//    private static final Logger log = LoggerFactory.getLogger(ObservationRemover.class);
//
//    List<Observation> observations = new ArrayList<Observation>();
//    Iterator it = observations.iterator();
//
//    EfusionProcessManager fuzerProcess;
//
//    @Override
//    public void run() {
//        if (it.hasNext()) {
//            log.debug("Adding one more observation..");
//
//            Observation obs = (Observation) it.next();
//            try {
//                fuzerProcess.removeObservation(obs);
//                log.debug("..Removed");
//            }
//            catch (Exception e) {
//                log.error(e.getMessage());
//            }
//        }
//    }
//
//    public EfusionProcessManager getFuzerProcess() {
//        return fuzerProcess;
//    }
//
//    public void setFuzerProcess(EfusionProcessManager fuzerProcess) {
//        this.fuzerProcess = fuzerProcess;
//    }
//
//    public List<Observation> getObservations() {
//        return observations;
//    }
//
//    public void setObservations(List<Observation> observations) {
//        this.observations = observations;
//        this.it = observations.iterator();
//    }
//}
