package staraxis.webnet.api.newgame;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * NewGameDraftFiles
 *
 * 作用：
 * - 统一管理新游戏草稿文件路径。
 *
 * 口径：
 * - 目录：gamedata/saves/
 * - 文件：<username>_newgame.json
 */
public final class NewGameDraftFiles {

    private NewGameDraftFiles() {
    }

    public static Path savesDir() {
        return Paths.get("gamedata", "saves");
    }

    public static void ensureDir() throws Exception {
        Files.createDirectories(savesDir());
    }

    public static Path draftPath(String username) {
        return savesDir().resolve(username + "_newgame.json");
    }
}
