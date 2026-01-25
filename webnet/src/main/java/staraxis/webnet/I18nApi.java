package staraxis.webnet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class I18nApi {

    private I18nApi() {
    }

    public static Map<String, String> loadMergedStrings(String language) {
        String lang = (language == null || language.isBlank()) ? "zh" : language.trim();

        // base
        Map<String, String> out = new HashMap<>();
        File baseFile = new File("assets/i18n/strings_" + lang + ".properties");
        loadPropertiesInto(baseFile, out);

        // mods：按 ModManager 给出的顺序加载（后读覆盖前读）
        ModManager modManager = new ModManager(new ModOrderRepository());
        List<String> orderedModIds = modManager.listModIdsOrderedAndEnabled();
        for (String modId : orderedModIds) {
            if (modId == null || modId.isBlank()) {
                continue;
            }
            File modI18n = new File("gamedata/mods/" + modId + "/i18n/strings_" + lang + ".properties");
            loadPropertiesInto(modI18n, out);
        }

        return out;
    }

    public static List<String> listAvailableLanguages() {
        java.util.Set<String> langs = new java.util.TreeSet<>();

        File baseDir = new File("assets/i18n");
        if (baseDir.exists() && baseDir.isDirectory()) {
            File[] files = baseDir.listFiles(
                    (d, name) -> name != null && name.startsWith("strings_") && name.endsWith(".properties"));
            if (files != null) {
                for (File f : files) {
                    String code = parseLanguageCode(f.getName());
                    if (code != null) {
                        langs.add(code);
                    }
                }
            }
        }

        // mods：按 ModManager 给出的顺序扫描（去重）
        ModManager modManager = new ModManager(new ModOrderRepository());
        List<String> orderedModIds = modManager.listModIdsOrderedAndEnabled();
        for (String modId : orderedModIds) {
            if (modId == null || modId.isBlank()) {
                continue;
            }
            File i18nDir = new File("gamedata/mods/" + modId + "/i18n");
            if (!i18nDir.exists() || !i18nDir.isDirectory()) {
                continue;
            }
            File[] files = i18nDir.listFiles(
                    (d, name) -> name != null && name.startsWith("strings_") && name.endsWith(".properties"));
            if (files == null) {
                continue;
            }
            for (File f : files) {
                String code = parseLanguageCode(f.getName());
                if (code != null) {
                    langs.add(code);
                }
            }
        }

        return new ArrayList<>(langs);
    }

    private static String parseLanguageCode(String filename) {
        if (filename == null) {
            return null;
        }
        if (!filename.startsWith("strings_") || !filename.endsWith(".properties")) {
            return null;
        }
        String code = filename.substring("strings_".length(), filename.length() - ".properties".length());
        return code.isBlank() ? null : code;
    }

    private static void loadPropertiesInto(File file, Map<String, String> out) {
        if (file == null || !file.exists() || !file.isFile()) {
            return;
        }

        Properties p = new Properties();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            p.load(r);
        } catch (Exception ignored) {
            return;
        }

        for (String k : p.stringPropertyNames()) {
            String v = p.getProperty(k);
            if (k != null && v != null) {
                out.put(k, v);
            }
        }
    }
}
