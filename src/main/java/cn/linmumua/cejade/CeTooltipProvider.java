package cn.linmumua.cejade;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;

/**
 * 方块提示：只显示 CE 方块的名称。
 *
 * <p>本 provider 只负责「内容」，排在标题行之后、其它 provider 之前。
 * 需要移除别人行的清理工作交给 {@link CeCleanupProvider}——Jade 的 provider 按优先级
 * 升序执行，音符盒音高行（优先级 0）与模组名行（9999）在我们之后才产生，
 * 在这里 remove 是拿不到的。</p>
 *
 * <p>命中 CE 方块时移除 Jade 的载体方块名（如「音符盒」「绊线」），
 * 否则提示框标题会是载体名称而不是 CE 方块名。</p>
 *
 * <p>只显示名称：不追加「命名空间:ID」，也不追加载体方块状态（instrument/note/powered 之类）。
 * 后者是载体方块的属性、不是 CE 方块的语义状态，对玩家没有意义。</p>
 */
public final class CeTooltipProvider implements IBlockComponentProvider {
    @Override
    public Identifier getUid() {
        return Ids.TOOLTIP;
    }

    /**
     * 紧跟在 Jade 的标题行（ObjectNameProvider，-10100）之后执行：
     * 既能移除刚加进来的载体方块名，又能保证自己的名称行出现在提示框靠上位置。
     */
    @Override
    public int getDefaultPriority() {
        return -10000;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!config.get(Ids.CONFIG_ENABLED)) {
            return;
        }
        CeBlockInfo info = CePackIndex.lookup(accessor.getBlockState());
        if (info == null) {
            return; // 未命中：完全交还 Jade 原版处理
        }

        tooltip.remove(JadeIds.CORE_OBJECT_NAME);

        String key = translationKeyOf(info);
        // 命不中翻译就退回「命名空间:ID」当名称，反正只显示这一行
        tooltip.add(key != null ? Component.translatable(key) : Component.literal(info.blockId()));
    }

    /** 命中翻译键时返回该键，否则返回 null。 */
    private static String translationKeyOf(CeBlockInfo info) {
        Language language = Language.getInstance();
        for (String key : info.translationCandidates()) {
            if (language.has(key)) {
                return key;
            }
        }
        return null;
    }
}
