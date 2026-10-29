package cn.linmumua.cejade;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import snownee.jade.api.ui.Element;

/**
 * 方块图标。
 *
 * <p>首选：CE 会为方块生成 {@code assets/<ns>/items/<方块>.json}（item 定义），把它塞进
 * {@code item_model} 组件后交给原版物品渲染管线，就是真正的 3D 等距模型，与物品栏里的
 * 表现完全一致。带 {@code oversized_in_gui:true} 的方块由原版自己放大并居中，这里不要
 * 再叠加缩放，否则会和原版的居中计算打架。</p>
 *
 * <p>退回：没有 item model 的方块（例如展示实体渲染的方块）用模型贴图平铺。</p>
 *
 * <p>两个 26.3 的坑（都靠 javap 核实过，别凭印象改）：</p>
 * <ul>
 *   <li>{@code Element#extractRenderState} 的 {@code x}/{@code y} 是<b>鼠标坐标</b>，
 *       不是绘制原点 —— {@code OverlayRenderer} 传的是 {@code TooltipAnimation}
 *       映射后的鼠标位置，Jade 自己的 {@code ItemStackElement} 之类也是丢掉这两个
 *       参数、改用 {@code getX()}/{@code getY()} 定位。所以图标必须画在自身矩形上。</li>
 *   <li>{@code item(...)} 与 {@code fakeItem(...)} 的唯一差别是 ItemOwner：
 *       {@code item} 拿当前玩家、{@code fakeItem} 传 null。两者都只画物品本体，
 *       不画数量/耐久（那是另一个方法 {@code itemDecorations}）。预览图标用
 *       {@code fakeItem}，避免图标随玩家状态（护符/挂饰类 model 谓词）漂移。</li>
 * </ul>
 */
public final class ModelIconElement extends Element {
    /**
     * 与 Jade 自带的 {@code ItemStackElement} 保持一致：它用 {@code floor(18.0f * scale)}，
     * 留出的 1px 余量正好给 {@code oversized_in_gui} 的方块模型，避免压到右侧文字。
     */
    private static final int SIZE = 18;
    /** 固定 seed：让带随机项（随机偏移/随机变体）的 item model 每次都画出同一个图标。 */
    private static final int SEED = 0;
    private static final Logger LOG = LoggerFactory.getLogger(Ids.MOD_ID);
    private static boolean loggedFailure;

    private final Identifier itemModel;
    private final Identifier texture;
    private ItemStack stack;

    public ModelIconElement(Identifier itemModel, Identifier side, Identifier top) {
        // 该 item 定义没烘出模型时，塞进 item_model 会得到 MissingItemModel 的紫黑格，
        // 比贴图难看得多，所以先验一次；验不过就当一个纯贴图图标处理
        this.itemModel = usableItemModel(itemModel) ? itemModel : null;
        this.texture = side != null ? side : top;
        // 直接写 protected 字段：Element#size() 返回的是包装对象，不会改到本实例
        this.width = SIZE;
        this.height = SIZE;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // 注意：mouseX/mouseY 是鼠标位置，绘制原点要取自身矩形
        int x = getX();
        int y = getY();
        if (itemModel != null) {
            try {
                graphics.fakeItem(stack(), x, y, SEED);
                return;
            } catch (Exception e) {
                // 畸形 CE 模型会让 fakeItem 抛 ReportedException（带 crash report 直接崩客户端）。
                // 图标只是装饰，不值得为它中断游戏：记一次日志后退回贴图分支。
                if (!loggedFailure) {
                    loggedFailure = true;
                    LOG.warn("[{}] 渲染 item model {} 失败，退回贴图图标", Ids.MOD_ID, itemModel, e);
                }
            }
        }
        if (texture != null) {
            TextureAtlasSprite sprite = graphics.getSprite(new SpriteId(TextureAtlas.LOCATION_BLOCKS, texture));
            if (sprite != null) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, SIZE, SIZE);
            }
        }
    }

    private ItemStack stack() {
        if (stack == null) {
            ItemStack created = new ItemStack(Items.PAPER);
            created.set(DataComponents.ITEM_MODEL, itemModel);
            stack = created;
        }
        return stack;
    }

    /** item_model 组件存的是 item 定义 id（{@code assets/<ns>/items/<path>.json}），这里按同一个 id 查烘焙结果。 */
    private static boolean usableItemModel(Identifier id) {
        if (id == null) {
            return false;
        }
        return !(Minecraft.getInstance().getModelManager().getItemModel(id) instanceof MissingItemModel);
    }

    @Override
    public Component getNarration() {
        return Component.empty();
    }
}
