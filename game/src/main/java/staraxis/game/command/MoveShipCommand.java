package staraxis.game.command;

/**
 * MoveShipCommand（移动舰船命令）喵。
 *
 * 用于让舰船移动到指定世界坐标喵。
 */
public class MoveShipCommand extends Command {

    private final String nationId;
    private final long shipEntityId;
    private final double targetX;
    private final double targetY;

    public MoveShipCommand(String nationId, long shipEntityId, double targetX, double targetY) {
        super("moveShip");
        this.nationId = nationId;
        this.shipEntityId = shipEntityId;
        this.targetX = targetX;
        this.targetY = targetY;
    }

    public String getNationId() { return nationId; }
    public long getShipEntityId() { return shipEntityId; }
    public double getTargetX() { return targetX; }
    public double getTargetY() { return targetY; }
}
