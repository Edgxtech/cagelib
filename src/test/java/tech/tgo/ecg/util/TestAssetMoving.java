package tech.tgo.ecg.util;

import tech.tgo.ecg.model.Asset;

import java.util.List;

/**
 * @author Timothy Edge (timmyedge)
 */
public class TestAssetMoving extends Asset {
    Boolean provide_range;
    Boolean provide_aoa;
    Boolean provide_tdoa;
    List<String> tdoa_asset_ids;

    /* Locations the asset moves between sequentially, define custom shapes within */
    List<Double[]> locs;

    public Boolean getProvide_range() {
        return provide_range;
    }

    public void setProvide_range(Boolean provide_range) {
        this.provide_range = provide_range;
    }

    public Boolean getProvide_aoa() {
        return provide_aoa;
    }

    public void setProvide_aoa(Boolean provide_aoa) {
        this.provide_aoa = provide_aoa;
    }

    public Boolean getProvide_tdoa() {
        return provide_tdoa;
    }

    public void setProvide_tdoa(Boolean provide_tdoa) {
        this.provide_tdoa = provide_tdoa;
    }

    public List<String> getTdoa_asset_ids() {
        return tdoa_asset_ids;
    }

    public void setTdoa_asset_ids(List<String> tdoa_asset_ids) {
        this.tdoa_asset_ids = tdoa_asset_ids;
    }

    public List<Double[]> getLocs() {
        return locs;
    }

    public void setLocs(List<Double[]> locs) {
        this.locs = locs;
    }
}
