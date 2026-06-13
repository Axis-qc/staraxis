package staraxis.webnet.core;

import java.io.File;

import io.undertow.Handlers;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;

/**
 * WebUiRoutes
 *
 * 作用：
 * - 负责静态资源托管逻辑（Vue 前端构建产物）。
 * - 将 /webui/** 映射到本地 webui/ 目录。
 * - 处理根路径 / 跳转到 /webui/。
 *
 * 使用方式：
 * - 在 WebNetServer 中调用 register(pathHandler) 挂载路由。
 */
public class WebUiRoutes {

    /**
     * 查找 webui 目录的路径喵。
     * 支持多个可能的目录位置：
     * 1. 项目根目录下的 webui/（默认预期位置）
     * 2. StarAxis Game/webui/（构建输出目录）
     * 3. 如果都不存在，返回 null
     *
     * @return 找到的 webui 目录，或 null
     */
    public static File findWebUiDir() {
        // 1. 项目根目录下的 webui
        File dir1 = new File("webui");
        if (dir1.exists() && dir1.isDirectory()) {
            WebNetLog.log("WebUiRoutes: found webui directory at 'webui/' (absolute: " + dir1.getAbsolutePath() + ")");
            return dir1;
        }

        // 2. StarAxis Game/webui（构建输出目录）
        File dir2 = new File("StarAxis Game/webui");
        if (dir2.exists() && dir2.isDirectory()) {
            WebNetLog.log("WebUiRoutes: found webui directory at 'StarAxis Game/webui/' (absolute: " + dir2.getAbsolutePath() + ")");
            return dir2;
        }

        // 3. 都不存在
        WebNetLog.log("WebUiRoutes: webui directory not found in any expected location");
        return null;
    }

    /**
     * 注册静态资源与跳转路由喵。
     *
     * @param pathHandler Undertow 的路径分发器。
     */
    public static void register(PathHandler pathHandler) {
        File webuiDir = findWebUiDir();

        if (webuiDir == null) {
            // 如果 webui 目录不存在，记录警告但不注册路由
            // 在开发模式下，前端通过独立的 dev server 运行
            WebNetLog.log("WebUI directory not found, skipping static resource registration. "
                    + "Expected locations: 'webui/' or 'StarAxis Game/webui/'");
            return;
        }

        // 静态资源 Handler：托管本地 webui 目录
        ResourceHandler webuiHandler = Handlers.resource(new FileResourceManager(webuiDir, 1024))
                .setDirectoryListingEnabled(false);

        // SPA fallback：文件不存在时返回 index.html，支持 Vue Router 的前端路由喵
        HttpHandler spaHandler = new HttpHandler() {
            private final ResourceHandler delegate = webuiHandler;

            @Override
            public void handleRequest(HttpServerExchange exchange) throws Exception {
                // 先尝试正常文件查找
                String relativePath = exchange.getRelativePath();
                File targetFile = new File(webuiDir, relativePath);
                if (targetFile.exists() && targetFile.isFile()) {
                    delegate.handleRequest(exchange);
                } else {
                    // 文件不存在，返回 index.html（SPA 路由由前端处理）喵
                    exchange.setRelativePath("/index.html");
                    delegate.handleRequest(exchange);
                }
            }
        };

        // 1. 挂载 /webui/**（带 SPA fallback）喵
        pathHandler.addPrefixPath("/webui", spaHandler);

        // 2. 挂载根路径跳转 / -> /webui/
        pathHandler.addExactPath("/", Handlers.redirect("/webui/"));
    }
}
