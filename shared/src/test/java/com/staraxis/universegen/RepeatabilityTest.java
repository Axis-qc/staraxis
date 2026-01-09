package com.staraxis.universegen;

import com.staraxis.game.shared.net.worldgen.snapshot.HexCoordSnapshot;
import com.staraxis.universegen.config.GalaxyPreset;
import com.staraxis.universegen.config.UniverseGenConfig;
import com.staraxis.game.shared.net.worldgen.snapshot.SectorSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.UniverseSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RepeatabilityTest {

    @Test
    void sameSeed_generatesSameGalaxy_threeTimes() throws GenerationException {
        UniverseGenConfig cfg = new UniverseGenConfig();
        cfg.setSeed(123);
        cfg.setGalaxyRadiusR(2);
        cfg.setHexRadiusLy(5f);

        GalaxyGeneratorFacade gen = new GalaxyGeneratorFacade();
        UniverseSnapshot g1 = gen.generate(cfg);
        UniverseSnapshot g2 = gen.generate(cfg);
        UniverseSnapshot g3 = gen.generate(cfg);

        String summary1 = summary(g1);
        String summary2 = summary(g2);
        String summary3 = summary(g3);

        assertEquals(summary1, summary2, "Generated galaxy summary should match for same seed (run 1 vs 2)");
        assertEquals(summary2, summary3, "Generated galaxy summary should match for same seed (run 2 vs 3)");
    }

    @Test
    void presetConflict_laterOverrides() throws GenerationException {
        UniverseGenConfig cfg = new UniverseGenConfig();
        cfg.setSeed(456);
        cfg.setGalaxyRadiusR(1);
        cfg.setHexRadiusLy(1f);

        HexCoordSnapshot conflictCoord = new HexCoordSnapshot();
        conflictCoord.setX(1);
        conflictCoord.setY(0);
        conflictCoord.setZ(-1);

        GalaxyPreset presetA = new GalaxyPreset();
        presetA.setPresetId("preset-a");
        presetA.setLoadOrder(10);
        presetA.setPlacementType("fixed-hex");
        presetA.setFixedHexCoords(List.of(conflictCoord));
        presetA.setContentTypeId("nebula");

        GalaxyPreset presetB = new GalaxyPreset();
        presetB.setPresetId("preset-b");
        presetB.setLoadOrder(20); // Higher load order, should override A
        presetB.setPlacementType("fixed-hex");
        presetB.setFixedHexCoords(List.of(conflictCoord));
        presetB.setContentTypeId("deep_space");

        List<GalaxyPreset> presets = new ArrayList<>();
        presets.add(presetA);
        presets.add(presetB);
        cfg.setGalaxyPresets(presets);

        GalaxyGeneratorFacade gen = new GalaxyGeneratorFacade();
        UniverseSnapshot snapshot = gen.generate(cfg);

        long conflictId = SectorLocatorService.packAxialToSectorId(conflictCoord.getX(), conflictCoord.getY());

        Optional<SectorSnapshot> conflictSector = snapshot.getSectors().stream()
                .filter(s -> {
                    HexCoordSnapshot c = s.getCoord();
                    return SectorLocatorService.packAxialToSectorId(c.getX(), c.getY()) == conflictId;
                })
                .findFirst();

        assertTrue(conflictSector.isPresent(), "Conflict sector should exist");
        assertEquals("deep_space", conflictSector.get().getSectorType(), "Preset with higher loadOrder should override");
    }

    private String summary(UniverseSnapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append("sectors=").append(s.getSectors().size()).append(";");
        s.getSectors().stream()
                .sorted((a, b) -> {
                    long idA = SectorLocatorService.packAxialToSectorId(a.getCoord().getX(), a.getCoord().getY());
                    long idB = SectorLocatorService.packAxialToSectorId(b.getCoord().getX(), b.getCoord().getY());
                    return Long.compare(idA, idB);
                })
                .forEach(snap -> {
                    long id = SectorLocatorService.packAxialToSectorId(snap.getCoord().getX(), snap.getCoord().getY());
                    sb.append(id).append(":").append(snap.getSectorType()).append(";");
                });
        return sb.toString();
    }
}
