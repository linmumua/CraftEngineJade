package cn.linmumua.cejade;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 客户端初始化：在资源重载时重建索引。 */
public final class CeJadeClient implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(Ids.MOD_ID);

    @Override
    public void onInitializeClient() {
        // 打一行版本号：换 jar 后忘记重启游戏时，可以靠这行确认实际加载的是哪一版
        FabricLoader.getInstance().getModContainer(Ids.MOD_ID).ifPresent(container ->
                LOG.info("[{}] 已加载版本 {}", Ids.MOD_ID,
                        container.getMetadata().getVersion().getFriendlyString()));

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new CePackIndex());
    }
}
