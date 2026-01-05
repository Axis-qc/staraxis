package com.staraxis.game.shared.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HexMathTest {

    @Test
    public void testHexCoordInvariants() {
        // Valid coord
        assertDoesNotThrow(() -> HexCoord.of(1, -1, 0));
        assertDoesNotThrow(() -> HexCoord.of(0, 0, 0));

        // Invalid coord
        assertThrows(IllegalArgumentException.class, () -> HexCoord.of(1, 1, 1));
    }

    @Test
    public void testDistance() {
        HexCoord origin = HexCoord.of(0, 0, 0);
        HexCoord a = HexCoord.of(2, -2, 0);
        HexCoord b = HexCoord.of(-1, 0, 1);

        assertEquals(0, HexMath.distance(origin, origin));
        assertEquals(2, HexMath.distance(origin, a));
        assertEquals(1, HexMath.distance(origin, b));
        assertEquals(3, HexMath.distance(a, b));
    }

    @Test
    public void testNeighbors() {
        HexCoord origin = HexCoord.of(0, 0, 0);
        HexCoord neighbor = HexMath.neighbor(origin, 0);

        assertEquals(1, HexMath.distance(origin, neighbor));
        // DIRECTIONS[0] = (1, -1, 0)
        assertEquals(1, neighbor.getX());
        assertEquals(-1, neighbor.getY());
        assertEquals(0, neighbor.getZ());
    }
}
