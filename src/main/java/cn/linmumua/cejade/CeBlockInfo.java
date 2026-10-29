package cn.linmumua.cejade;

import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 一个 CraftEngine 方块外观在客户端可解析出的信息。
 *
 * @param model     视觉状态对应的模型 id
 * @param namespace 模型命名空间，通常等于 CE 方块 id 的命名空间
 * @param path      去掉 {@code block/} 前缀后的模型路径，用于猜测 CE 方块 id
 * @param side      侧面贴图（方块图集 sprite 名），可能为 null
 * @param top       顶面贴图（方块图集 sprite 名），可能为 null
 * @param itemModel 若资源包为该方块生成了 item model，这里是它的 id（可直接塞进 item_model 组件）
 * @param variant   命中的视觉状态属性组合，未精确命中时为 null
 */
public record CeBlockInfo(
        Identifier model,
        String namespace,
        String path,
        @Nullable Identifier side,
        @Nullable Identifier top,
        @Nullable Identifier itemModel,
        @Nullable Map<String, String> variant
) {
    /** 推断出的 CE 方块 id，例如 {@code default:ruby_block}。 */
    public String blockId() {
        return namespace + ":" + path;
    }

    /**
     * 反推是否可信：CE 把模型放在 {@code minecraft:} 命名空间时（如
     * {@code minecraft:block/custom/x}），模型路径无法代表方块 id，此时不做推断。
     */
    public boolean idReliable() {
        return !"minecraft".equals(namespace);
    }

    /** 候选翻译键，按可信度排列。 */
    public String[] translationCandidates() {
        String dotted = path.replace('/', '.');
        String last = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return new String[]{
                "block." + namespace + "." + dotted,
                "block." + namespace + "." + last,
                "item." + namespace + "." + dotted
        };
    }

    /** 至少有一张贴图才能画图标。 */
    public boolean hasAnyTexture() {
        return side != null || top != null;
    }
}
