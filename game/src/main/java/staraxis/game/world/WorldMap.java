package staraxis.game.world;

import staraxis.game.world.hex.SectorCoord;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorldMap {

    public final int radius;

    private final Map<SectorCoord, WorldSector> sectorsByCoord;

    /**
     * 预留：玩家国家。
     */
    public final String playerNationId;

    public WorldMap(int radius, String playerNationId, Map<SectorCoord, WorldSector> sectorsByCoord) {
        this.radius = radius;
        this.playerNationId = playerNationId;
        this.sectorsByCoord = new LinkedHashMap<>(sectorsByCoord);
    }

    public WorldSector getSector(SectorCoord coord) {
        return sectorsByCoord.get(coord);
    }

    public Map<SectorCoord, WorldSector> getSectorsByCoordView() {
        return Collections.unmodifiableMap(sectorsByCoord);
    }

    public Collection<WorldSector> getSectorsView() {
        return Collections.unmodifiableCollection(sectorsByCoord.values());
    }
}
