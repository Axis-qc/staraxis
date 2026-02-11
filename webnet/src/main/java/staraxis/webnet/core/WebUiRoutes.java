package staraxis.webnet.core;

import io.undertow.Handlers;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;

import java.io.File;

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
     * 注册静态资源与跳转路由喵。
     *
     * @param pathHandler Undertow 的路径分发器。
     */
    public static void register(PathHandler pathHandler) {
        File webuiDir = new File("webui");

        // 静态资源 Handler：托管本地 webui 目录
        ResourceHandler webuiHandler = Handlers.resource(new FileResourceManager(webuiDir, 1024))
                .setDirectoryListingEnabled(false);

        // 1. 挂载 /webui/**
        pathHandler.addPrefixPath("/webui", webuiHandler);

        // 2. 挂载根路径跳转 / -> /webui/
        pathHandler.addExactPath("/", Handlers.redirect("/webui/"));
    }
}
