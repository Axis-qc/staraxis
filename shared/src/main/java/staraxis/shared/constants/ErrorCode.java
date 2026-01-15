package staraxis.shared.constants;

/**
 * 服务端返回给客户端的通用错误码集合。
 * <p>
 * 注意：枚举值一旦对外发布即<strong>不可调整顺序</strong>，只能追加。
 * </p>
 */
public enum ErrorCode {
    OK(0),
    UNAUTHORIZED(1),
    INVALID_PARAM(2),
    NOT_FOUND(3),
    COOLDOWN(4),
    RESOURCE_NOT_ENOUGH(5),
    STATE_NOT_ALLOW(6),
    INTERNAL_ERROR(999);

    public final int code;

    ErrorCode(int code) {
        this.code = code;
    }
}
