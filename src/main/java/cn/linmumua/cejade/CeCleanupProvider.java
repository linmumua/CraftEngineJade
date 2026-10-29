package cn.linmumua.cejade;

import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.config.IPluginConfig;

/**
 * 只做清理：移除命中 CE 方块后残留的 Jade 原版行。
 *
 * <p>为什么单独拆一个 provider：Jade 的 provider 按优先级<b>升序</b>执行，
 * 音符盒音高行来自默认优先级 0 的 {@code NoteBlockProvider}，模组名行来自优先级 9999 的
 * {@code ModNameProvider}。要在它们产生之后才移除得掉，就必须排在最后；
 * 而名称行又必须排在靠上位置，两个需求冲突，所以拆成「内容」与「清理」两个 provider。</p>
 */
public final class CeCleanupProvider implements IBlockComponentProvider {
    /** 排在 Jade 的 ModNameProvider（9999）之后，确保它那一行已经产生。 */
    private static final int PRIORITY = 10000;

    @Override
    public Identifier getUid() {
        return Ids.CLEANUP;
    }

    @Override
    public int getDefaultPriority() {
        return PRIORITY;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!config.get(Ids.CONFIG_ENABLED)) {
            return;
        }
        if (CePackIndex.lookup(accessor.getBlockState()) == null) {
            return; // 不是 CE 方块，别动 Jade 原版内容
        }
        // 音符盒音高行（如「贝斯 C#/Db」）与「Minecraft」模组名行
        tooltip.remove(JadeIds.MC_NOTE_BLOCK);
        tooltip.remove(JadeIds.CORE_MOD_NAME);
        // Jade 调试模式下的方块状态行（形如 instrument=zombie, note=16, powered=true）。
        // 那是载体方块的属性，不是 CE 方块的语义状态，对玩家没有意义。
        tooltip.remove(JadeIds.DEBUG_BLOCK_STATES);
        tooltip.remove(JadeIds.DEBUG_BLOCK_PROPERTIES);
    }
}
