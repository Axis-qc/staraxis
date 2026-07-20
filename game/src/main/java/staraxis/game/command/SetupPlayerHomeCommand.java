package staraxis.game.command;

import java.util.ArrayList;
import java.util.List;

import staraxis.game.space.SpacePosition;

/**
 * SetupPlayerHomeCommand（玩家建立母星家园命令）喵。
 *
 * 在 Galaxy View 选择母星系后，由 client 调用 game 端同步执行：
 * - 注册国家 → 星系归属 → 在最远行星轨道外侧生成初始舰队。
 *
 * 执行结果（由 SetupPlayerHomeHandler 填充）：
 * - success / errorMessage：执行状态
 * - spawnedShipIds：生成的舰船实体ID列表
 * - fleetCenterPos：舰队质心世界坐标（用于 client 镜头定位和虫洞平面）
 */
public class SetupPlayerHomeCommand extends Command {

    private final String nationId;
    private final long systemId;

    // ── 执行结果（由 handler 填充）──
    private boolean success;
    private String errorMessage;
    private final List<Long> spawnedShipIds = new ArrayList<>();
    private SpacePosition fleetCenterPos;

    /**
     * @param nationId 玩家所选国家ID（必须非空）
     * @param systemId 玩家所选星系ID（必须 > 0 且在 AstroData 中存在）
     */
    public SetupPlayerHomeCommand(String nationId, long systemId) {
        super("setupPlayerHome");
        if (nationId == null || nationId.isBlank()) {
            throw new IllegalArgumentException("nationId_required");
        }
        if (systemId <= 0) {
            throw new IllegalArgumentException("systemId_required");
        }
        this.nationId = nationId;
        this.systemId = systemId;
    }

    public String getNationId() {
        return nationId;
    }

    public long getSystemId() {
        return systemId;
    }

    /** 命令执行是否成功（由 handler 填充）喵。 */
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    /** 错误信息（仅失败时有值）喵。 */
    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /** 生成的舰船实体ID列表（成功时有值）喵。 */
    public List<Long> getSpawnedShipIds() {
        return spawnedShipIds;
    }

    /** 舰队质心世界坐标（成功时有值，用于 client 镜头定位）喵。 */
    public SpacePosition getFleetCenterPos() {
        return fleetCenterPos;
    }

    public void setFleetCenterPos(SpacePosition fleetCenterPos) {
        this.fleetCenterPos = fleetCenterPos;
    }
}
