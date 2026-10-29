package cn.linmumua.cejade;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.ui.Element;

/** 把方块图集里的一张 sprite 画成 Jade 图标。 */
public final class TextureIconElement extends Element {
    private static final int SIZE = 16;

    private final Identifier texture;

    public TextureIconElement(Identifier texture) {
        this.texture = texture;
        // 直接写 protected 字段：Element#size() 返回的是包装对象，不会改到本实例
        this.width = SIZE;
        this.height = SIZE;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {
        TextureAtlasSprite sprite = graphics.getSprite(new SpriteId(TextureAtlas.LOCATION_BLOCKS, texture));
        if (sprite == null) {
            return;
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, SIZE, SIZE);
    }

    @Override
    public Component getNarration() {
        return Component.empty();
    }
}
