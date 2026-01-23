package staraxis.ui.settings;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Mod 元数据仓库：负责读取 gamedata/mods/<modId>/mod.json。
 */
public class ModMetadataRepository {

    private static final String MODS_ROOT_PATH = "../gamedata/mods/";
    private static final String MOD_METADATA_FILENAME = "mod.json";

    private final ObjectMapper mapper;

    public ModMetadataRepository() {
        this.mapper = new ObjectMapper();
    }

    public ModMetadata loadOrDefault(String modId) {
        if (modId == null || modId.isBlank()) {
            return ModMetadata.createDefault("unknown");
        }

        FileHandle metadataFile = Gdx.files.local(MODS_ROOT_PATH + modId + "/" + MOD_METADATA_FILENAME);
        if (!metadataFile.exists()) {
            return ModMetadata.createDefault(modId);
        }

        try {
            ModMetadata meta = mapper.readValue(metadataFile.read(), ModMetadata.class);
            if (meta.modId == null || meta.modId.isBlank()) {
                meta.modId = modId;
            }
            if (meta.name == null || meta.name.isBlank()) {
                meta.name = modId;
            }
            if (meta.version == null) {
                meta.version = "0.0.0";
            }
            if (meta.compatibleGameVersion == null) {
                meta.compatibleGameVersion = "";
            }
            if (meta.description == null) {
                meta.description = "";
            }
            return meta;
        } catch (Exception e) {
            Gdx.app.error("ModMetadataRepository", "Failed to parse " + metadataFile.path() + ", using defaults.", e);
            return ModMetadata.createDefault(modId);
        }
    }
}
