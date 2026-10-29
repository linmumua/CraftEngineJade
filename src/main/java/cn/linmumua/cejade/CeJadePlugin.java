package cn.linmumua.cejade;

import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;

/** Jade 插件入口：注册图标与文本 provider。 */
public final class CeJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addConfig(Ids.CONFIG_ENABLED, true);

        // 注册给所有方块，内部只在命中 CE 索引时输出，未命中即完全交还原版行为
        registration.registerBlockIcon(new CeIconProvider(), Block.class);
        registration.registerBlockComponent(new CeTooltipProvider(), Block.class);
        // 清理必须排在最后，单独一个 provider
        registration.registerBlockComponent(new CeCleanupProvider(), Block.class);
    }
}
