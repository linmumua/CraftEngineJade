package cn.linmumua.cejade;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 从当前已加载的资源包建立「载体方块 + 视觉状态属性 → CE 外观」索引。
 * CE 改包后由资源重载自动重建，无需服务端参与。
 */
public final class CePackIndex implements IdentifiableResourceReloadListener, ResourceManagerReloadListener {
    private static final Logger LOG = LoggerFactory.getLogger(Ids.MOD_ID);

    private record Snapshot(Map<Block, Map<String, CeBlockInfo>> exact, Map<Block, CeBlockInfo> loose) {
        static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of());
    }

    private static final AtomicReference<Snapshot> SNAPSHOT = new AtomicReference<>(Snapshot.EMPTY);
    private static final Map<Identifier, Identifier> TEXTURE_CACHE = new HashMap<>();

    @Override
    public Identifier getFabricId() {
        return Ids.PACK_INDEX;
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        try {
            SNAPSHOT.set(build(manager));
            LOG.info("[{}] 索引完成：{}", Ids.MOD_ID, describe());
        } catch (Throwable t) {
            LOG.warn("[{}] 资源包索引构建失败，将不显示任何提示", Ids.MOD_ID, t);
            SNAPSHOT.set(Snapshot.EMPTY);
        }
    }

    /** 查询方块状态对应的 CE 外观；未命中返回 null。 */
    public static CeBlockInfo lookup(BlockState state) {
        Snapshot snapshot = SNAPSHOT.get();
        Map<String, CeBlockInfo> variants = snapshot.exact().get(state.getBlock());
        if (variants != null) {
            CeBlockInfo info = variants.get(propertyKey(state));
            if (info != null) {
                return info;
            }
        }
        return snapshot.loose().get(state.getBlock());
    }

    public static String describe() {
        Snapshot s = SNAPSHOT.get();
        int exact = s.exact().values().stream().mapToInt(Map::size).sum();
        return "exact=" + exact + " blocks=" + s.loose().size();
    }

    // ---------------------------------------------------------------- 解析

    private static Snapshot build(ResourceManager manager) {
        TEXTURE_CACHE.clear();
        Map<Block, Map<String, CeBlockInfo>> exact = new HashMap<>();
        Map<Block, CeBlockInfo> loose = new HashMap<>();

        Map<Identifier, Resource> files = manager.listResources("blockstates", id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> entry : files.entrySet()) {
            Identifier file = entry.getKey();
            String name = strip(file.getPath(), "blockstates/", ".json");
            if (name.isEmpty()) {
                continue;
            }
            Block carrier = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(file.getNamespace(), name));
            if (carrier == null) {
                continue;
            }

            JsonObject root = readJson(entry.getValue());
            if (root == null) {
                continue;
            }

            Map<String, CeBlockInfo> fileExact = new HashMap<>();
            CeBlockInfo fileLoose = null;
            int propertyCount = carrier.getStateDefinition().getProperties().size();

            JsonObject variants = asObject(root.get("variants"));
            if (variants != null) {
                for (Map.Entry<String, JsonElement> variant : variants.entrySet()) {
                    Identifier model = pickModel(variant.getValue());
                    if (model == null || isVanillaDefault(file.getNamespace(), name, model)) {
                        continue;
                    }
                    Map<String, String> props = parseVariantKey(variant.getKey());
                    CeBlockInfo info = create(manager, model, props);
                    if (props.size() == propertyCount) {
                        fileExact.put(key(props), info);
                    }
                    if (fileLoose == null) {
                        fileLoose = create(manager, model, null);
                    }
                }
            } else {
                Identifier model = firstApplyModel(asObject(root.get("multipart")));
                if (model != null && !isVanillaDefault(file.getNamespace(), name, model)) {
                    fileLoose = create(manager, model, null);
                }
            }

            if (!fileExact.isEmpty()) {
                exact.put(carrier, fileExact);
            }
            if (fileLoose != null) {
                loose.put(carrier, fileLoose);
            }
        }
        return new Snapshot(exact, loose);
    }

    private static CeBlockInfo create(ResourceManager manager, Identifier model, Map<String, String> variant) {
        String path = model.getPath();
        if (path.startsWith("block/")) {
            path = path.substring("block/".length());
        }
        Identifier[] faces = facesOf(manager, model);
        Identifier itemModel = itemModelOf(manager, model.getNamespace(), path);
        return new CeBlockInfo(model, model.getNamespace(), path, faces[0], faces[1], itemModel, variant);
    }

    /**
     * 资源包为方块生成了 item model 时返回它的 id（可直接塞进 {@code item_model} 组件）。
     *
     * <p>CE 会为方块生成 {@code assets/<ns>/items/<方块>.json}。注意变体模型通常没有
     * 自己的 item 定义——例如 {@code block/gloomwood_dark_log_horizontal} 存在，但
     * {@code items/gloomwood_dark_log_horizontal.json} 不存在，只有基础名那一份。
     * 所以这里复用名称解析的剥后缀候选，让变体也能拿到基础方块的 item model。</p>
     */
    private static Identifier itemModelOf(ResourceManager manager, String namespace, String path) {
        for (String candidate : CeBlockInfo.pathCandidates(path)) {
            Identifier file = Identifier.fromNamespaceAndPath(namespace, "items/" + candidate + ".json");
            if (manager.getResource(file).isPresent()) {
                return Identifier.fromNamespaceAndPath(namespace, candidate);
            }
        }
        return null;
    }

    /** 沿 parent 链查找模型贴图，返回 [侧面, 顶面]（方块图集 sprite 名）。 */
    private static Identifier[] facesOf(ResourceManager manager, Identifier model) {
        Identifier side = null;
        Identifier top = null;
        Identifier current = model;
        for (int depth = 0; depth < 5 && current != null; depth++) {
            JsonObject json = readModel(manager, current);
            if (json == null) {
                break;
            }
            JsonObject textures = asObject(json.get("textures"));
            if (textures != null) {
                if (side == null) {
                    side = pickTexture(textures, "side", "all", "particle", "texture", "cross", "0");
                }
                if (top == null) {
                    top = pickTexture(textures, "top", "all", "particle");
                }
            }
            if (side != null && top != null) {
                break;
            }
            JsonElement parent = json.get("parent");
            current = parent != null && parent.isJsonPrimitive() ? Identifier.tryParse(parent.getAsString()) : null;
        }
        return new Identifier[]{side, top != null ? top : side};
    }

    private static Identifier pickTexture(JsonObject textures, String... keys) {
        for (String key : keys) {
            JsonElement value = textures.get(key);
            if (value == null || !value.isJsonPrimitive()) {
                continue;
            }
            String raw = value.getAsString();
            if (raw.startsWith("#")) {
                JsonElement referenced = textures.get(raw.substring(1));
                if (referenced == null || !referenced.isJsonPrimitive()) {
                    continue;
                }
                raw = referenced.getAsString();
                if (raw.startsWith("#")) {
                    continue;
                }
            }
            Identifier id = Identifier.tryParse(raw);
            if (id == null) {
                continue;
            }
            // 归一化为方块图集里的 sprite 名，例如 betternet:block/mushroom_grass
            String path = id.getPath();
            if (path.endsWith(".png")) {
                path = path.substring(0, path.length() - ".png".length());
            }
            if (path.startsWith("textures/")) {
                path = path.substring("textures/".length());
            }
            return Identifier.fromNamespaceAndPath(id.getNamespace(), path);
        }
        return null;
    }

    private static JsonObject readModel(ResourceManager manager, Identifier model) {
        Identifier path = Identifier.fromNamespaceAndPath(model.getNamespace(), "models/" + model.getPath() + ".json");
        Optional<Resource> resource = manager.getResource(path);
        return resource.map(CePackIndex::readJson).orElse(null);
    }

    // ---------------------------------------------------------------- 工具

    private static JsonObject readJson(Resource resource) {
        try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Identifier pickModel(JsonElement element) {
        if (element == null) {
            return null;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                Identifier id = pickModel(child);
                if (id != null) {
                    return id;
                }
            }
            return null;
        }
        JsonObject obj = asObject(element);
        if (obj == null) {
            return null;
        }
        JsonElement model = obj.get("model");
        return model != null && model.isJsonPrimitive() ? Identifier.tryParse(model.getAsString()) : null;
    }

    private static Identifier firstApplyModel(JsonObject multipart) {
        if (multipart == null) {
            return null;
        }
        JsonElement parts = multipart.get("parts");
        if (parts == null || !parts.isJsonArray()) {
            return null;
        }
        for (JsonElement part : parts.getAsJsonArray()) {
            JsonObject obj = asObject(part);
            if (obj == null) {
                continue;
            }
            JsonObject apply = asObject(obj.get("apply"));
            if (apply == null) {
                continue;
            }
            JsonElement model = apply.get("model");
            if (model != null && model.isJsonPrimitive()) {
                return Identifier.tryParse(model.getAsString());
            }
        }
        return null;
    }

    /** 判断是否只是原版默认模型（即该状态未被资源包覆盖）。 */
    private static boolean isVanillaDefault(String namespace, String blockName, Identifier model) {
        return "minecraft".equals(namespace)
                && "minecraft".equals(model.getNamespace())
                && ("block/" + blockName).equals(model.getPath());
    }

    private static String propertyKey(BlockState state) {
        Map<String, String> props = new TreeMap<>();
        for (Property<?> property : state.getProperties()) {
            props.put(property.getName(), valueName(state, property));
        }
        return key(props);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueName(BlockState state, Property property) {
        Comparable value = state.getValue(property);
        return property.getName(value);
    }

    private static String key(Map<String, String> props) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(props).entrySet()) {
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append(',');
        }
        return sb.toString();
    }

    private static Map<String, String> parseVariantKey(String key) {
        Map<String, String> props = new TreeMap<>();
        if (key == null || key.isBlank()) {
            return props;
        }
        for (String pair : key.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                props.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return props;
    }

    private static JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String strip(String value, String prefix, String suffix) {
        String result = value;
        if (result.startsWith(prefix)) {
            result = result.substring(prefix.length());
        }
        if (result.endsWith(suffix)) {
            result = result.substring(0, result.length() - suffix.length());
        }
        return result;
    }
}
