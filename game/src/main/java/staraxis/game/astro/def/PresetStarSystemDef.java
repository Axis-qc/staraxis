package staraxis.game.astro.def;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * PresetStarSystemDef（预设恒星系定义）喵。
 *
 * 作用：
 * - 通过 JSON 数据定义“固定/随机放置”的预设恒星系（恒星、行星与轨道参数）。
 * - 用于剧情关键系统、母星系、教学关卡等，需要稳定可控生成结果的场景喵。
 *
 * 使用方式：
 * - 由 PresetStarSystemRepository（预设星系仓库）加载
 * assets/star_system/star-system-presets.json 喵。
 * - AstroGenerator（星体生成器）在随机生成前优先放置预设系统，并占位去重喵。
 *
 * 注意事项：
 * - 该 Def 仅描述“生成输入”，不包含运行时 ID（entityId/systemId）喵。
 * - position.mode=random 时，最终坐标由 worldSeed（世界种子）+ presetId（预设ID）确定性计算得到喵。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PresetStarSystemDef {

    public String presetId;

    public Position position;

    public SystemDef system;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Position {
        /** 放置模式：fixedSector（固定坐标）/ randomSector（按 seed 随机坐标）喵。 */
        public String mode;

        /** 固定坐标：当 mode=fixedSector 时必填喵。 */
        public Integer q;
        public Integer r;

        /** 随机放置：可选的候选半径（限制随机选择范围），为空表示全图可选喵。 */
        public Integer randomRadius;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SystemDef {
        public List<StarDef> stars = new ArrayList<>();
        public List<PlanetDef> planets = new ArrayList<>();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StarDef {
        public String starTypeId;

        public Double radiusGU;
        public Double massSolar;
        public Integer temperatureK;

        /** 可选：指定纹理路径；为空则按 StarTypeDef.spriteCandidates 确定性选择喵。 */
        public String surfaceTexturePath;

        /** 可选：所属国家ID（ownerNationId）；天体公共可见但可有归属喵。 */
        public String ownerNationId;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlanetDef {
        public String planetTypeId;

        public Double radiusGU;
        public Double rotationPeriodHours;

        /** 可选：指定纹理路径；为空则按 PlanetTypeDef.spriteCandidates 确定性选择喵。 */
        public String surfaceTexturePath;

        /** 可选：所属国家ID（ownerNationId）；天体公共可见但可有归属喵。 */
        public String ownerNationId;

        /** 轨道参数喵。 */
        public OrbitDef orbit;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OrbitDef {
        public Double semiMajorAxisGU;
        public Double eccentricity;
        public Double inclinationDeg;
        public Double periapsisArgDeg;
        public Double meanAnomalyDegAtEpoch;
    }
}
