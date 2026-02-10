package staraxis.webnet.repo.nation;

import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.webnet.auth.AuthStore;

import java.io.File;
import java.util.Map;

/**
 * AccountOwnershipValidator
 *
 * 作用：
 * - 根据 username + playerId 校验所有权。
 *
 * 口径：
 * - 必须读取账号文件：gamedata/accounts/<username>.json
 * - 若文件中的 playerId 与请求 playerId 不一致，则视为越权。
 */
public final class AccountOwnershipValidator {

    private AccountOwnershipValidator() {
    }

    /**
     * 校验 username 与 playerId 是否匹配。
     *
     * @param objectMapper Jackson
     * @param username     账号名
     * @param playerId     请求携带的 playerId
     * @throws IllegalArgumentException 当参数不合法或校验失败时抛出
     */
    public static void validate(ObjectMapper objectMapper, String username, String playerId) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username_required");
        }
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId_required");
        }

        // 与 AuthStore 的 username 规则保持一致：username 作为文件名
        String safeUsername = username.trim();
        if (!AuthStore.isSafeUsername(safeUsername)) {
            throw new IllegalArgumentException("username_invalid");
        }

        File f = new File("gamedata/accounts/" + safeUsername + ".json");
        if (!f.exists() || !f.isFile()) {
            throw new IllegalArgumentException("account_not_found");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = objectMapper.readValue(f, Map.class);
            Object pid = root.get("playerId");
            String accountPlayerId = pid == null ? "" : String.valueOf(pid);
            if (!playerId.equals(accountPlayerId)) {
                throw new IllegalArgumentException("ownership_mismatch");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("account_read_failed");
        }
    }
}
