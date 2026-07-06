package staraxis.game.command;

import staraxis.game.astro.StarBody;
import staraxis.game.astro.StarSystem;
import staraxis.game.state.WorldState;

/**
 * JoinGameHandler（加入游戏命令处理器）喵。
 *
 * 仅执行 read-only 的星系查找和归属检查，将结果通过 JoinGameCommand 的可变字段返回给 webnet。
 *
 * TODO AssetManager 统一处理：暂不注册国家/绑定玩家/分配归属/生成舰船，等后续流程设计喵。
 *
 * 所有 WorldState 只读查询均在 game 模块内完成，webnet 层不再直接访问 WorldState 喵。
 */
public class JoinGameHandler implements CommandHandler<JoinGameCommand> {

    @Override
    public void handle(JoinGameCommand command, WorldState worldState, double dtGameHours) {
        if (command == null || worldState == null) {
            return;
        }

        String playerId = command.getPlayerId();
        long chosenSystemId = command.getChosenSystemId();
        boolean randomSpawn = chosenSystemId == -1L;

        // 1. 查找目标星系喵
        StarSystem target = null;
        if (randomSpawn) {
            long bestId = Long.MAX_VALUE;
            for (StarSystem sys : worldState.astro.getSystemsView()) {
                if (sys == null || !isSystemUnowned(sys)) {
                    continue;
                }
                if (sys.systemId < bestId) {
                    bestId = sys.systemId;
                    target = sys;
                }
            }
        } else {
            for (StarSystem sys : worldState.astro.getSystemsView()) {
                if (sys != null && sys.systemId == chosenSystemId) {
                    target = sys;
                    break;
                }
            }
        }

        if (target == null) {
            command.setSuccess(false);
            command.setErrorMessage(randomSpawn ? "no_spawn_system_available" : "system_not_found");
            return;
        }

        if (!isSystemUnowned(target)) {
            command.setSuccess(false);
            command.setErrorMessage("system_already_owned");
            return;
        }

        // TODO AssetManager 统一处理：暂不注册国家/绑定玩家/分配归属，等后续流程设计喵
        // 以下操作将在后续 AssetManager 重构中统一处理：
        // - nationManager.registerNation / assignPlayerToNation
        // - target.assignOwnership(nationId)
        // - ShipSpawnService.spawnInitialShip

        // 2. 填充查询结果喵
        String nationId = "nation_" + playerId;
        command.setSuccess(true);
        command.setNationId(nationId);
        command.setSpawnSystemId(target.systemId);
    }

    /**
     * 判断恒星系是否无主喵。
     * 只要该系统或其恒星、行星任一 ownerNationId 非空，即视为已归属喵。
     */
    private static boolean isSystemUnowned(StarSystem sys) {
        if (sys == null) {
            return false;
        }

        if (sys.ownerNationId != null && !sys.ownerNationId.isBlank()) {
            return false;
        }

        if (sys.stars != null) {
            for (StarBody star : sys.stars) {
                if (star != null && star.ownerNationId != null && !star.ownerNationId.isBlank()) {
                    return false;
                }
            }
        }

        if (sys.planets != null) {
            for (var planet : sys.planets) {
                if (planet != null && planet.ownerNationId != null && !planet.ownerNationId.isBlank()) {
                    return false;
                }
            }
        }

        return true;
    }
}
