package staraxis.webnet;

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
