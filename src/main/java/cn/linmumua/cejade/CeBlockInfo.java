package cn.linmumua.cejade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    /**
     * 候选翻译键，按可信度排列。
     *
     * <p>同一个 CE 方块的不同朝向/连接状态会各自生成一个模型，例如
     * {@code block/gloomwood_dark_log} 与 {@code block/gloomwood_dark_log_horizontal}，
     * 但翻译键只有 {@code block.<ns>.gloomwood_dark_log} 一个。
     * 所以完整路径查不到时，逐级剥掉末尾的 {@code _} 段再试
     * （实测本地资源包中 689 个无键模型里 661 个能靠这一步命中）。</p>
     */
    public String[] translationCandidates() {
        List<String> keys = new ArrayList<>();
        for (String candidate : pathCandidates(path)) {
            int slash = candidate.lastIndexOf('/');
            String head = slash < 0 ? "" : candidate.substring(0, slash + 1).replace('/', '.');
            String base = slash < 0 ? candidate : candidate.substring(slash + 1);
            keys.add("block." + namespace + "." + head + base);
            keys.add("block." + namespace + "." + base);
        }
        keys.add("item." + namespace + "." + path.replace('/', '.'));
        return keys.toArray(new String[0]);
    }

    /**
     * 模型路径的候选序列：完整路径在前，随后逐级剥掉末尾 {@code _} 段。
     *
     * <p>同一 CE 方块的不同朝向/连接状态会各自生成模型，例如
     * {@code gloomwood_dark_log} 与 {@code gloomwood_dark_log_horizontal}，
     * 但翻译键与 item 定义都只有基础名一份。名称解析与 item 定义查找共用这套候选。</p>
     *
     * <p>最多剥 4 段：再短会撞到毫不相关的方块名。</p>
     */
    public static List<String> pathCandidates(String path) {
        List<String> candidates = new ArrayList<>();
        int slash = path.lastIndexOf('/');
        String head = slash < 0 ? "" : path.substring(0, slash + 1);
        String[] parts = (slash < 0 ? path : path.substring(slash + 1)).split("_");
        int floor = Math.max(1, parts.length - 4);
        for (int n = parts.length; n >= floor; n--) {
            candidates.add(head + String.join("_", Arrays.copyOf(parts, n)));
        }
        return candidates;
    }

    /** 至少有一张贴图才能画图标。 */
    public boolean hasAnyTexture() {
        return side != null || top != null;
    }
}
