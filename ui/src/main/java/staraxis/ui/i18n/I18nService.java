package staraxis.ui.i18n;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

public class I18nService {

    private final Properties strings = new Properties();
    private String currentLanguage = "zh";

    public void load(String language) {
        currentLanguage = language;
        strings.clear();

        loadAndMerge(Gdx.files.internal("i18n/strings_" + language + ".properties"));

        FileHandle modsDir = Gdx.files.local("../gamedata/mods/");
        if (modsDir.exists() && modsDir.isDirectory()) {
            staraxis.ui.settings.ModManager modManager = new staraxis.ui.settings.ModManager(
                    new staraxis.ui.settings.ModOrderRepository(),
                    new staraxis.ui.settings.ModMetadataRepository());

            java.util.List<String> orderedModIds = modManager.listModIdsOrdered();
            for (String modId : orderedModIds) {
                if (modId == null || modId.isBlank()) {
                    continue;
                }
                FileHandle modDir = modsDir.child(modId);
                if (!modDir.exists() || !modDir.isDirectory()) {
                    continue;
                }
                FileHandle modI18nFile = modDir.child("i18n/strings_" + language + ".properties");
                loadAndMerge(modI18nFile);
            }
        }
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    /**
     * 扫描可用语言列表：
     * - 本体：assets/i18n/strings_*.properties
     * - Mod：gamedata/mods/
     * 返回语言 code（如 zh/en/ja），去重并排序。
     */

    public List<String> listAvailableLanguages() {
        Set<String> langs = new TreeSet<>();

        // base
        FileHandle baseDir = Gdx.files.internal("i18n");
        if (baseDir.exists() && baseDir.isDirectory()) {
            for (FileHandle f : baseDir.list()) {
                String code = parseLanguageCode(f.name());
                if (code != null)
                    langs.add(code);
            }
        }

        // mods
        FileHandle modsDir = Gdx.files.local("../gamedata/mods/");
        if (modsDir.exists() && modsDir.isDirectory()) {
            FileHandle[] modDirs = modsDir.list();
            Arrays.sort(modDirs, Comparator.comparing(FileHandle::name));
            for (FileHandle modDir : modDirs) {
                if (!modDir.isDirectory())
                    continue;
                FileHandle modI18nDir = modDir.child("i18n");
                if (!modI18nDir.exists() || !modI18nDir.isDirectory())
                    continue;
                for (FileHandle f : modI18nDir.list()) {
                    String code = parseLanguageCode(f.name());
                    if (code != null)
                        langs.add(code);
                }
            }
        }

        return new ArrayList<>(langs);
    }

    private String parseLanguageCode(String filename) {
        if (!filename.startsWith("strings_") || !filename.endsWith(".properties"))
            return null;
        String code = filename.substring("strings_".length(), filename.length() - ".properties".length());
        return code.isBlank() ? null : code;
    }

    private void loadAndMerge(FileHandle fileHandle) {
        if (fileHandle != null && fileHandle.exists()) {
            try (Reader reader = fileHandle.reader("UTF-8")) {
                strings.load(reader);
                Gdx.app.log("I18nService", "Loaded and merged: " + fileHandle.path());
            } catch (IOException e) {
                Gdx.app.error("I18nService", "Failed to load " + fileHandle.path(), e);
            }
        }
    }

    public String get(String key) {
        return strings.getProperty(key, key);
    }

    public String format(String key, Object... args) {
        String format = strings.getProperty(key, key);
        try {
            return String.format(format, args);
        } catch (Exception e) {
            return key;
        }
    }
}
