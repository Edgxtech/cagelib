package tech.tgo.fuzer.model;

/**
 * Geolocation modes
 *
 * fix - generates single position and exits thread, requires observations pre-selected
 * track - generates online track of position, can take new, update or remove observations dynamically
 *
 * @author Timothy Edge (timmyedge)
 */
public enum FuzerMode {
    fix,track
}
