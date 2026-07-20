package staraxis.game.command;

import staraxis.game.astro.StarSystem;
import staraxis.game.nation.NationSpawnService;
import staraxis.game.state.WorldState;

/**
 * SetupPlayerHomeHandler（玩家建立母星家园命令处理器）喵。
 *
 * 在 game 权威层执行：
 * 1. 参数校验（nationId / systemId）
 * 2. 查找目标星系
 * 3. 委托 NationSpawnService 执行国家注册 + 星系归属 + 初始舰队生成
 * 4. 填充执行结果到 command（舰船ID列表 + 舰队质心坐标）
 */
public class SetupPlayerHomeHandler implements CommandHandler<SetupPlayerHomeCommand> {

    @Override
    public void handle(SetupPlayerHomeCommand command, WorldState worldState, double dtGameHours) {
        if (command == null || worldState == null) {
            return;
        }

        String nationId = command.getNationId();
        long systemId = command.getSystemId();

        // 1. 查找目标星系喵
        StarSystem target = null;
        for (StarSystem sys : worldState.astro.getSystemsView()) {
            if (sys != null && sys.systemId == systemId) {
                target = sys;
                break;
            }
        }

        if (target == null) {
            command.setSuccess(false);
            command.setErrorMessage("system_not_found: " + systemId);
            return;
        }

        // 2. 调 NationSpawnService 执行开局设置喵
        NationSpawnService spawnService = new NationSpawnService(worldState);
        NationSpawnService.SpawnResult result = spawnService.setupPlayerNationAt(nationId, target);

        // 3. 填充结果喵
        command.setSuccess(true);
        command.getSpawnedShipIds().addAll(result.shipIds());
        command.setFleetCenterPos(result.fleetCenterPos());
    }
}
