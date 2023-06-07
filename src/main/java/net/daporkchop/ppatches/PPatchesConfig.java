package net.daporkchop.ppatches;

import com.google.common.collect.ImmutableSortedMap;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * @author DaPorkchop_
 */
@UtilityClass
public class PPatchesConfig {
    public static final Configuration CONFIGURATION;

    @Config.Comment({
            "Patches FoamFix to optimize the algorithm used for blending between frames of animated textures with interpolation enabled, such as lava or command blocks.",
            "This is unlikely to give any meaningful performance benefits.",
    })
    public static final BaseModule foamFix_optimizeTextureInterpolation = new BaseModule();

    @Config.Comment({
            "Patches FoamFix to make OptiFine's \"Smart Animations\" work.",
            "Without this patch, OptiFine's \"Smart Animations\" will have no effect if FoamFix is installed.",
    })
    public static final BaseModule foamFix_respectOptiFineSmartAnimations = new BaseModule();

    @Config.Comment({
            "Patches JourneyMap to prevent it from rendering a tooltip for every widget on the screen, regardless of whether or not the mouse is hovering over the widget"
            + " in question.",
            "This will have the most effect when using a mod which adds lots of widgets to JourneyMap, such as FTB Utilities' chunk claiming.",
            "In a test world with JourneyMap's minimap set to size 30, and all visible chunks claimed using FTB Utilities, this produced roughly a 3.5x speedup in"
            + " JourneyMap map rendering (from ~60% of the total frame time to ~15%).",
    })
    public static final BaseModule journeyMap_skipRenderingOffscreenTooltips = new BaseModule();

    @Config.Comment({
            "Patches JustPlayerHeads to make it be able to retrieve player skins again.",
            "Somewhere around February 2023, Mojang changed their API to return formatted JSON strings instead of minified ones. For some reason, JustPlayerHeads"
            + " retrieves player UUID and skin data by polling the Mojang API and then \"parsing\" the JSON by splitting on double-quotes, which no longer worked as"
            + " expected when the API data format was changed. This patch makes JustPlayerHeads use Minecraft's built-in player profile cache to retrieve skin data"
            + " without using Mojang's API directly (and also ends up being quite a bit faster as a result, since 99% of the time the skin data is already cached locally!)",
    })
    public static final BaseModule justPlayerHeads_fixSkinRetrieval = new BaseModule();

    @Config.Comment({
            "Patches Minecraft's font renderer to group together entire strings and send them to the GPU at once, instead of drawing each letter individually.",
            "Whether or not this will give a performance increase depends on your GPU driver. AMD GPUs appear to benefit the most from this, have an FPS increase "
            + " of roughly 5% when the F3 menu is open.",
    })
    public static final BaseModule vanilla_fontRendererBatching = new BaseModule();

    @Config.Comment({
            "Patches Minecraft's font renderer to reset the text style when drawing text with a shadow.",
            "Without this, text formatting from the end of the string will carry over to the front of the text unless it contains an explicit style reset sequence at the"
            + " beginning or end.",
            "This has no meaningful performance impact.",
    })
    public static final BaseModule vanilla_fontRendererFixStyleResetShadow = new BaseModule();

    @Config.Comment({
            "Patches Minecraft's Tessellator to use an alternative technique for sending draw commands to the GPU, which may be more efficient on some systems.",
            "The tessellator is used for drawing many parts of the GUI (such as backgrounds, items in the inventory, and some tile entities), as well as for text if"
            + " PPatches' \"fontRendererBatching\" module is enabled.",
            "Whether or not this will give a performance increase depends on your GPU driver, and on some drivers it may cause visual bugs. NVIDIA GPUs in particular"
            + " seem to get roughly 10-15% higher FPS without any noticeable issues, however AMD's driver seems to glitch out most of the time.",
    })
    public static final BaseModule vanilla_optimizeTessellatorDraw = new BaseModule();

    @Config.Comment({
            "Patches Minecraft's World class to cache its hash code, instead of using Java's default implementation.",
            "For some reason, the default Object#hashCode() implementation appears to be very slow in some circumstances, even though it just delegates to"
            + " System.identityHashCode(Object). This causes very bad performance anywhere a World is used as a key in a map (such as in"
            + " MinecraftForgeClient.getRegionRenderCache(World, BlockPos), which is typically called once per tile entity per frame by TESR rendering code).",
            "In a test world containing roughly 1000 OpenBlocks fans, this made MinecraftForgeClient#getRegionRenderCache(World, BlockPos) about 50x faster (from ~76% of"
            + " the total frame time to ~1.5%). Your mileage may vary, however even in the worst case this patch should have no effect (enabling it won't make your game"
            + " run slower).",
    })
    public static final BaseModule vanilla_optimizeWorldHashing = new BaseModule();

    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @Data
    public static class BaseModule implements IModuleConfig {
        @Config.RequiresMcRestart
        public boolean enabled = false;
    }

    private static String getName(String fallback, AnnotatedElement element) {
        Config.Name annotation = element.getAnnotation(Config.Name.class);
        return annotation != null ? annotation.value() : fallback;
    }

    private static String getComment(AnnotatedElement element) {
        Config.Comment annotation = element.getAnnotation(Config.Comment.class);
        return annotation != null ? String.join("\n", annotation.value()) : null;
    }

    private static String getLangKey(String fallback, AnnotatedElement element) {
        Config.LangKey annotation = element.getAnnotation(Config.LangKey.class);
        return annotation != null ? annotation.value() : PPatchesMod.MODID + '.' + fallback;
    }

