package staraxis.game.command;

/**
 * MoveShipCommand（移动舰船命令）喵。
 *
 * 用于让舰船移动到指定世界坐标喵。
 */
public class MoveShipCommand extends Command {

    private final String nationId;
    private final String clientCommandId;
    private final long shipEntityId;
    private final double targetX;
    private final double targetY;
    private final double targetZ;

    public MoveShipCommand(String nationId, String clientCommandId, long shipEntityId, double targetX, double targetY) {
        this(nationId, clientCommandId, shipEntityId, targetX, 0, targetY);
    }

    public MoveShipCommand(String nationId, String clientCommandId, long shipEntityId, double targetX, double targetY, double targetZ) {
        super("moveShip");
        this.nationId = nationId;
        this.clientCommandId = clientCommandId;
        this.shipEntityId = shipEntityId;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
    }

    public String getNationId() { return nationId; }
    public String getClientCommandId() { return clientCommandId; }
    public long getShipEntityId() { return shipEntityId; }
    public double getTargetX() { return targetX; }
    public double getTargetY() { return targetY; }
    public double getTargetZ() { return targetZ; }
}
