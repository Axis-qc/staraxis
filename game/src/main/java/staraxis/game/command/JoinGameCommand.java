package staraxis.game.command;

/**
 * JoinGameCommand（加入游戏命令）喵。
 *
 * 替代 JoinGameApi.handleConfirmSpawn 中 3 处 getWorldStateForSimOnly() 直读：
 * - 获取星系列表查找目标星系
 * - 检查星系是否无主
 *
 * TODO AssetManager 统一处理：暂不注册国家/绑定玩家/分配归属/生成舰船，等后续流程设计喵。
 *
 * webnet 提交 JoinGameCommand(playerId, chosenSystemId) → game 内部只读查询喵。
 *
 * chosenSystemId = -1 表示随机分配出生点喵。
 */
public class JoinGameCommand extends Command {

    private final String playerId;
    private final long chosenSystemId; // -1 = random spawn

    // ── 执行结果（由 JoinGameHandler 在 handle() 中填充）──
    private boolean success;
    private String errorMessage;
    private String nationId;
    private long spawnSystemId;

    /**
     * @param playerId       玩家 ID
     * @param chosenSystemId 选择的星系 ID，-1 表示随机分配
     */
    public JoinGameCommand(String playerId, long chosenSystemId) {
        super("joinGame");
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId_required");
        }
        this.playerId = playerId;
        this.chosenSystemId = chosenSystemId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public long getChosenSystemId() {
        return chosenSystemId;
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

    /** 分配的国家 ID（成功时有值）喵。 */
    public String getNationId() {
        return nationId;
    }

    public void setNationId(String nationId) {
        this.nationId = nationId;
    }

    /** 出生的星系 ID（成功时有值）喵。 */
    public long getSpawnSystemId() {
        return spawnSystemId;
    }

    public void setSpawnSystemId(long spawnSystemId) {
        this.spawnSystemId = spawnSystemId;
    }
}
