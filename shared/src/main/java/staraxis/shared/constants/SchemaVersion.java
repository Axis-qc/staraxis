package staraxis.shared.constants;

/**
 * 当前通信协议的结构版本号。客户端与服务端在握手阶段应交换并比对此字段，
 * 以决定是否需要兼容流程或强制更新。
 */
public final class SchemaVersion {
    /** 禁止实例化 */
    private SchemaVersion() {
    }

    /** 当前协议版本（递增且只增不减）。 */
    public static final int CURRENT = 1;
}
