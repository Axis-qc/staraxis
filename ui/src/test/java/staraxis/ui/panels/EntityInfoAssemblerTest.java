package staraxis.ui.panels;

import org.junit.jupiter.api.Test;
import staraxis.game.astro.Habitability;
import staraxis.game.entity.EntityType;
import staraxis.game.ship.ShipDesign;
import staraxis.game.space.SpacePosition;
import staraxis.game.state.DailySettlementState;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.snapshot.EntitySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EntityInfoAssemblerTest（实体信息组装器 G1.1 指令组装单元测试）。
 *
 * 覆盖：
 * - 舰船「移动」action 的组装与可用性（有主启用，无主置灰，非舰船不显示）
 * - 行星「殖民」action 的组装与可用性（宜居 + 无主 + 无城市才显示；
 *   选中殖民舰才可用，否则置灰但仍显示）
 * - A/B 实体切换不残留 action
 */
class EntityInfoAssemblerTest {

    private static final long SYSTEM_ID = 1L;
    private static final long COLONY_SHIP_ID = 100L;
    private static final long PLANET_ID = 1001L;

    // ===== 构造辅助 =====

    private static EntitySnapshot shipSnapshot(long id, String ownerNationId, Set<String> flags) {
        EntitySnapshot.ShipDetails sd = new EntitySnapshot.ShipDetails(
                flags != null ? flags : Set.of(), 0.0, false, null, null);
        return new EntitySnapshot(id, EntityType.SHIP, SYSTEM_ID, 0L,
                new SpacePosition(100, 0, 200), ownerNationId, null, true, sd);
    }

    private static EntitySnapshot planetSnapshot(long id, String typeId, String ownerNationId) {
        EntitySnapshot.PlanetDetails pd = new EntitySnapshot.PlanetDetails(
                typeId, Habitability.HABITABLE, 0, 0, 6000.0, 24.0, "texture.png",
                false, 1L, 100000.0, 0.01, 0.0, 0.0, 0.0, 365.0, 0.0);
        return new EntitySnapshot(id, EntityType.PLANET, SYSTEM_ID, 1L,
                new SpacePosition(0, 0, 0), ownerNationId, null, true, pd);
    }

    private static EntitySnapshot starSnapshot(long id) {
        EntitySnapshot.StarDetails sd = new EntitySnapshot.StarDetails(
                "G2V", 40.0, 1.0, 5772, "star", "star.png", 0, 0, 0, 0);
        return new EntitySnapshot(id, EntityType.STAR, SYSTEM_ID, 0L,
                new SpacePosition(0, 0, 0), null, null, true, sd);
    }

    private static RealTimeWorldState rtWith(EntitySnapshot... snaps) {
        RealTimeWorldState rt = new RealTimeWorldState();
        for (EntitySnapshot s : snaps) {
            rt.putEntitySnapshot(s);
        }
        return rt;
    }

    private static DailySettlementState dsWith(EntitySnapshot... snaps) {
        DailySettlementState ds = new DailySettlementState();
        ds.publicEntityBaselinesBySectorKey = new HashMap<>();
        ds.publicEntityBaselinesBySectorKey.put(String.valueOf(SYSTEM_ID), new ArrayList<>(List.of(snaps)));
        ds.planetSurfacesByPlanetId = new HashMap<>();
        return ds;
    }

    private static EntityInfoViewModel.ActionEntry findAction(EntityInfoViewModel vm, String id) {
        return vm.actions.stream().filter(a -> a.id().equals(id)).findFirst().orElse(null);
    }

    // ===== 移动指令 =====

    @Test
    void shipWithOwnerGetsMoveActionEnabled() {
        EntityInfoViewModel vm = EntityInfoAssembler.assemble(COLONY_SHIP_ID,
                rtWith(shipSnapshot(COLONY_SHIP_ID, "nation_player", Set.of(ShipDesign.FLAG_COLONY))), null);

        EntityInfoViewModel.ActionEntry move = findAction(vm, EntityInfoAssembler.ACTION_MOVE);
        assertNotNull(move);
        assertEquals("移动", move.label());
        assertTrue(move.enabled());
    }

    @Test
    void shipWithoutOwnerGetsMoveActionDisabled() {
        EntityInfoViewModel vm = EntityInfoAssembler.assemble(COLONY_SHIP_ID,
                rtWith(shipSnapshot(COLONY_SHIP_ID, null, Set.of())), null);

        EntityInfoViewModel.ActionEntry move = findAction(vm, EntityInfoAssembler.ACTION_MOVE);
        assertNotNull(move);
        assertFalse(move.enabled());
    }

    @Test
    void nonShipHasNoMoveAction() {
        EntityInfoViewModel starVm = EntityInfoAssembler.assemble(3L, null, dsWith(starSnapshot(3L)));
        assertNull(findAction(starVm, EntityInfoAssembler.ACTION_MOVE));

        EntityInfoViewModel planetVm = EntityInfoAssembler.assemble(PLANET_ID, null,
                dsWith(planetSnapshot(PLANET_ID, "TERRESTRIAL", null)));
        assertNull(findAction(planetVm, EntityInfoAssembler.ACTION_MOVE));
    }

    // ===== 殖民指令 =====

    @Test
    void colonizeEnabledWhenColonyShipSelected() {
        RealTimeWorldState rt = rtWith(
                shipSnapshot(COLONY_SHIP_ID, "nation_player", Set.of(ShipDesign.FLAG_COLONY)));
        DailySettlementState ds = dsWith(planetSnapshot(PLANET_ID, "TERRESTRIAL", null));

        EntityInfoViewModel vm = EntityInfoAssembler.assemble(PLANET_ID, rt, ds, COLONY_SHIP_ID);

        EntityInfoViewModel.ActionEntry colonize = findAction(vm, EntityInfoAssembler.ACTION_COLONIZE);
        assertNotNull(colonize);
        assertEquals("殖民", colonize.label());
        assertTrue(colonize.enabled());
    }

