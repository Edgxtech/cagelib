package tech.tgo.ecg.model;

/**
 * Processing modes
 *
 * fix - generates single position and exits thread, requires observations pre-selected
 * track - generates online track of position, takes new or updates and removes observations dynamically
 *
 * @author Timothy Edge (timmyedge)
 */
public enum MissionMode {
    fix,track
}