package cn.linmumua.cejade;

import net.minecraft.resources.Identifier;

/** 全局标识符常量，避免与其他附属的 Jade UID 冲突。 */
public final class Ids {
    public static final String MOD_ID = "cejade";

    private Ids() {
    }

    public static Identifier of(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    // 资源重载监听
    public static final Identifier PACK_INDEX = of("pack_index");

    // Jade provider UID
    public static final Identifier ICON = of("icon");
    public static final Identifier TOOLTIP = of("tooltip");
    public static final Identifier CLEANUP = of("cleanup");

    // Tooltip 行 UID
    public static final Identifier LINE_ID = of("line_id");
    public static final Identifier LINE_STATE = of("line_state");

    // 配置项 UID
    public static final Identifier CONFIG_ENABLED = of("enabled");
}
