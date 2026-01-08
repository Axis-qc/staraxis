package com.staraxis.game.core.coordinate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CoordinateServiceTest {

    @Test
    void shouldExposeScaleSystem() {
        CoordinateService service = new CoordinateService();
        assertNotNull(service.getScaleSystem());
    }
}
