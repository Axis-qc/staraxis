package staraxis.lwjgl3.test;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

import java.util.ArrayList;
import java.util.List;

/**
 * ShaderCompileTest（GLSL 着色器编译验证）。
 *
 * 用 GL 上下文编译 assets/shaders/ 下的着色器，验证多种材质 flag 组合均能编译通过。
 * Java 编译检查不了 GLSL，改 shader 后必须跑本测试。
 *
 * 运行方式：`test\shader_test.bat`（一键脚本）。
 *
 * 覆盖组合：
 * - 完整舰船：diffuse + normal + specular 贴图 + playerColor（星噬者模型实际组合）
 * - 无高光：diffuse + normal（无 specular 贴图）
 * - 纯贴图：仅 diffuse
 * - 纯颜色：无任何贴图
 * - 无光照：无 environment 时的降级路径
 *
 * 任一组合编译失败即退出码非 0，并输出 GLSL 编译日志。
 */
public class ShaderCompileTest {

    /** 待验证的 vertex shader 文件（相对 assets 根目录）。 */
    private static final String VERTEX_PATH = "shaders/ship.vertex.glsl";

    /** 待验证的 fragment shader 文件（相对 assets 根目录）。 */
    private static final String FRAGMENT_PATH = "shaders/ship.fragment.glsl";

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("ShaderCompileTest");
        config.setWindowedMode(64, 64);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override
            public void create() {
                String vertex = Gdx.files.internal(VERTEX_PATH).readString();
                String fragment = Gdx.files.internal(FRAGMENT_PATH).readString();

                List<String> combos = buildCombos();
                boolean allOk = true;

                for (String combo : combos) {
                    ShaderProgram program = new ShaderProgram(combo + vertex, combo + fragment);
                    if (program.isCompiled()) {
                        System.out.println("[OK] " + describe(combo));
                    } else {
                        allOk = false;
                        System.out.println("[FAIL] " + describe(combo));
                        System.out.println("----------------------------------------");
                        System.out.println(program.getLog());
                        System.out.println("----------------------------------------");
                        System.out.println("Prefix:");
                        System.out.println(combo);
                    }
                    program.dispose();
                }

                System.out.println("=== RESULT: " + (allOk ? "ALL PASS" : "SOME FAILED") + " ===");
                if (allOk) {
                    Gdx.app.exit();
                } else {
                    // 抛异常让进程以非零码退出（gradle 任务失败），退出码判定比 System.exit 可靠
                    throw new RuntimeException("Shader compile test FAILED, see logs above");
                }
            }
        }, config);
    }

    /** 构造所有要验证的 flag 组合（prefix）。 */
    private static List<String> buildCombos() {
        List<String> combos = new ArrayList<>();

        String base = ""
                + "#define positionFlag\n"
                + "#define normalFlag\n"
                + "#define tangentFlag\n"
                + "#define lightingFlag\n"
                + "#define ambientCubemapFlag\n"
                + "#define numDirectionalLights 2\n"
                + "#define numPointLights 0\n"
                + "#define numSpotLights 0\n"
                + "#define texCoord0Flag\n";

        // 完整舰船：diffuse + normal + specular 贴图 + playerColor
        combos.add(base
                + "#define diffuseTextureFlag\n"
                + "#define diffuseColorFlag\n"
                + "#define normalTextureFlag\n"
                + "#define specularTextureFlag\n"
                + "#define playerColorFlag\n");

        // 无高光：diffuse + normal（无 specular 贴图）
        combos.add(base
                + "#define diffuseTextureFlag\n"
                + "#define diffuseColorFlag\n"
                + "#define normalTextureFlag\n"
                + "#define playerColorFlag\n");

        // 纯贴图：仅 diffuse
        combos.add(base
                + "#define diffuseTextureFlag\n"
                + "#define diffuseColorFlag\n");

        // 纯颜色：无贴图无 playerColor
        combos.add(base);

        // 无光照降级路径
        combos.add(""
                + "#define positionFlag\n"
                + "#define normalFlag\n"
                + "#define diffuseTextureFlag\n"
                + "#define diffuseColorFlag\n");

        return combos;
    }

    /** 简短描述组合（提取 #define 行用于日志输出）。 */
    private static String describe(String prefix) {
        String cleaned = prefix.replace("\n", " ");
        return cleaned.trim();
    }
}
