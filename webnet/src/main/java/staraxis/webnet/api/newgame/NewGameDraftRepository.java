package staraxis.webnet.api.newgame;

import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.webnet.repo.nation.AccountOwnershipValidator;

import java.nio.file.Path;

/**
 * NewGameDraftRepository
 *
 * 作用：
 * - 新游戏草稿（NewGameDraft）的文件读写。
 *
 * 注意：
 * - 文件读写是阻塞 IO：调用方必须在 Undertow worker 线程执行（exchange.dispatch(...)）。
 */
public class NewGameDraftRepository {

    private final ObjectMapper objectMapper;

    public NewGameDraftRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 加载草稿；若不存在则返回 null。
     */
    public NewGameDraft load(String username, String playerId) {
        AccountOwnershipValidator.validate(objectMapper, username, playerId);

        try {
            Path p = NewGameDraftFiles.draftPath(username);
            if (!p.toFile().isFile()) {
                return null;
            }
            NewGameDraft d = objectMapper.readValue(p.toFile(), NewGameDraft.class);
            if (d == null) {
                return null;
            }
            if (!username.equals(d.username) || !playerId.equals(d.playerId)) {
                return null;
            }
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 覆盖写入草稿。
     */
    public void save(NewGameDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("draft_required");
        }
        AccountOwnershipValidator.validate(objectMapper, draft.username, draft.playerId);

        try {
            NewGameDraftFiles.ensureDir();
            draft.updatedAtUnixMs = System.currentTimeMillis();
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(NewGameDraftFiles.draftPath(draft.username).toFile(), draft);
        } catch (Exception e) {
            throw new IllegalArgumentException("draft_save_failed");
        }
    }
}
