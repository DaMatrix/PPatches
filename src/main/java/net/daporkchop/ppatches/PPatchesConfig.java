package net.daporkchop.ppatches;

import com.google.common.collect.ImmutableSortedMap;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.daporkchop.ppatches.modules.vanilla.optimizeTessellatorDraw.ModuleConfigOptimizeTessellatorDraw;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @author DaPorkchop_
 */
@UtilityClass
@Mod.EventBusSubscriber(modid = PPatchesMod.MODID)
public class PPatchesConfig {
    public static final Configuration CONFIGURATION;
    private static ImmutableSortedMap<String, ModuleConfigBase> MODULES;

    @Config.Comment({
            "Patches CustomMainMenu to use GlStateManager instead of directly invoking glColor*.",
            "This fixes a bug that normally makes no difference, but is noticeable when using the vanilla.optimizeTessellatorDraw module. Enabling this patch should have"
            + " no performance implications.",
    })
    public static final ModuleConfigBase customMainMenu_fixRenderColors = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches FoamFix to optimize the algorithm used for blending between frames of animated textures with interpolation enabled, such as lava or command blocks.",
            "This is unlikely to give any meaningful performance benefits.",
    })
    public static final ModuleConfigBase foamFix_optimizeTextureInterpolation = new ModuleConfigBase(ModuleState.DISABLED);

    @Config.Comment({
            "Patches FoamFix to make OptiFine's \"Smart Animations\" work.",
            "Without this patch, OptiFine's \"Smart Animations\" will have no effect if FoamFix is installed.",
    })
    @ModuleDescriptor(requiredClasses = "net.optifine.SmartAnimations")
    public static final ModuleConfigBase foamFix_respectOptiFineSmartAnimations = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches JourneyMap to prevent it from rendering a tooltip for every widget on the screen, regardless of whether or not the mouse is hovering over the widget"
            + " in question.",
            "This will have the most effect when using a mod which adds lots of widgets to JourneyMap, such as FTB Utilities' chunk claiming.",
            "In a test world with JourneyMap's minimap set to size 30, and all visible chunks claimed using FTB Utilities, this produced roughly a 3.5x speedup in"
            + " JourneyMap map rendering (from ~60% of the total frame time to ~15%).",
    })
    public static final ModuleConfigBase journeyMap_skipRenderingOffscreenTooltips = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches JustPlayerHeads to make it be able to retrieve player skins again.",
            "Somewhere around February 2023, Mojang changed their API to return formatted JSON strings instead of minified ones. For some reason, JustPlayerHeads"
            + " retrieves player UUID and skin data by polling the Mojang API and then \"parsing\" the JSON by splitting on double-quotes, which no longer worked as"
            + " expected when the API data format was changed. This patch makes JustPlayerHeads use Minecraft's built-in player profile cache to retrieve skin data"
            + " without using Mojang's API directly (and also ends up being quite a bit faster as a result, since 99% of the time the skin data is already cached locally!)",
    })
    public static final ModuleConfigBase justPlayerHeads_fixSkinRetrieval = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches Mekanism to avoid triggering sky light updates in dimensions which don't have sky light.",
            "This will dramatically reduce lag spikes when loading chunks containing large numbers of Mekanism machines in dimensions which don't have sky light, as the"
            + " lighting algorithm is extremely slow at calculating sky light in these dimensions.",
    })
    public static final ModuleConfigBase mekanism_optimizeSkyLightUpdates = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches OpenBlocks to align the rotation angles of its fans to a multiple of 10° (the angle by which the fans are rotated when right-clicked).",
            "Without this patch, there is almost no way to make neighboring fans point in the same exact direction, however it is disabled by default due to the very"
            + " slim possibility that it may break existing builds which rely on extremely precise fan placement.",
            "This has no meaningful performance impact.",
    })
    public static final ModuleConfigBase openBlocks_fanAngleRounding = new ModuleConfigBase(ModuleState.DISABLED);

    @Config.Comment({
            "Patches OpenBlocks to optimize the processing of entities being pushed by fans.",
            "This can have measurable performance benefits when there are many fans present in the world.",
    })
    @ModuleDescriptor(
            requiredClasses = "openblocks.common.tileentity.TileEntityFan",
            transformerRegisterPhase = "DEFAULT",
            transformerClass = "net.daporkchop.ppatches.modules.openBlocks.fanEntityOptimization.FanEntityOptimizationTransformer")
    public static final ModuleConfigBase openBlocks_fanEntityOptimization = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches OpenBlocks to avoid re-scanning entities in the world when many fans are placed near each other in the world.",
            "This can result in very large performance improvements for server tick rate when many OpenBlocks fans are placed close to each other.",
    })
    public static final ModuleConfigBase openBlocks_fanUpdateBatching = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches OptiFine to use MethodHandles in place of Java reflection.",
            "This could give some minor performance benefits when OptiFine is installed, and will definitely help reduce GC churn.",
    })
    @ModuleDescriptor(
            requiredClasses = "net.optifine.reflect.Reflector",
            hasMixins = false,
            transformerClass = "net.daporkchop.ppatches.modules.optifine.optimizeReflector.OptimizeReflectorTransformer")
    public static final ModuleConfigBase optifine_optimizeReflector = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches Minecraft's font renderer to group together entire strings and send them to the GPU at once, instead of drawing each letter individually.",
            "Whether or not this will give a performance increase depends on your GPU driver. AMD GPUs appear to benefit the most from this, have an FPS increase "
            + " of roughly 5% when the F3 menu is open.",
    })
    @ModuleDescriptor(mixinRegisterPhase = "PREINIT")
    public static final ModuleConfigBase vanilla_fontRendererBatching = new ModuleConfigBase(ModuleState.DISABLED);

    @Config.Comment({
            "Patches Minecraft's font renderer to reset the text style when drawing text with a shadow.",
            "Without this, text formatting from the end of the string will carry over to the front of the text unless it contains an explicit style reset sequence at the"
            + " beginning or end.",
            "This has no meaningful performance impact.",
    })
    @ModuleDescriptor(mixinRegisterPhase = "PREINIT")
    public static final ModuleConfigBase vanilla_fontRendererFixStyleResetShadow = new ModuleConfigBase(ModuleState.DISABLED);

    @Config.Comment({
            "Patches Minecraft's item renderer to re-use the same vertex data when rendering items which have the same mesh.",
            "This should notably improve performance when rendering many items (generally during GUI rendering) by ~5% or more, and will definitely help reduce GC churn.",
    })
    @ModuleDescriptor(mixinRegisterPhase = "PREINIT")
    public static final ModuleConfigBase vanilla_optimizeItemRendererCacheModel = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches Minecraft's Tessellator to use an alternative technique for sending draw commands to the GPU, which may be more efficient on some systems.",
            "The tessellator is used for drawing many parts of the GUI (such as backgrounds, items in the inventory, and some tile entities), as well as for text if"
            + " PPatches' \"fontRendererBatching\" module is enabled.",
            "Whether or not this will give a performance increase depends on your GPU driver, and on some drivers it may cause visual bugs. NVIDIA GPUs in particular"
            + " seem to get roughly 10-15% higher FPS without any noticeable issues, however AMD's driver seems to glitch out most of the time.",
    })
    @ModuleDescriptor(mixinRegisterPhase = "PREINIT")
    public static final ModuleConfigOptimizeTessellatorDraw vanilla_optimizeTessellatorDraw = new ModuleConfigOptimizeTessellatorDraw(ModuleState.DISABLED);

    @Config.Comment({
            "Patches Minecraft's World class to cache its hash code, instead of using Java's default implementation.",
            "For some reason, the default Object#hashCode() implementation appears to be very slow in some circumstances, even though it just delegates to"
            + " System.identityHashCode(Object). This causes very bad performance anywhere a World is used as a key in a map (such as in"
            + " MinecraftForgeClient.getRegionRenderCache(World, BlockPos), which is typically called once per tile entity per frame by TESR rendering code).",
            "In a test world containing roughly 1000 OpenBlocks fans, this made MinecraftForgeClient#getRegionRenderCache(World, BlockPos) about 50x faster (from ~76% of"
            + " the total frame time to ~1.5%). Your mileage may vary, however even in the worst case this patch should have no effect (enabling it won't make your game"
            + " run slower).",
    })
    @ModuleDescriptor(mixinRegisterPhase = "PREINIT")
    public static final ModuleConfigBase vanilla_optimizeWorldHashing = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches all references to the isRemote field of Minecraft's World class to be hard-coded to false on the dedicated server.",
            "This will slightly improve dedicated server performance, although the improvement will be extremely difficult to measure.",
    })
    @ModuleDescriptor(
            hasMixins = false,
            transformerClass = "net.daporkchop.ppatches.modules.vanilla.optimizeWorldIsRemoteOnDedicatedServer.OptimizeWorldIsRemoteOnDedicatedServerTransformer")
    public static final ModuleConfigBase vanilla_optimizeWorldIsRemoteOnDedicatedServer = new ModuleConfigBase(ModuleState.ENABLED);

    /**
     * @author DaPorkchop_
     */
    public static class ModuleConfigBase {
        @Config.RequiresMcRestart
        public ModuleState state;

        public transient ModuleDescriptor descriptor;
        public transient Field field;

        public ModuleConfigBase(ModuleState defaultState) {
            this.state = defaultState;
        }

        @SneakyThrows(ReflectiveOperationException.class)
        public void loadFromConfig(Configuration configuration, String category, boolean init) {
            Set<String> unknownKeys = new TreeSet<>(configuration.getCategory(category).keySet());

            for (Field field : this.getClass().getFields()) {
                if ((field.getModifiers() & Modifier.TRANSIENT) != 0 || field.isAnnotationPresent(Config.Ignore.class)) { //skip ignored fields
                    continue;
                }

                Class<?> type = field.getType();
                String name = getName(field.getName(), field);
                unknownKeys.remove(name);

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
                    } else {
                        throw new IllegalStateException("don't know how to handle " + field);
                    }
                }

                if (init) {
                    if (type == boolean.class) {
                        property.setDefaultValue(field.getBoolean(this));
                    } else if (type == int.class) {
                        property.setDefaultValue(field.getInt(this));
                    } else if (type == double.class) {
                        property.setDefaultValue(field.getDouble(this));
                    } else if (type == String.class) {
                        property.setDefaultValue((String) field.get(this));
                    } else if (type.isEnum()) {
                        property.setDefaultValue(((Enum<?>) field.get(this)).name());

                        Enum<?>[] values = (Enum<?>[]) type.getEnumConstants();
                        String[] names = new String[values.length];
                        for (int i = 0; i < values.length; i++) {
                            names[i] = values[i].name();
                        }
                        property.setValidValues(names);
                    } else {
                        throw new IllegalStateException("don't know how to handle " + field);
                    }

                    property.setComment(getComment(type, property, field));
                    property.setLanguageKey(getLangKey(category + '.' + name, field));
                    property.setRequiresWorldRestart(requiresMcRestart(field));
                    property.setRequiresMcRestart(requiresMcRestart(field));

                    Config.RangeInt rangeAnnotationInt = field.getAnnotation(Config.RangeInt.class);
                    if (rangeAnnotationInt != null) {
                        if (type != int.class) {
                            throw new IllegalStateException(Config.RangeInt.class + " cannot be applied to " + field);
                        }

                        int value = property.getInt();
                        int min = rangeAnnotationInt.min();
                        int max = rangeAnnotationInt.max();

                        property.setMinValue(min);
                        property.setMaxValue(max);
                        if (value < min || value > max) { //enforce limits on configured values
                            property.setValue(Math.min(Math.max(value, min), max));
                        }
                    }

                    Config.RangeDouble rangeAnnotationDouble = field.getAnnotation(Config.RangeDouble.class);
                    if (rangeAnnotationDouble != null) {
                        if (type != double.class) {
                            throw new IllegalStateException(Config.RangeDouble.class + " cannot be applied to " + field);
                        }

                        double value = property.getDouble();
                        double min = rangeAnnotationDouble.min();
                        double max = rangeAnnotationDouble.max();

                        property.setMinValue(min);
                        property.setMaxValue(max);
                        if (value < min || value > max) { //enforce limits on configured values
                            property.setValue(Math.min(Math.max(value, min), max));
                        }
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

            if (!unknownKeys.isEmpty()) { //some config keys aren't present any more, remove them
                configuration.getCategory(category).keySet().removeAll(unknownKeys);
            }
        }
    }

    private static String getName(String fallback, AnnotatedElement element) {
        Config.Name annotation = element.getAnnotation(Config.Name.class);
        return annotation != null ? annotation.value() : fallback;
    }

    private static String getComment(AnnotatedElement element) {
        Config.Comment annotation = element.getAnnotation(Config.Comment.class);
        return annotation != null ? String.join("\n", annotation.value()) : null;
    }

    @SneakyThrows(ReflectiveOperationException.class)
    private static String getComment(Class<?> type, Property property, AnnotatedElement element) {
        StringBuilder builder = new StringBuilder();

        Config.Comment commentAnnotation = element.getAnnotation(Config.Comment.class);
        if (commentAnnotation != null) {
            for (String line : commentAnnotation.value()) {
                builder.append(line).append('\n');
            }
        }

        //append additional comment text describing the property
        builder.append("[default: ").append(property.isList() ? Arrays.toString(property.getDefaults()) : property.getDefault()).append(']');

        if (property.getValidValues() != null && property.getValidValues().length != 0) { //only some values are actually allowed, add them
            builder.append("\nAccepted values:");
            for (String validValue : property.getValidValues()) {
                builder.append("\n- ").append(validValue);

                if (type.isEnum()) { //the field is an enum, the enum properties might be annotated with a comment describing the value
                    Field enumField = type.getField(validValue);
                    assert enumField.getDeclaringClass() == type : enumField + " is declared in class " + enumField.getDeclaringClass().getTypeName();
                    String enumComment = getComment(enumField);
                    if (enumComment != null) { //append enum value description to comment
                        builder.append(":\n   ").append(enumComment.replace("\n", "   \n"));
                    }
                }
            }
        }

        Config.RangeInt rangeAnnotationInt = element.getAnnotation(Config.RangeInt.class);
        if (rangeAnnotationInt != null) {
            builder.append("\nMinimum: ").append(rangeAnnotationInt.min()).append(", maximum: ").append(rangeAnnotationInt.max());
        }

        Config.RangeDouble rangeAnnotationDouble = element.getAnnotation(Config.RangeDouble.class);
        if (rangeAnnotationDouble != null) {
            builder.append("\nMinimum: ").append(rangeAnnotationDouble.min()).append(", maximum: ").append(rangeAnnotationDouble.max());
        }

        return builder.toString();
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

    private synchronized static void load(boolean init) {
        Set<String> unusedCategoryNames = null;
        if (init) {
            unusedCategoryNames = new HashSet<>(CONFIGURATION.getCategoryNames());
        }

        for (Map.Entry<String, ModuleConfigBase> entry : listModules().entrySet()) {
            String categoryName = entry.getKey();
            ModuleConfigBase module = entry.getValue();

            ConfigCategory category = CONFIGURATION.getCategory(categoryName);

            if (init) {
                unusedCategoryNames.remove(categoryName);

                category.setComment(getComment(module.field));
                category.setLanguageKey(getLangKey(categoryName, module.field));
                category.setRequiresWorldRestart(requiresWorldRestart(module.field));
                category.setRequiresMcRestart(requiresMcRestart(module.field));
            }

            module.loadFromConfig(CONFIGURATION, categoryName, init);
        }

        if (init) {
            boolean anyChange;
            do {
                anyChange = false;
                for (String unusedCategoryName : unusedCategoryNames) {
                    if (CONFIGURATION.hasCategory(unusedCategoryName)) { //the category might be removed already if it was the child of another unused category
                        ConfigCategory category = CONFIGURATION.getCategory(unusedCategoryName);
                        if (category.getChildren().isEmpty()) {
                            CONFIGURATION.removeCategory(category);
                            PPatchesMod.LOGGER.info("Removed empty config category {}", unusedCategoryName);
                            anyChange = true;
                        }
                    }
                }
            } while (anyChange); //keep looping around in order to recursively delete categories which contain only empty categories
        }

        sortConfigurationCategories(CONFIGURATION);
        CONFIGURATION.save();
    }

    @SneakyThrows(ReflectiveOperationException.class)
    private static void sortConfigurationCategories(@SuppressWarnings("SameParameterValue") Configuration configuration) {
        Field field = ConfigCategory.class.getDeclaredField("children");
        field.setAccessible(true);

        Comparator<ConfigCategory> compareCategoriesByName = Comparator.comparing(ConfigCategory::getName);

        for (String categoryName : configuration.getCategoryNames()) {
            ConfigCategory category = configuration.getCategory(categoryName);

            //for some reason the child categories of a category aren't stored in a sorted datastructure, and so aren't alphabetized automatically like everything else.
            // this is kinda gross, but i don't care.
            @SuppressWarnings("unchecked")
            ArrayList<ConfigCategory> children = (ArrayList<ConfigCategory>) field.get(category);
            children.sort(compareCategoriesByName);

            List<String> propertyOrder = new ArrayList<>(category.size());
            if (category.containsKey("state")) { //ensure "state" property always comes before all other fields
                propertyOrder.add("state");
            }
            category.setPropertyOrder(propertyOrder);
        }
    }

    /**
     * This field exists only to be annotated with a default {@link ModuleDescriptor} annotation.
     */
    @ModuleDescriptor
    private static final boolean DUMMY_FIELD = false;

    @SneakyThrows(ReflectiveOperationException.class)
    public static ImmutableSortedMap<String, ModuleConfigBase> listModules() {
        if (MODULES != null) {
            return MODULES;
        }

        ModuleDescriptor defaultDescriptor = Objects.requireNonNull(PPatchesConfig.class.getDeclaredField("DUMMY_FIELD").getAnnotation(ModuleDescriptor.class));

        ImmutableSortedMap.Builder<String, ModuleConfigBase> builder = ImmutableSortedMap.naturalOrder();
        for (Field field : PPatchesConfig.class.getDeclaredFields()) {
            Object value = field.get(null);
            if (value instanceof ModuleConfigBase) {
                ModuleConfigBase module = (ModuleConfigBase) value;

                ModuleDescriptor descriptor = field.getAnnotation(ModuleDescriptor.class);
                module.descriptor = descriptor != null ? descriptor : defaultDescriptor;
                module.field = field;

                builder.put(field.getName().replace('_', '.'), module);
            }
        }
        return MODULES = builder.build();
    }

    /**
     * @author DaPorkchop_
     */
    public enum ModuleState {
        ENABLED,
        DISABLED,
        AUTO,
    }

    /**
     * @author DaPorkchop_
     */
    @Target({ ElementType.FIELD })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ModuleDescriptor {
        String[] requiredClasses() default {};

        boolean hasMixins() default true;

        String mixinRegisterPhase() default "DEFAULT";

        String transformerClass() default "";

        String transformerRegisterPhase() default "PREINIT";
    }

    @SubscribeEvent
    public static void configChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (PPatchesMod.MODID.equals(event.getModID())) {
            load(false);
        }
    }

    static {
        CONFIGURATION = new Configuration(new File("config", "ppatches.cfg"), true);
        load(true);
    }
}
