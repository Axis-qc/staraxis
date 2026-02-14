package staraxis.webnet.repo.nation;

import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.game.nation.NationDef;
import staraxis.game.nation.design.PlayerNationDesign;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PlayerNationFileRepository
 *
 * 作用：
 * - 负责玩家自定义国家（Players）的落盘读写。
 *
 * 目录口径：
 * - gamedata/nations/players/<username>/<nationId>.json
 *
 * 文件格式（最小）：
 * - { schemaVersion: 1, username, playerId, nation: { ...NationDef... },
 * updatedAtUnixMs }
 *
 * 注意：
 * - 所有方法都会进行所有权校验：username + playerId 必须与 accounts 文件一致。
 * - 文件读写是阻塞 IO：调用方必须在 Undertow worker 线程执行（exchange.dispatch(...)）。
 */
public class PlayerNationFileRepository {

    private final ObjectMapper objectMapper;

    public PlayerNationFileRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 列出玩家自定义国家 nationId 列表。
     */
    public List<String> listNationIds(String username, String playerId) {
        AccountOwnershipValidator.validate(objectMapper, username, playerId);

        File dir = new File("gamedata/nations/players/" + username);
        ArrayList<String> out = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory()) {
            return out;
        }
        File[] files = dir.listFiles((d, name) -> name != null && name.endsWith(".json"));
        if (files == null) {
            return out;
        }
        for (File f : files) {
            if (f == null || !f.isFile()) {
                continue;
            }
            String name = f.getName();
            String nationId = name.substring(0, name.length() - ".json".length());
            if (!nationId.isBlank()) {
                out.add(nationId);
            }
        }
        return out;
    }

    /**
     * 加载玩家自定义国家。
     */
    public Optional<PlayerNationDesign> load(String username, String playerId, String nationId) {
        AccountOwnershipValidator.validate(objectMapper, username, playerId);

        if (nationId == null || nationId.isBlank()) {
            return Optional.empty();
        }

        File f = new File("gamedata/nations/players/" + username + "/" + nationId.trim() + ".json");
        if (!f.exists() || !f.isFile()) {
            return Optional.empty();
        }

        try {
            PlayerNationDesign d = objectMapper.readValue(f, PlayerNationDesign.class);
            if (d == null) {
                return Optional.empty();
            }
            // 再做一次一致性校验，防止磁盘内容被手动改坏。
            if (!username.equals(d.username) || !playerId.equals(d.playerId)) {
                return Optional.empty();
            }
            if (d.nation == null || d.nation.id == null || d.nation.id.isBlank()) {
                return Optional.empty();
            }
            if (!nationId.equals(d.nation.id)) {
                return Optional.empty();
            }
            return Optional.of(d);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /**
     * 保存玩家自定义国家（覆盖写）。
     */
    public void save(PlayerNationDesign design) {
        if (design == null) {
            throw new IllegalArgumentException("design_required");
        }
        AccountOwnershipValidator.validate(objectMapper, design.username, design.playerId);

        NationDef nation = design.nation;
        if (nation == null || nation.id == null || nation.id.isBlank()) {
            throw new IllegalArgumentException("nationId_required");
        }

        // 目录隔离：强制写入 players/<username>/
        File dir = new File("gamedata/nations/players/" + design.username);
        if (dir.getParentFile() != null) {
            dir.getParentFile().mkdirs();
        }
        dir.mkdirs();

        File f = new File(dir, nation.id.trim() + ".json");

        // 统一填充 schemaVersion/updatedAt
        if (design.schemaVersion <= 0) {
            design.schemaVersion = 1;
        }
        design.updatedAtUnixMs = System.currentTimeMillis();

        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(f, design);
        } catch (Exception e) {
            throw new IllegalArgumentException("save_failed");
        }
    }

    /**
     * 将玩家自定义国家转换为简化响应结构（避免暴露不需要的字段）。
     */
    public static Map<String, Object> toNationItem(PlayerNationDesign d) {
        NationDef n = d.nation;
        return Map.of(
                "id", n.id,
                "name", n.name == null ? "" : n.name,
                "description", n.description == null ? "" : n.description,
                "governmentId", n.governmentId == null ? "" : n.governmentId,
                "speciesIds", n.speciesIds == null ? List.of() : n.speciesIds,
                "startingTechIds", n.startingTechIds == null ? List.of() : n.startingTechIds);
    }
}
