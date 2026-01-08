package com.staraxis.universegen;

import com.staraxis.universegen.model.Sector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SectorLocatorServiceTest {

    @Test
    void locateCenter_originSector() {
        double edgeLy = 10; // arbitrary
        SectorLocatorService locator = new SectorLocatorService(edgeLy);
        Sector origin = new Sector(0);
        CoordinateSystem coord = locator.locateCenter(origin);
        assertEquals(0, coord.getXKm(), 1e-6, "Origin sector X should be 0");
        assertEquals(0, coord.getYKm(), 1e-6, "Origin sector Y should be 0");
        assertEquals(0, coord.getZKm(), 1e-6);
    }

    @Test
    void locateCenter_positiveAxial() {
        double edgeLy = 5;
        SectorLocatorService locator = new SectorLocatorService(edgeLy);
        long q = 1;
        long r = 2;
        long id = (q << 32) | r;
        Sector sector = new Sector((int) id);
        CoordinateSystem coord = locator.locateCenter(sector);

        double LY_TO_KM = 9.4607e12;
        double sizeKm = edgeLy * LY_TO_KM;
        double expectedX = sizeKm * (Math.sqrt(3) * q + Math.sqrt(3)/2 * r);
        double expectedY = sizeKm * (3.0/2.0 * r);
        assertEquals(expectedX, coord.getXKm(), 1e-3);
        assertEquals(expectedY, coord.getYKm(), 1e-3);
    }
}
