package staraxis.webnet;

/**
 * AccountFiles
 *
 * 作用：
 * - 统一管理本地账号数据文件的路径与目录操作。
 * - 账号目录：gamedata/accounts/
 * - 账号文件：<username>.json（username 作为文件名，username 的合法性由 AuthStore 校验）
 *
 * 重要注意事项：
 * - 所有路径均为相对路径，依赖进程 workingDir（通常应为项目根目录）。
 * - 目录/文件操作属于阻塞 IO：在 Undertow handler 中调用这些方法时，应使用 exchange.dispatch(...) 切换到 worker 线程。
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class AccountFiles {

    private AccountFiles() {
    }

    public static Path accountsDir() {
        // workingDir 在 webnet/build.gradle 里设为项目根目录
        return Paths.get("gamedata", "accounts");
    }

    public static void ensureDir() throws Exception {
        Files.createDirectories(accountsDir());
    }

    public static Path accountPath(String username) {
        return accountsDir().resolve(username + ".json");
    }

    public static List<Path> listAccountFiles() {
        try {
            var dir = accountsDir();
            if (!Files.isDirectory(dir)) {
                return List.of();
            }
            var out = new ArrayList<Path>();
            try (var s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().endsWith(".json"))
                        .forEach(out::add);
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
