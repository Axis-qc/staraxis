package staraxis.ui.i18n;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.I18NBundle;

import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;

public class I18nService {

    private final Properties strings = new Properties();

    public void load(String language) {
        strings.clear();

        // 1. Load base language file from assets
        loadAndMerge(Gdx.files.internal("i18n/strings_" + language + ".properties"));

        // 2. Scan and load mod language files
        FileHandle modsDir = Gdx.files.local("gamedata/mods/");
        if (modsDir.exists() && modsDir.isDirectory()) {
            FileHandle[] modDirs = modsDir.list();
            Arrays.sort(modDirs, (f1, f2) -> f1.name().compareTo(f2.name())); // Sort by name for predictable order

            for (FileHandle modDir : modDirs) {
                if (modDir.isDirectory()) {
                    FileHandle modI18nFile = modDir.child("i18n/strings_" + language + ".properties");
                    loadAndMerge(modI18nFile);
                }
            }
        }
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
            return key; // Return key on format error
        }
    }
}
