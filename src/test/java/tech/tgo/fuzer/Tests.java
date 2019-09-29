package tech.tgo.fuzer;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tests {

    private static final Logger log = LoggerFactory.getLogger(Tests.class);


    @Test
    public void tests() {
        log.debug("2: " +Math.atan(2));
        log.debug("-2: " +Math.atan(-2));
    }
}
