package tech.tgo.ecg.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Timothy Edge (timmyedge)
 */
public class ObservationTestHelpers {

    private static final Logger log = LoggerFactory.getLogger(ObservationTestHelpers.class);

    public static double getRangeMeasurement(double a_y, double a_x, double true_y, double true_x, double range_error_factor) {
        return Math.sqrt(Math.pow(a_y-true_y,2) + Math.pow(a_x-true_x,2)) + range_error_factor;
    }

    public static double getTdoaMeasurement(double a_y, double a_x, double b_y, double b_x, double true_y, double true_x, double tdoa_error_factor) {
        return (Math.sqrt(Math.pow(a_y-true_y,2) + Math.pow(a_x-true_x,2))
                - Math.sqrt(Math.pow(b_y-true_y,2) + Math.pow(b_x-true_x,2)))/ Helpers.SPEED_OF_LIGHT
                + tdoa_error_factor;
    }

    public static double getAoaMeasurement(double a_y, double a_x, double true_y, double true_x, double aoa_error_factor) {
        double meas_aoa = Math.atan((a_y-true_y)/(a_x-true_x)) + aoa_error_factor;
        log.debug("Meas AOA: "+meas_aoa);

        if (true_x < a_x) {
            meas_aoa = meas_aoa + Math.PI;
        }
        if (true_y<a_y && true_x>=a_x) {
            meas_aoa = (Math.PI- Math.abs(meas_aoa)) + Math.PI;
        }
        log.debug("Meas AOA (adjusted): "+meas_aoa);
        return meas_aoa;
    }

    /* ALL BELOW PERTAIN TO RANDOM MEASUREMENTS UP TO THE VALUE OF RANGE RAND FACTOR */
    public static double getRandomRangeMeasurement(double a_y, double a_x, double true_y, double true_x, double range_rand_factor) {
        return Math.sqrt(Math.pow(a_y-true_y,2) + Math.pow(a_x-true_x,2)) + (Math.random()-0.5)*range_rand_factor;
    }

    public static double getRandomTdoaMeasurement(double a_y, double a_x, double b_y, double b_x, double true_y, double true_x, double tdoa_rand_factor) {
        return (Math.sqrt(Math.pow(a_y-true_y,2) + Math.pow(a_x-true_x,2))
                - Math.sqrt(Math.pow(b_y-true_y,2) + Math.pow(b_x-true_x,2)))/Helpers.SPEED_OF_LIGHT
                + (Math.random()-0.5)*tdoa_rand_factor;
    }

    public static double getRandomAoaMeasurement(double a_y, double a_x, double true_y, double true_x, double aoa_rand_factor) {
        double meas_aoa = Math.atan((a_y-true_y)/(a_x-true_x)) + (Math.random()-0.5)*aoa_rand_factor;
        log.debug("Meas AOA: "+meas_aoa);

        if (true_x < a_x) {
            meas_aoa = meas_aoa + Math.PI;
        }
        if (true_y<a_y && true_x>=a_x) {
            meas_aoa = (Math.PI- Math.abs(meas_aoa)) + Math.PI;
        }
        log.debug("Meas AOA (adjusted): "+meas_aoa);
        return meas_aoa;
    }
}