    private static boolean requiresWorldRestart(AnnotatedElement element) {
        return element.isAnnotationPresent(Config.RequiresWorldRestart.class);
    }

    private static boolean requiresMcRestart(AnnotatedElement element) {
        return element.isAnnotationPresent(Config.RequiresMcRestart.class);
    }

    @SneakyThrows(ReflectiveOperationException.class)
    public synchronized static void load() {
        for (Field field : PPatchesConfig.class.getDeclaredFields()) {
            Object value = field.get(null);
            if (value instanceof IModuleConfig) {
                String categoryName = field.getName().replace('_', '.');
                ConfigCategory category = CONFIGURATION.getCategory(categoryName);

                category.setComment(getComment(field));
                category.setLanguageKey(getLangKey(categoryName, field));
                category.setRequiresWorldRestart(requiresWorldRestart(field));
                category.setRequiresMcRestart(requiresMcRestart(field));

                ((IModuleConfig) value).loadFromConfig(CONFIGURATION, categoryName);
            }
        }

        CONFIGURATION.save();
    }

    @SneakyThrows(ReflectiveOperationException.class)
    public static ImmutableSortedMap<String, IModuleConfig> listModules() {
        ImmutableSortedMap.Builder<String, IModuleConfig> builder = ImmutableSortedMap.naturalOrder();
        for (Field field : PPatchesConfig.class.getDeclaredFields()) {
            Object value = field.get(null);
            if (value instanceof IModuleConfig) {
                builder.put(field.getName().replace('_', '.'), (IModuleConfig) value);
            }
        }
        return builder.build();
    }

    /**
     * @author DaPorkchop_
     */
    public interface IModuleConfig {
        boolean isEnabled();

        @SneakyThrows(ReflectiveOperationException.class)
        default void loadFromConfig(Configuration configuration, String category) {
            for (Field field : this.getClass().getFields()) {
                Class<?> type = field.getType();
                String name = getName(field.getName(), field);

                Property property;
                if (configuration.hasKey(category, name)) {
                    //the field has already been loaded once, use the existing property (we want to avoid setting the default value, as the field's
                    //  current value may no longer be the default)
                    property = configuration.getCategory(category).get(name);
                } else {
                    if (type == boolean.class) {
                        property = configuration.get(category, name, field.getBoolean(this));
                    } else if (type == int.class) {
                        property = configuration.get(category, name, field.getInt(this));
                    } else if (type == double.class) {
                        property = configuration.get(category, name, field.getDouble(this));
                    } else if (type == String.class) {
                        property = configuration.get(category, name, (String) field.get(this));
                    } else if (type.isEnum()) {
                        property = configuration.get(category, name, ((Enum<?>) field.get(this)).name());

                        Enum<?>[] values = (Enum<?>[]) type.getEnumConstants();
                        String[] names = new String[values.length];
                        for (int i = 0; i < values.length; i++) {
                            names[i] = values[i].name();
                        }
                        property.setValidValues(names);
                    } else {
                        throw new IllegalStateException("don't know how to handle " + field);
                    }
                }

                String comment = getComment(field);
                StringBuilder commentBuilder = new StringBuilder();
                if (comment != null) {
                    commentBuilder.append(comment).append('\n');
                }
                commentBuilder.append("[default: ").append(property.isList() ? Arrays.toString(property.getDefaults()) : property.getDefault()).append(']');
                if (property.getValidValues() != null && property.getValidValues().length != 0) {
                    commentBuilder.append("\nAccepted values:");
                    for (String validValue : property.getValidValues()) {
                        commentBuilder.append("\n- ").append(validValue);
                    }
                }

                property.setComment(commentBuilder.toString());
                property.setLanguageKey(getLangKey(category + '.' + name, field));
                property.setRequiresWorldRestart(requiresMcRestart(field));
                property.setRequiresMcRestart(requiresMcRestart(field));

                {
                    Config.RangeInt annotation = field.getAnnotation(Config.RangeInt.class);
                    if (annotation != null) {
                        if (type != int.class) {
                            throw new IllegalStateException(Config.RangeInt.class + " cannot be applied to " + field);
                        }
                        property.setMinValue(annotation.min());
                        property.setMaxValue(annotation.max());
                    }
                }
                {
                    Config.RangeDouble annotation = field.getAnnotation(Config.RangeDouble.class);
                    if (annotation != null) {
                        if (type != double.class) {
                            throw new IllegalStateException(Config.RangeDouble.class + " cannot be applied to " + field);
                        }
                        property.setMinValue(annotation.min());
                        property.setMaxValue(annotation.max());
                    }
                }

                //configure this instance's values
                if (type == boolean.class) {
                    field.setBoolean(this, property.getBoolean());
                } else if (type == int.class) {
                    field.setInt(this, property.getInt());
                } else if (type == double.class) {
                    field.setDouble(this, property.getDouble());
                } else if (type == String.class) {
                    field.set(this, property.getString());
                } else if (type.isEnum()) {
                    //noinspection unchecked
                    field.set(this, Enum.valueOf((Class<Enum>) type, property.getString()));
                } else {
                    throw new IllegalStateException("don't know how to handle " + field);
                }
            }
        }
    }

    static { //this needs to be at the end of the class
        CONFIGURATION = new Configuration(new File("config", "ppatches.cfg"), true);
        load();
    }
}
