package tech.tgo.ecg.compute;

import java.util.Vector;

public class FilterExecution {
    public FilterExecution(Double[] latlon) {
        this.latlon = latlon;
    }
    Double[] latlon;

    public Double[] getLatlon() {
        return latlon;
    }

    public void setLatlon(Double[] latlon) {
        this.latlon = latlon;
    }

    // Reused memory store, for holding Observation utilisation and performance information
    Vector<FilterObservationDTO> filterObservationDTOs = new Vector<FilterObservationDTO>();

    public Vector<FilterObservationDTO> getFilterObservationDTOs() {
        return filterObservationDTOs;
    }

    public void setFilterObservationDTOs(Vector<FilterObservationDTO> filterObservationDTOs) {
        this.filterObservationDTOs = filterObservationDTOs;
    }
}
