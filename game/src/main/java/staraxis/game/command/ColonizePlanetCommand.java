package staraxis.game.command;

/**
 * ColonizePlanetCommand
 *
 * @description
 *              殖民行星的游戏命令喵。
 *              用于让殖民舰殖民无主行星，将行星所有权分配给玩家国家喵。
 *
 * @usage
 *        - 通过CommandBus提交：commandBus.submit(new ColonizePlanetCommand(shipEntityId, planetEntityId, nationId))
 *        - 在模拟tick的PrepareTick阶段执行喵
 *
 * @provides
 *           - **命令类型**: type() = "colonizePlanet"
 *           - **殖民操作**: 将指定行星的所有权分配给指定国家喵
 *
 * @api
 *      - 参数：
 *      - shipEntityId: 殖民舰实体ID（用于验证和后续逻辑）喵
 *      - planetEntityId: 目标行星实体ID喵
 *      - nationId: 殖民国家ID喵
 *
 * @important_notes
 *                  - 命令执行前会验证：行星是否无主、殖民舰是否属于该国家、距离是否足够等喵
 *                  - 殖民成功后，行星ownerNationId将被设置为指定国家ID喵
 *                  - 殖民舰可能会被消耗或改变状态（如变为殖民地建筑）喵
 *                  - 星区归属可能随之更新（如果该行星是星区内第一个被殖民的实体）喵
 */
public class ColonizePlanetCommand extends Command {

    private final long shipEntityId;
    private final long planetEntityId;
    private final String nationId;

    public ColonizePlanetCommand(long shipEntityId, long planetEntityId, String nationId) {
        super("colonizePlanet");
        this.shipEntityId = shipEntityId;
        this.planetEntityId = planetEntityId;
        this.nationId = nationId;
    }

    public long getShipEntityId() {
        return shipEntityId;
    }

    public long getPlanetEntityId() {
        return planetEntityId;
    }

    public String getNationId() {
        return nationId;
    }
}