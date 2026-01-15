package staraxis.shared.protocol;

import java.io.Serializable;

/**
 * 客户端向服务端发送的意图指令基类。
 * 具体业务指令应继承并补充字段。
 */
public abstract class Command implements Serializable {
  /** 全局唯一 ID，用于幂等去重 */
  public String cmdId;
  /** 客户端递增序号，用于乱序处理 */
  public int clientSeq;
  /** 发起玩家 ID */
  public String playerId;
}