    @Test
    void colonizeShownButDisabledWithoutSelectedShip() {
        RealTimeWorldState rt = rtWith(
                shipSnapshot(COLONY_SHIP_ID, "nation_player", Set.of(ShipDesign.FLAG_COLONY)));
        DailySettlementState ds = dsWith(planetSnapshot(PLANET_ID, "TERRESTRIAL", null));

        // 三参重载：未传选中舰船 → 殖民按钮置灰但仍显示喵
        EntityInfoViewModel vm = EntityInfoAssembler.assemble(PLANET_ID, rt, ds);

        EntityInfoViewModel.ActionEntry colonize = findAction(vm, EntityInfoAssembler.ACTION_COLONIZE);
        assertNotNull(colonize);
        assertFalse(colonize.enabled());
    }

    @Test
    void colonizeShownButDisabledWhenSelectedShipIsNotColony() {
        RealTimeWorldState rt = rtWith(shipSnapshot(COLONY_SHIP_ID, "nation_player", Set.of()));
        DailySettlementState ds = dsWith(planetSnapshot(PLANET_ID, "TERRESTRIAL", null));

        EntityInfoViewModel vm = EntityInfoAssembler.assemble(PLANET_ID, rt, ds, COLONY_SHIP_ID);

        EntityInfoViewModel.ActionEntry colonize = findAction(vm, EntityInfoAssembler.ACTION_COLONIZE);
        assertNotNull(colonize);
        assertFalse(colonize.enabled());
    }

    @Test
    void colonizeDisabledWhenSelectedShipIdDoesNotExist() {
        RealTimeWorldState rt = rtWith(
                shipSnapshot(COLONY_SHIP_ID, "nation_player", Set.of(ShipDesign.FLAG_COLONY)));
        DailySettlementState ds = dsWith(planetSnapshot(PLANET_ID, "TERRESTRIAL", null));

        EntityInfoViewModel vm = EntityInfoAssembler.assemble(PLANET_ID, rt, ds, 9999L);

        EntityInfoViewModel.ActionEntry colonize = findAction(vm, EntityInfoAssembler.ACTION_COLONIZE);
        assertNotNull(colonize);
        assertFalse(colonize.enabled());
    }

    @Test
    void nonHabitablePlanetHasNoColonizeAction() {
        DailySettlementState ds = dsWith(planetSnapshot(PLANET_ID, "GAS_GIANT", null));

        EntityInfoViewModel vm = EntityInfoAssembler.assemble(PLANET_ID, null, ds, COLONY_SHIP_ID);

        assertNull(findAction(vm, EntityInfoAssembler.ACTION_COLONIZE));
    }

    @Test
    void ownedPlanetHasNoColonizeAction() {
        DailySettlementState ds = dsWith(planetSnapshot(PLANET_ID, "TERRESTRIAL", "nation_ai"));

        EntityInfoViewModel vm = EntityInfoAssembler.assemble(PLANET_ID, null, ds, COLONY_SHIP_ID);

        assertNull(findAction(vm, EntityInfoAssembler.ACTION_COLONIZE));
    }

    @Test
    void planetWithCitiesHasNoColonizeAction() {
        EntitySnapshot planet = planetSnapshot(PLANET_ID, "TERRESTRIAL", null);
        DailySettlementState ds = dsWith(planet);
        ds.planetSurfacesByPlanetId.put(PLANET_ID, new DailySettlementState.PlanetSurfaceDailySnapshot(
                PLANET_ID, List.of(),
                List.of(new DailySettlementState.CityDailySnapshot(1L, "新伊甸", "OUTPOST", 1, 5000, true))));

        EntityInfoViewModel vm = EntityInfoAssembler.assemble(PLANET_ID, null, ds, COLONY_SHIP_ID);

        assertNull(findAction(vm, EntityInfoAssembler.ACTION_COLONIZE));
    }

    // ===== A/B 实体切换不残留 action =====

    @Test
    void switchingEntitiesDoesNotLeakActions() {
        RealTimeWorldState rt = rtWith(
                shipSnapshot(COLONY_SHIP_ID, "nation_player", Set.of(ShipDesign.FLAG_COLONY)));
        DailySettlementState ds = dsWith(
                planetSnapshot(PLANET_ID, "TERRESTRIAL", null),
                planetSnapshot(2002L, "GAS_GIANT", null));

        // 行星 A（宜居无主）：只含殖民指令，不残留移动喵
        EntityInfoViewModel vmA = EntityInfoAssembler.assemble(PLANET_ID, rt, ds, COLONY_SHIP_ID);
        assertNotNull(findAction(vmA, EntityInfoAssembler.ACTION_COLONIZE));
        assertNull(findAction(vmA, EntityInfoAssembler.ACTION_MOVE));

        // 行星 B（不宜居）：无任何指令，不残留 A 的殖民喵
        EntityInfoViewModel vmB = EntityInfoAssembler.assemble(2002L, rt, ds, COLONY_SHIP_ID);
        assertNull(findAction(vmB, EntityInfoAssembler.ACTION_COLONIZE));
        assertNull(findAction(vmB, EntityInfoAssembler.ACTION_MOVE));

        // 舰船：只含移动，不残留殖民喵
        EntityInfoViewModel vmShip = EntityInfoAssembler.assemble(COLONY_SHIP_ID, rt, ds, COLONY_SHIP_ID);
        assertNotNull(findAction(vmShip, EntityInfoAssembler.ACTION_MOVE));
        assertNull(findAction(vmShip, EntityInfoAssembler.ACTION_COLONIZE));
    }
}
