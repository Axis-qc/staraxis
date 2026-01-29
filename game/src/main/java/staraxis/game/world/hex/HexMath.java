package staraxis.game.world.hex;

public final class HexMath {

    private HexMath() {
    }

    public static int distance(SectorCoord a, SectorCoord b) {
        int ax = a.q();
        int az = a.r();
        int ay = -ax - az;

        int bx = b.q();
        int bz = b.r();
        int by = -bx - bz;

        return (Math.abs(ax - bx) + Math.abs(ay - by) + Math.abs(az - bz)) / 2;
    }
}
