package cn.linmumua.cejade;

import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.Element;

/**
 * 方块图标：有 item 定义就用原版物品管线画 3D 等距模型，没有才退回模型贴图平铺。
 *
 * <p>判定顺序固定为 item model → 贴图 → 保留原图标，具体绘制见 {@link ModelIconElement}。</p>
 */
public final class CeIconProvider implements IComponentProvider<BlockAccessor> {
    @Override
    public Identifier getUid() {
        return Ids.ICON;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        // 只负责图标，文本由 CeTooltipProvider 处理
    }

    @Override
    public Element getIcon(BlockAccessor accessor, IPluginConfig config, Element current) {
        if (!config.get(Ids.CONFIG_ENABLED)) {
            return current;
        }
        CeBlockInfo info = CePackIndex.lookup(accessor.getBlockState());
        if (info == null) {
            return current;
        }
        if (info.itemModel() == null && !info.hasAnyTexture()) {
            // 既没有 item model 也没有贴图（展示实体渲染的方块）就保留原图标
            return current;
        }
        return new ModelIconElement(info.itemModel(), info.side(), info.top());
    }
}
