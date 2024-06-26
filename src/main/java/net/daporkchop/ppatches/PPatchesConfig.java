package net.daporkchop.ppatches;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import net.daporkchop.ppatches.core.bootstrap.PPatchesBootstrap;
import net.daporkchop.ppatches.modules.misc.ModuleConfig_PerDimensionBlackList;
import net.daporkchop.ppatches.modules.mixin.optimizeCallbackInfoAllocation.ModuleConfigOptimizeCallbackInfoAllocation;
import net.daporkchop.ppatches.modules.vanilla.optimizeBufferBuilder.ModuleConfigOptimizeBufferBuilder;
import net.daporkchop.ppatches.modules.vanilla.optimizeGameRulesAccess.ModuleConfigOptimizeGameRulesAccess;
import net.daporkchop.ppatches.modules.vanilla.optimizeItemRendererCacheModel.ModuleConfigOptimizeItemRendererCacheModels;
import net.daporkchop.ppatches.modules.vanilla.optimizeSearchTree.ModuleConfigOptimizeSearchTree;
import net.daporkchop.ppatches.modules.vanilla.optimizeTessellatorDraw.ModuleConfigOptimizeTessellatorDraw;
import net.daporkchop.ppatches.modules.vanilla.setRenderThreadCount.ModuleConfigSetRenderThreadCount;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author DaPorkchop_
 */
@UtilityClass
@Mod.EventBusSubscriber(modid = PPatchesMod.MODID)
public class PPatchesConfig {
    public static final Configuration CONFIGURATION;
    private static ImmutableMap<String, ModuleConfigBase> MODULES;

    @Config.Comment({
            "Patches all references to the ObjectWeb ASM library's Type class to limit the number of temporary object allocations.",
            "This does not directly affect game runtime performance by itself, but should slightly improve load times other transformers are loaded.",
    })
    @ModuleDescriptor(
            priority = 500, //high priority, but lower than ppatches.tagLogMessages
            registerPhase = PPatchesBootstrap.Phase.PREINIT,
            mixins = {},
            transformerClass = "net.daporkchop.ppatches.modules.asm.foldTypeConstants.FoldTypeConstantsTransformer")
    public static final ModuleConfigBase asm_foldTypeConstants = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches CustomMainMenu to use GlStateManager instead of directly invoking glColor*.",
            "This fixes a bug that normally makes no difference, but is noticeable when using the vanilla.optimizeTessellatorDraw module. Enabling this patch should have"
                    + " no performance implications.",
    })
    public static final ModuleConfigBase customMainMenu_fixRenderColors = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches Extra Utilities 2 to make all Quantum Quarries mine from the same chunk.",
            "This could result in a significant performance increase on the server side when multiple Quantum Quarries are active.",
            "Note that when quarries are configured using a Biome Marker, those quarries will use a separate shared chunk for each configured biome. This will partially"
                    + " negate the performance improvements, especially if many quarries are configured with unique Biome Markers.",
            "Note that this will affect the order in which blocks are mined - an individual quarry will no longer always mine an entire block column at a time, but will"
                    + " instead effectively be mining random blocks. On average, this should make no difference in mined items, however it should be taken into account.",
    })
    public static final ModuleConfigBase extraUtilities2_allQuarriesMineFromSameChunk = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches Extra Utilities 2 to disable sky light in the Quantum Quarry and Deep Dark dimensions.",
            "This could result in a slight performance increase on the server side, in particular when the Quantum Quarry is active.",
    })
    public static final ModuleConfigBase extraUtilities2_disableSkyLightInCustomDimensions = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches Extra Utilities 2 to make the Quantum Quarry chunkload the source chunks in the quarry dimension.",
            "This will improve Quantum Quarry performance overall by avoiding periodic stuttering caused by chunks being unloaded and immediately loaded again.",
    })
    @ModuleDescriptor(mixins = {
            //generic mixins for making the XUChunkLoader stuff work correctly with this
            @MixinConfig,
            //these mixins would break when allQuarriesMineFromSameChunk is enabled, so we'll only apply them when it's not enabled and duplicate the chunkloading logic
            // into that module when this module is enabled
            @MixinConfig(suffix = "standalone", requires = @Requirement(moduleDisabled = "extraUtilities2.allQuarriesMineFromSameChunk"))
    })
    public static final ModuleConfigBase extraUtilities2_loadQuarryChunks = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches Extra Utilities 2 to optimize the redstone clock texture to use whatever the currently configured texture animation update system is.",
            "This will make it respect OptiFine's \"Smart Animations\" and PPatches' vanilla.optimizeTextureAnimationUpdates.",
    })
    public static final ModuleConfigBase extraUtilities2_optimizeAnimatedTextures = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches Extra Utilities 2 to optimize the DropsHandler class to eliminate an expensive HashMultimap lookup every time a block is harvested.",
            "This can increase server performance by a few percent in scenarios where many blocks are being harvested quickly.",
    })
    public static final ModuleConfigBase extraUtilities2_optimizeDropsHandler = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches Extra Utilities 2 to optimize the ItemCaptureHandler class to eliminate the need to create an item entity for each captured item drop.",
            "This will result in a server-side performance increase of 15-30% for blocks which use it, in particular the Quantum Quarry.",
    })
    public static final ModuleConfigBase extraUtilities2_optimizeItemCaptureHandler = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches FoamFix to optimize the algorithm used for blending between frames of animated textures with interpolation enabled, such as lava or command blocks.",
            "This is unlikely to give any meaningful performance benefits.",
    })
    public static final ModuleConfigBase foamFix_optimizeTextureInterpolation = new ModuleConfigBase(ModuleState.DISABLED);

    @Config.Comment({
            "Patches FoamFix to make OptiFine's \"Smart Animations\" work.",
            "Without this patch, OptiFine's \"Smart Animations\" will have no effect if FoamFix is installed.",
    })
    @ModuleDescriptor(
            requires = @Requirement(classPresent = "net.optifine.SmartAnimations"))
    public static final ModuleConfigBase foamFix_respectOptiFineSmartAnimations = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches Forestry to optimize the habitat locator texture to use whatever the currently configured texture animation update system is.",
            "This will make it respect OptiFine's \"Smart Animations\" and PPatches' vanilla.optimizeTextureAnimationUpdates.",
    })
    public static final ModuleConfigBase forestry_optimizeAnimatedTextures = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches FML networking code to make splitting of large modded packets into multipart packets behave correctly.",
            "Specifically, this prevents the Forge code from assuming that the packet's contents are a single heap ByteBuf without offset or padding, which could"
                    + " otherwise result in packets being split into multipart packets unnecessarily, and also makes it work correctly with direct buffers.",
            "This has no meaningful performance impact.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase forge_fixPacketSplitting = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Optimizes the classes generated by Forge's EventBus for calling event handlers.",
            "This will bypass the standard ASMEventHandler code entirely and generate a new class specifically for every registered event handler instance.",
            "This is unlikely to noticeably affect performance by itself, however it may allow the JIT compiler to optimize more aggressively when combined with the"
                    + " forge.optimizeEventBusDispatch module.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase forge_optimizeAsmEventHandler = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches some Forge code in Minecraft's Block class which handles capturing block drops to make it run faster.",
            "This could substantially improve performance for mods which make extensive use of this feature.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase forge_optimizeBlockCaptureDrops = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches ChunkProviderServer to avoid iterating over Forge's forced chunk set on every tick",
            "This can reduce the server thread time spent processing chunk unloads significantly in worlds with lots of loaded chunks.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase forge_optimizeChunkProviderServerUnloading = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches all code which fires an event on the Forge EventBus to fire events in a much more efficient way.",
            "This uses INVOKEDYNAMIC to generate classes at runtime, resulting in code equivalent to a monomorphic virtual call to a method containing a sequence of static calls"
                    + " to event handler methods, rather than an interface call to get the listener list followed by two interface calls for each handler.",
            "This can give significant (~10-15%) performance improvements when many events are being fired, especially in situations where there are many listeners for a single"
                    + " event type.",
    })
    @ModuleDescriptor(
            registerPhase = PPatchesBootstrap.Phase.PREINIT,
            mixins = {
                    @MixinConfig,
                    //we want these mixins to be applied even if forge.optimizeEventInstanceAllocation is disabled, because they help us optimize as well
                    @MixinConfig(suffix = "optimizeEventInstanceAllocation", requires = @Requirement(moduleDisabled = "forge.optimizeEventInstanceAllocation"))
            },
            transformerClass = {
                    "net.daporkchop.ppatches.modules.forge.optimizeEventBusDispatch.OptimizeEventBusDispatchTransformer",
                    "net.daporkchop.ppatches.modules.forge.optimizeEventBusDispatch.OptimizeEventBusDispatchTransformer_ListenerList",
            })
    public static final ModuleConfigBase forge_optimizeEventBusDispatch = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches all Forge events and event handlers, and most references to Forge event buses, to allow resetting event instances when possible instead of allocating a new"
                    + " instance every time one is fired.",
            "This can dramatically improve performance and/or reduce GC churn in some situations.",
    })
    @ModuleDescriptor(
            registerPhase = PPatchesBootstrap.Phase.PREINIT,
            transformerClass = {
                    "net.daporkchop.ppatches.modules.forge.optimizeEventInstanceAllocation.OptimizeEventInstanceAllocationTransformer_Events",
                    "net.daporkchop.ppatches.modules.forge.optimizeEventInstanceAllocation.OptimizeEventInstanceAllocationTransformer_IndividualMethods",
            })
    //TODO: this is currently disabled because it conflicts with optimizeEventBusDispatch
    public static final ModuleConfigBase forge_optimizeEventInstanceAllocation = new ModuleConfigBase(ModuleState.DISABLED);

    @Config.Comment({
            "Patches ForgeChunkManager to store the per-world forced chunks set in the world instance directly.",
            "This can reduce the server thread time spent processing chunk unloads significantly in worlds with lots of loaded chunks.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase forge_optimizeGetPersistentChunks = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches ForgeChunkManager to avoid copying the entire loaded chunk set into a new HashSet on every tick.",
            "This can reduce the server thread time spent processing random ticks significantly in worlds with lots of loaded chunks."
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase forge_optimizeGetPersistentChunksIterable = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches the vanilla game overlay class to improve rendering performance.",
            "For instance, this will increase the FPS while the F3 screen is enabled by roughly 15%.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase forge_optimizeOverlayGui = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Prevents the FML splash screen from automatically disabling itself in config if the splash renderer thread throws an exception.",
            "This is mainly intended for mod developers who may regularly cause the game to crash during startup and don't want to have to re-enable the splash screen every time.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase forge_preventSplashScreenAutoDisable = new ModuleConfigBase(ModuleState.DISABLED);

    @Config.Comment({
            "Patches iChunUtil to not access client-only code on the dedicated server.",
    })
    public static final ModuleConfigBase iChunUtil_fixClientClassAccess = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches all Java code to move string concatenation out of the main method body and into a separate INVOKEDYNAMIC instruction.",
            "This emulates the standard behavior for string concatenation in Java 9+.",
            "This could slightly improve performance for code which uses lots of string concatenation.",
    })
    @ModuleDescriptor(
            registerPhase = PPatchesBootstrap.Phase.PREINIT,
            mixins = {},
            transformerClass = "net.daporkchop.ppatches.modules.java.dynamicStringConcatenation.DynamicStringConcatenationTransformer")
    public static final ModuleConfigBase java_dynamicStringConcatenation = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Rewrites simple usages of Java's Stream API into equivalent loop(s) and conditional(s).",
            "This can significantly improve performance when using mods which make extensive use of the Stream API, however vanilla code is unlikely to benefit.",
    })
    @ModuleDescriptor(
            registerPhase = PPatchesBootstrap.Phase.PREINIT,
            mixins = {},
            transformerClass = "net.daporkchop.ppatches.modules.java.flattenStreams.FlattenStreamsTransformer")
    public static final ModuleConfigBase java_flattenStreams = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Folds various effectively constant expressions using the Java API",
            "This can significantly improve performance when using mods which make extensive use of the Stream API, however vanilla code is unlikely to benefit.",
    })
    @ModuleDescriptor(
            registerPhase = PPatchesBootstrap.Phase.PREINIT,
            mixins = {},
            transformerClass = {
                    "net.daporkchop.ppatches.modules.java.foldTrivialConstants.FoldTrivialConstantsTransformer",
                    "net.daporkchop.ppatches.modules.java.foldTrivialConstants.FoldTrivialConstantsTransformer_MethodHandles",
                    "net.daporkchop.ppatches.modules.java.foldTrivialConstants.FoldTrivialConstantsTransformer_RemovePointlessExceptionHandlers",
            })
    public static final ModuleConfigBase java_foldTrivialConstants = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Optimizes calls to Math.toDegrees(double) and Math.toRadians(double) with a simple multiplication by a constant.",
            "This is the standard behavior on Java 9+ (see https://bugs.openjdk.org/browse/JDK-4477961), and as such should be totally safe to use. However, as it"
                    + " does slightly affect the results of angle conversions, this module is disabled by default.",
            "This can increase the performance of code involving angle conversions by 3-4 times, however it will not affect vanilla code (as vanilla doesn't use the Java"
                    + " Math class for angle conversions).",
    })
    @ModuleDescriptor(
            registerPhase = PPatchesBootstrap.Phase.PREINIT,
            mixins = {},
            transformerClass = "net.daporkchop.ppatches.modules.java.optimizeAngleConversions.OptimizeAngleConversionsTransformer")
    public static final ModuleConfigBase java_optimizeAngleConversions = new ModuleConfigBase(ModuleState.DISABLED);

    @Config.Comment({
            "Optimizes simple calls to String.format() into equivalent string concatenations.",
            "This can result in minor performance increases overall.",
    })
    @ModuleDescriptor(
            registerPhase = PPatchesBootstrap.Phase.PREINIT,
            mixins = {},
            transformerClass = "net.daporkchop.ppatches.modules.java.optimizeStringFormat.OptimizeStringFormatTransformer")
    public static final ModuleConfigBase java_optimizeStringFormat = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches all Java code to move construction of exception objects out of the main method body and into a separate INVOKEDYNAMIC instruction.",
            "This could theoretically improve performance in specific scenarios and on specific JVMs, but don't expect to see measurable improvements.",
    })
    @ModuleDescriptor(
            registerPhase = PPatchesBootstrap.Phase.PREINIT,
            mixins = {},
            transformerClass = "net.daporkchop.ppatches.modules.java.separatedExceptionConstruction.SeparatedExceptionConstructionTransformer")
    public static final ModuleConfigBase java_separatedExceptionConstruction = new ModuleConfigBase(ModuleState.ENABLED);

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
            "Disable lighting updates in specific dimensions.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfig_PerDimensionBlackList misc_disableLightUpdatesPerDimension = new ModuleConfig_PerDimensionBlackList(ModuleState.DISABLED);

    @Config.Comment({
            "Disable random block ticks in specific dimensions.",
    })
    @ModuleDescriptor(
            registerPhase = PPatchesBootstrap.Phase.AFTER_MIXIN_DEFAULT,
            mixins = {
                    @MixinConfig,
                    @MixinConfig(suffix = "CubicChunks", requires = @Requirement(classPresent = "io.github.opencubicchunks.cubicchunks.core.CubicChunks"))
            })
    public static final ModuleConfig_PerDimensionBlackList misc_disableRandomTicksPerDimension = new ModuleConfig_PerDimensionBlackList(ModuleState.DISABLED);

    @Config.Comment({
            "Aggressively preload classes in the order in which they were loaded during the last startup.",
            "This can improve startup times and first join times by ~10-15% on large modpacks, but may cause issues with some particularly hacky mods.",
    })
    @ModuleDescriptor(
            registerPhase = PPatchesBootstrap.Phase.PREINIT,
            transformerClass = "net.daporkchop.ppatches.modules.misc.preloadClassesAsync.PreloadClassesDummyTransformer")
    public static final ModuleConfigBase misc_preloadClassesAsync = new ModuleConfigBase(ModuleState.DISABLED);

    @Config.Comment({
            "Patches all Mixin injection points to replace eligible allocations of CallbackInfo with a static instance which can be re-used.",
            "This can dramatically improve performance and/or reduce GC churn in some situations, especially when there are other mods installed which use Mixin.",
            "Note for developers: this will cause Mixin hot swapping to fail when reloading classes whose Mixins got optimized away.",
    })
    @ModuleDescriptor(
            registerPhase = PPatchesBootstrap.Phase.PREINIT,
            mixins = {},
            transformerClass = "net.daporkchop.ppatches.modules.mixin.optimizeCallbackInfoAllocation.OptimizeCallbackInfoAllocationTransformer")
    public static final ModuleConfigOptimizeCallbackInfoAllocation mixin_optimizeCallbackInfoAllocation = new ModuleConfigOptimizeCallbackInfoAllocation(ModuleState.ENABLED);

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
            requires = @Requirement(classPresent = "openblocks.common.tileentity.TileEntityFan"),
            transformerClass = "net.daporkchop.ppatches.modules.openBlocks.fanEntityOptimization.FanEntityOptimizationTransformer")
    public static final ModuleConfigBase openBlocks_fanEntityOptimization = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches OpenBlocks to avoid re-scanning entities in the world when many fans are placed near each other in the world.",
            "This can result in very large performance improvements for server tick rate when many OpenBlocks fans are placed close to each other.",
    })
    public static final ModuleConfigBase openBlocks_fanUpdateBatching = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches OpenBlocks to make rendering fan tile entities significantly faster.",
            "This can increase FPS by as much as 10 times in cases where many OpenBlocks fans are visible at once.",
    })
    public static final ModuleConfigBase openBlocks_optimizeFanRendering = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches OptiFine to use MethodHandles in place of Java reflection.",
            "This could give some minor performance benefits when OptiFine is installed, and will definitely help reduce GC churn.",
    })
    @ModuleDescriptor(
            requires = @Requirement(classPresent = "net.optifine.reflect.Reflector"),
            registerPhase = PPatchesBootstrap.Phase.AFTER_MIXIN_DEFAULT,
            mixins = {},
            transformerClass = "net.daporkchop.ppatches.modules.optifine.optimizeReflector.OptimizeReflectorTransformer")
    public static final ModuleConfigBase optifine_optimizeReflector = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches all PPatches module code to include the module name in log messages.",
            "This can be useful for debugging and filtering uninteresting data out of the log.",
    })
    @ModuleDescriptor(
            priority = 1000,
            registerPhase = PPatchesBootstrap.Phase.PREINIT,
            mixins = {},
            transformerClass = "net.daporkchop.ppatches.modules.ppatches.tagLogMessages.TagLogMessagesTransformer")
    public static final ModuleConfigBase ppatches_tagLogMessages = new ModuleConfigBase(ModuleState.AUTO);

    @Config.Comment({
            "Patches Minecraft's PooledMutableBlockPos class to not actually pool instances, but allocate new ones every time instead.",
            "This also deletes some unnecessary related state, which should make all uses of MutableBlockPos faster in general by eliminating possible dynamic dispatch.",
            "This should improve overall performance of a variety of performance-critical functions which access lots of blocks by a tiny amount.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase vanilla_disableMutableBlockPosPooling = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches Minecraft's networking code to avoid disconnecting players twice.",
            "This helps avoid crashing the dedicated server when the server is shut down while players are online.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase vanilla_fixRemovePlayersOnServerShutdown = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches Minecraft's font renderer to group together entire strings and send them to the GPU at once, instead of drawing each letter individually.",
            "Whether or not this will give a performance increase depends on your GPU driver. AMD GPUs appear to benefit the most from this, have an FPS increase "
                    + " of roughly 5% when the F3 menu is open.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase vanilla_fontRendererBatching = new ModuleConfigBase(ModuleState.DISABLED);

    @Config.Comment({
            "Patches Minecraft's font renderer to reset the text style when drawing text with a shadow.",
            "Without this, text formatting will carry over from the end of the shadow layer to the start of the foreground layer unless it contains an explicit style reset sequence at the"
                    + " beginning or end.",
            "This has no meaningful performance impact.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase vanilla_fontRendererFixStyleResetShadow = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches Minecraft's tile entity update code to group tile entity updates by the type of tile entity being updated.",
            "This can significantly improve server tick time in worlds with large numbers of tickable tile entities, but may break existing builds which rely on a"
                    + " particular tile entity update order.",
            "Test extensively before enabling this in a new world!",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase vanilla_groupTileEntityUpdatesByType = new ModuleConfigBase(ModuleState.DISABLED);

    @Config.Comment({
            "Patches the vanilla BufferBuilder class to optimize writing vertex data into the buffer.",
            "This can result in significant FPS improvements, typically somewhere between 10% and 30%. However, results will vary based on installed mods.",
    })
    @ModuleDescriptor(
            registerPhase = PPatchesBootstrap.Phase.AFTER_MIXIN_DEFAULT,
            mixins = {
                    @MixinConfig,
                    @MixinConfig(suffix = "OptiFine", requires = @Requirement(classPresent = "net.optifine.reflect.Reflector"))
            })
    public static final ModuleConfigOptimizeBufferBuilder vanilla_optimizeBufferBuilder = new ModuleConfigOptimizeBufferBuilder(ModuleState.ENABLED);

    @Config.Comment({
            "Patches all code which accesses a constant gamerule to avoid a TreeMap lookup and instead access the value directly.",
            "This will give a very slight overall performance boost.",
    })
    @ModuleDescriptor(
            registerPhase = PPatchesBootstrap.Phase.PREINIT,
            transformerClass = {
                    "net.daporkchop.ppatches.modules.vanilla.optimizeGameRulesAccess.OptimizeGameRulesAccessTransformer_GameRules",
                    "net.daporkchop.ppatches.modules.vanilla.optimizeGameRulesAccess.OptimizeGameRulesAccessTransformer_UserMethods",
            })
    public static final ModuleConfigOptimizeGameRulesAccess vanilla_optimizeGameRulesAccess = new ModuleConfigOptimizeGameRulesAccess(ModuleState.ENABLED);

    @Config.Comment({
            "Patches all code to optimize all calls to Block#getDefaultState() into a simple constant load wherever we can prove that the block instance is a constant.",
            "This will give a very slight performance increase in a lot of cases.",
    })
    @ModuleDescriptor(
            registerPhase = PPatchesBootstrap.Phase.PREINIT,
            mixins = {},
            transformerClass = "net.daporkchop.ppatches.modules.vanilla.optimizeGetDefaultState.OptimizeGetDefaultStateTransformer")
    public static final ModuleConfigBase vanilla_optimizeGetDefaultState = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches Minecraft's item renderer to re-use the same vertex data when rendering items which have the same mesh.",
            "This should notably improve performance when rendering many items (generally during GUI rendering) by ~5% or more, and will definitely help reduce GC churn.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigOptimizeItemRendererCacheModels vanilla_optimizeItemRendererCacheModel = new ModuleConfigOptimizeItemRendererCacheModels(ModuleState.ENABLED);

    @Config.Comment({
            "Patches Minecraft's MathHelper class to make a few operations more efficient.",
            "This will VERY slightly improve overall performance in certain cases.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase vanilla_optimizeMathHelper = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches Minecraft's NonNullList class to add specialized implementations for some list operations.",
            "This will VERY slightly improve overall performance in certain cases.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase vanilla_optimizeNonNullList = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches Minecraft's ResourceLocation class to make equality checks faster.",
            "This will slightly improve overall performance in certain cases.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase vanilla_optimizeResourceLocationComparison = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches Minecraft's SearchTree class to make initializing it faster.",
            "This makes the SearchTree calculation run asynchronously, and avoids adding entries to the internal SuffixArray datastructure multiple times.",
            "In large modpacks with many items and/or recipes, this can reduce client startup times by 10-20s (the effects are even more obvious if CraftTweaker is installed).",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigOptimizeSearchTree vanilla_optimizeSearchTree = new ModuleConfigOptimizeSearchTree(ModuleState.ENABLED);

    @Config.Comment({
            "Patches Minecraft's Tessellator to use an alternative technique for sending draw commands to the GPU, which may be more efficient on some systems.",
            "The tessellator is used for drawing many parts of the GUI (such as backgrounds, items in the inventory, and some tile entities), as well as for text if"
                    + " PPatches' \"fontRendererBatching\" module is enabled.",
            "Whether or not this will give a performance increase depends on your GPU driver, and on some drivers it may cause visual bugs. NVIDIA GPUs in particular"
                    + " seem to get roughly 10-15% higher FPS without any noticeable issues, however AMD's driver seems to glitch out most of the time.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigOptimizeTessellatorDraw vanilla_optimizeTessellatorDraw = new ModuleConfigOptimizeTessellatorDraw(ModuleState.DISABLED);

    @Config.Comment({
            "Patches Minecraft's texture atlas to avoid updating animated textures from the CPU every frame.",
            "Specifically, this will construct a secondary texture atlas containing all frames from all animated textures, and then uses a compute shader to efficiently"
                    + " modify relevant parts of the texture atlas when an animated texture transitions to the next frame.",
            "Because the compute shader is able to handle all possible animation updates, this is substantially more effective than FoamFix's texture animation optimization,"
                    + " which still relies on CPU data transfer in some (but not all) cases, which prevents any potential benefits from being visible.",
            "This gives roughly a 15-25% performance increase on an NVIDIA GPU. For best results, disable (!!!) OptiFine's \"Smart Animations\", as the extra work it does"
                    + " to avoid updating invisible textures actually ends up being slower than doing unnecessary extra work on the GPU.",
    })
    @ModuleDescriptor(
            registerPhase = PPatchesBootstrap.Phase.PREINIT,
            eventHandlerClass = "net.daporkchop.ppatches.modules.vanilla.optimizeTextureAnimationUpdates.EventHandler")
    public static final ModuleConfigBase vanilla_optimizeTextureAnimationUpdates = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches Minecraft's TextureUtil class to avoid allocating a 4MiB buffer on the Java heap every time part of an OpenGL texture is updated, even if the part"
                    + " of the texture being updated is much smaller than that.",
            "This will probably not have much effect by itself, however it can significantly improve startup times for some mods. (e.g. Ancient Warfare)",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase vanilla_optimizeTextureUtilHeapAllocations = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches Minecraft's World class to cache its hash code, instead of using Java's default implementation.",
            "For some reason, the default Object#hashCode() implementation appears to be very slow in some circumstances, even though it just delegates to"
                    + " System.identityHashCode(Object). This causes very bad performance anywhere a World is used as a key in a map (such as in"
                    + " MinecraftForgeClient.getRegionRenderCache(World, BlockPos), which is typically called once per tile entity per frame by TESR rendering code).",
            "In a test world containing roughly 1000 OpenBlocks fans, this made MinecraftForgeClient#getRegionRenderCache(World, BlockPos) about 50x faster (from ~76% of"
                    + " the total frame time to ~1.5%). Your mileage may vary, however even in the worst case this patch should have no effect (enabling it won't make your game"
                    + " run slower).",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase vanilla_optimizeWorldHashing = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches all references to the isRemote field of Minecraft's World class to be hard-coded to false on the dedicated server.",
            "This will slightly improve dedicated server performance, although the improvement will be extremely difficult to measure.",
    })
    @ModuleDescriptor(
            registerPhase = PPatchesBootstrap.Phase.PREINIT,
            transformerClass = "net.daporkchop.ppatches.modules.vanilla.optimizeWorldIsRemoteOnDedicatedServer.OptimizeWorldIsRemoteOnDedicatedServerTransformer")
    public static final ModuleConfigBase vanilla_optimizeWorldIsRemoteOnDedicatedServer = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches Minecraft to override the number of chunk rendering threads.",
            "This is intended to avoid starting unnecessarily many threads on systems with lots of cores. Whether or not it makes any performance difference will depend"
                    + " on your system and usage, so it isn't enabled by default.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigSetRenderThreadCount vanilla_setRenderThreadCount = new ModuleConfigSetRenderThreadCount(ModuleState.DISABLED);

    @Config.Comment({
            "Patches Minecraft to replace the most frequently used instances of java.util.Random and Math.random() with ThreadLocalRandom or a functionally equivalent"
                    + " version of java.util.Random which isn't thread-safe.",
            "This will slightly improve server thread performance, especially when generating terrain.",
    })
    @ModuleDescriptor(
            registerPhase = PPatchesBootstrap.Phase.PREINIT,
            transformerClass = "net.daporkchop.ppatches.modules.vanilla.useFasterRandom.UseFasterRandomTransformer")
    public static final ModuleConfigBase vanilla_useFasterRandom = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Patches Minecraft to use a Netty FastThreadLocalThread when creating the server thread.",
            "This will slightly improve server thread performance.",
    })
    @ModuleDescriptor(registerPhase = PPatchesBootstrap.Phase.PREINIT)
    public static final ModuleConfigBase vanilla_useFastThreadLocalThread = new ModuleConfigBase(ModuleState.ENABLED);

    @Config.Comment({
            "Rewrites a lot of frequently-used methods in classes such as Block and Item which simply return a constant value and have multiple overrides which also return a constant value"
                    + " so that the value is stored in a field in the base class, the base class' method returns the value from the field (instead of the constant) and the overriding methods are"
                    + " removed entirely.",
            "This can substantially reduce the amount of virtual method invocations, and allow these methods to be inlined far more aggressively by the JIT compiler. It will definitely"
                    + " result in an increase in performance by some amount, although by how much can vary drastically based on a wide variety of factors.",
            "(some additional benchmarking should be done)",
            "Disabled by default as this is still somewhat experimental and untested.",
    })
    @ModuleDescriptor(
            registerPhase = PPatchesBootstrap.Phase.PREINIT,
            transformerClass = "net.daporkchop.ppatches.modules.vanilla.useFieldsForSimpleConstantGetters.UseFieldsForSimpleConstantGettersTransformer")
    public static final ModuleConfigBase vanilla_useFieldsForSimpleConstantGetters = new ModuleConfigBase(ModuleState.DISABLED);

    public static String getDisabledReason(Requirement[] requirements) {
        for (Requirement requirement : requirements) {
            String classPresent = requirement.classPresent();
            String classAbsent = requirement.classAbsent();
            String moduleEnabled = requirement.moduleEnabled();
            String moduleDisabled = requirement.moduleDisabled();
            Preconditions.checkArgument((classPresent.isEmpty() ? 0 : 1) + (classAbsent.isEmpty() ? 0 : 1) + (moduleEnabled.isEmpty() ? 0 : 1) + (moduleDisabled.isEmpty() ? 0 : 1) == 1, "exactly one requirement must be set!");
            if (!classPresent.isEmpty()) {
                if (Launch.classLoader.getResource(classPresent.replace('.', '/') + ".class") == null) {
                    return "dependency class " + classPresent + " can't be found";
                }
            } else if (!classAbsent.isEmpty()) {
                if (Launch.classLoader.getResource(classAbsent.replace('.', '/') + ".class") != null) {
                    return "negative dependency class " + classAbsent + " was found";
                }
            } else if (!moduleEnabled.isEmpty()) {
                ModuleConfigBase module = listModules().get(moduleEnabled);
                Preconditions.checkState(module != null, "module %s doesn't exist!", moduleEnabled);
                String disabledReason = module.getDisabledReason();
                if (disabledReason != null) {
                    return "dependency module " + moduleEnabled + " is disabled (reason: " + disabledReason + ')';
                }
            } else /*if (!moduleDisabled.isEmpty())*/ {
                ModuleConfigBase module = listModules().get(moduleDisabled);
                Preconditions.checkState(module != null, "module %s doesn't exist!", moduleDisabled);
                String disabledReason = module.getDisabledReason();
                if (disabledReason == null) {
                    return "negative dependency module " + moduleDisabled + " is enabled";
                }
            }
        }
        return null;
    }

    /**
     * @author DaPorkchop_
     */
    @RequiredArgsConstructor
    public static class ModuleConfigBase implements Comparable<ModuleConfigBase> {
        public transient final ModuleState defaultState;

        @Config.RequiresMcRestart
        @UseGetterForCommentDefault
        public ModuleState state = ModuleState.DEFAULT;

        public transient ModuleDescriptor descriptor;
        public transient Field field;

        private transient String cachedDisabledReason;
        private transient boolean disabledReasonCached = false;
        private transient boolean computingDisabledReason = false;

        //called to determine the string used in the [default: ...] comment entry for the state property
        @SuppressWarnings("unused")
        protected String state$commentDefault() {
            return this.defaultState.name();
        }

        public boolean isEnabled() {
            return this.getDisabledReason() == null;
        }

        public String getDisabledReason() {
            if (this.disabledReasonCached) {
                return this.cachedDisabledReason;
            }

            String name = this.field.getName();
            PPatchesBootstrap.Phase currentPhase = PPatchesBootstrap.currentPhase();
            PPatchesBootstrap.Phase registerPhase = this.descriptor.registerPhase();
            Preconditions.checkState(currentPhase.compareTo(registerPhase) >= 0, "state for module %s was accessed during phase %s, but the module is not loadable until %s", name, currentPhase, registerPhase);

            Preconditions.checkState(!this.computingDisabledReason, "state for module %s was accessed recursively!", name);
            this.computingDisabledReason = true;
            String disabledReason = this.computeDisabledReason();
            this.cachedDisabledReason = disabledReason;
            this.disabledReasonCached = true;
            this.computingDisabledReason = false;
            return disabledReason;
        }

        private String computeDisabledReason() {
            ModuleState state = this.state;
            do {
                switch (state) {
                    case DISABLED:
                        return "disabled by config";
                    case AUTO:
                        return PPatchesConfig.getDisabledReason(this.descriptor.requires());
                    case ENABLED:
                        return null;
                    case DEFAULT:
                        //check again using the default state
                        state = this.defaultState;
                        continue;
                    default:
                        throw new IllegalStateException(String.valueOf(this.state));
                }
            } while (true);
        }

        @Override
        public int compareTo(ModuleConfigBase o) {
            int d = -Integer.compare(this.descriptor.priority(), o.descriptor.priority());
            return d == 0 ? this.field.getName().compareTo(o.field.getName()) : d;
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
                    } else if (type == boolean[].class) {
                        property = configuration.get(category, name, (boolean[]) field.get(this));
                    } else if (type == int[].class) {
                        property = configuration.get(category, name, (int[]) field.get(this));
                    } else if (type == double[].class) {
                        property = configuration.get(category, name, (double[]) field.get(this));
                    } else if (type == String[].class) {
                        property = configuration.get(category, name, (String[]) field.get(this));
                    } else {
                        throw new IllegalStateException("don't know how to handle " + field);
                    }
                }
                Preconditions.checkState(property.isList() == type.isArray(), "field type is %san array, but property is %sa list", type.isArray() ? "" : "not", property.isList() ? "" : "not");

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
                    } else if (type == boolean[].class) {
                        property.setDefaultValues((boolean[]) field.get(this));
                    } else if (type == int[].class) {
                        property.setDefaultValues((int[]) field.get(this));
                    } else if (type == double[].class) {
                        property.setDefaultValues((double[]) field.get(this));
                    } else if (type == String[].class) {
                        property.setDefaultValues((String[]) field.get(this));
                    } else {
                        throw new IllegalStateException("don't know how to handle " + field);
                    }

                    property.setComment(getComment(type, property, field, this));
                    property.setLanguageKey(getLangKey(category + '.' + name, field));
                    property.setRequiresWorldRestart(requiresWorldRestart(field));
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
                } else if (type == boolean[].class) {
                    field.set(this, property.getBooleanList());
                } else if (type == int[].class) {
                    field.set(this, property.getIntList());
                } else if (type == double[].class) {
                    field.set(this, property.getDoubleList());
                } else if (type == String[].class) {
                    field.set(this, property.getStringList().clone());
                } else {
                    throw new IllegalStateException("don't know how to handle " + field);
                }
            }

            if (!unknownKeys.isEmpty()) { //some config keys aren't present any more, remove them
                configuration.getCategory(category).keySet().removeAll(unknownKeys);
            }
        }

        @SneakyThrows(ReflectiveOperationException.class)
        public boolean saveToConfig(Configuration configuration, String category) {
            boolean changed = false;
            for (Field field : this.getClass().getFields()) {
                if ((field.getModifiers() & Modifier.TRANSIENT) != 0 || field.isAnnotationPresent(Config.Ignore.class)) { //skip ignored fields
                    continue;
                }

                Class<?> type = field.getType();
                String name = getName(field.getName(), field);
                Preconditions.checkState(configuration.hasKey(category, name), "configuration doesn't contain key: %s#%s", category, name);

                //configure this instance's values
                Property property = configuration.getCategory(category).get(name);
                if (type == boolean.class) {
                    boolean value = field.getBoolean(this);
                    if (property.getBoolean() != value) {
                        property.setValue(value);
                        changed = true;
                    }
                } else if (type == int.class) {
                    int value = field.getInt(this);
                    if (property.getInt() != value) {
                        property.setValue(value);
                        changed = true;
                    }
                } else if (type == double.class) {
                    double value = field.getDouble(this);
                    if (Double.doubleToRawLongBits(property.getDouble()) != Double.doubleToRawLongBits(value)) {
                        property.setValue(value);
                        changed = true;
                    }
                } else if (type == String.class) {
                    String value = (String) field.get(this);
                    if (!property.getString().equals(value)) {
                        property.setValue(value);
                        changed = true;
                    }
                } else if (type.isEnum()) {
                    Enum<?> value = (Enum<?>) field.get(this);
                    if (!property.getString().equals(value.name())) {
                        property.setValue(value.name());
                        changed = true;
                    }
                } else if (type == boolean[].class) {
                    boolean[] value = (boolean[]) field.get(this);
                    if (!Arrays.equals(property.getBooleanList(), value)) {
                        property.setValues(value);
                        changed = true;
                    }
                } else if (type == int[].class) {
                    int[] value = (int[]) field.get(this);
                    if (!Arrays.equals(property.getIntList(), value)) {
                        property.setValues(value);
                        changed = true;
                    }
                } else if (type == double[].class) {
                    double[] value = (double[]) field.get(this);
                    if (!Arrays.equals(property.getDoubleList(), value)) {
                        property.setValues(value);
                        changed = true;
                    }
                } else if (type == String[].class) {
                    String[] value = (String[]) field.get(this);
                    if (!Arrays.equals(property.getStringList(), value)) {
                        property.setValues(value);
                        changed = true;
                    }
                } else {
                    throw new IllegalStateException("don't know how to handle " + field);
                }
            }
            return changed;
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

    @SneakyThrows
    private static String getComment(Class<?> type, Property property, Field field, Object instance) {
        StringBuilder builder = new StringBuilder();

        Config.Comment commentAnnotation = field.getAnnotation(Config.Comment.class);
        if (commentAnnotation != null) {
            for (String line : commentAnnotation.value()) {
                builder.append(line).append('\n');
            }
        }

        //append additional comment text describing the property
        builder.append("[default: ");
        if (field.isAnnotationPresent(UseGetterForCommentDefault.class)) {
            builder.append(MethodHandles.lookup().findVirtual(field.getDeclaringClass(), field.getName() + "$commentDefault", MethodType.methodType(String.class)).invoke(instance));
        } else {
            builder.append(property.isList() ? Arrays.toString(property.getDefaults()) : property.getDefault());
        }
        builder.append(']');

        if (property.getValidValues() != null && property.getValidValues().length != 0) { //only some values are actually allowed, add them
            builder.append("\nAccepted values:");

            boolean oneLine;
            if (type.isEnum()) { //the field is an enum, the enum properties might be annotated with a comment describing the value
                oneLine = true;
                for (String validValue : property.getValidValues()) {
                    Field enumField = type.getField(validValue);
                    assert enumField.getDeclaringClass() == type : enumField + " is declared in class " + enumField.getDeclaringClass().getTypeName();
                    if (getComment(enumField) != null) { //at least one of the enum constants has a comment, we can't print out the options on a single line
                        oneLine = false;
                        break;
                    }
                }
            } else {
                oneLine = false;
            }

            if (oneLine) {
                builder.append(' ').append(String.join(", ", property.getValidValues()));
            } else {
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
        }

        Config.RangeInt rangeAnnotationInt = field.getAnnotation(Config.RangeInt.class);
        if (rangeAnnotationInt != null) {
            builder.append("\nMinimum: ").append(rangeAnnotationInt.min()).append(", maximum: ").append(rangeAnnotationInt.max());
        }

        Config.RangeDouble rangeAnnotationDouble = field.getAnnotation(Config.RangeDouble.class);
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
            boolean changed;
            do {
                changed = false;
                for (String unusedCategoryName : unusedCategoryNames) {
                    if (CONFIGURATION.hasCategory(unusedCategoryName)) { //the category might be removed already if it was the child of another unused category
                        ConfigCategory category = CONFIGURATION.getCategory(unusedCategoryName);
                        if (category.getChildren().isEmpty()) {
                            CONFIGURATION.removeCategory(category);
                            PPatchesMod.LOGGER.info("Removed empty config category {}", unusedCategoryName);
                            changed = true;
                        }
                    }
                }
            } while (changed); //keep looping around in order to recursively delete categories which contain only empty categories
        }

        saveConfiguration(CONFIGURATION);
    }

    public synchronized static void save() {
        boolean anyChanged = false;
        for (Map.Entry<String, ModuleConfigBase> entry : listModules().entrySet()) {
            String categoryName = entry.getKey();
            ModuleConfigBase module = entry.getValue();

            Preconditions.checkState(CONFIGURATION.hasCategory(categoryName), "config category %s doesn't exist: %s", categoryName);

            anyChanged |= module.saveToConfig(CONFIGURATION, categoryName);
        }

        if (anyChanged) {
            saveConfiguration(CONFIGURATION);
        }
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

    @SneakyThrows(IOException.class)
    private static void saveConfiguration(Configuration configuration) {
        sortConfigurationCategories(configuration);

        Path filePath = configuration.getConfigFile().toPath();
        if (!Files.exists(filePath) || !Arrays.equals(Files.readAllBytes(filePath), saveConfigurationToBytes(configuration))) {
            configuration.save();
        }
    }

    @SneakyThrows({IOException.class, ReflectiveOperationException.class})
    private static byte[] saveConfigurationToBytes(Configuration configuration) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(baos, configuration.defaultEncoding))) {
            writer.write("# Configuration file" + Configuration.NEW_LINE + Configuration.NEW_LINE);

            if (configuration.getDefinedConfigVersion() != null) {
                Field CONFIG_VERSION_MARKER = Configuration.class.getDeclaredField("CONFIG_VERSION_MARKER");
                CONFIG_VERSION_MARKER.setAccessible(true);
                writer.write(CONFIG_VERSION_MARKER.get(null) + ": " + configuration.getDefinedConfigVersion() + Configuration.NEW_LINE + Configuration.NEW_LINE);
            }

            Field childrenField = Configuration.class.getDeclaredField("children");
            childrenField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Configuration> children = (Map<String, Configuration>) childrenField.get(configuration);

            Method save = Configuration.class.getDeclaredMethod("save", BufferedWriter.class);
            save.setAccessible(true);

            if (children.isEmpty()) {
                save.invoke(configuration, writer);
            } else {
                for (Map.Entry<String, Configuration> entry : children.entrySet()) {
                    writer.write("START: \"" + entry.getKey() + '"' + Configuration.NEW_LINE);
                    save.invoke(entry.getValue(), writer);
                    writer.write("END: \"" + entry.getKey() + '"' + Configuration.NEW_LINE + Configuration.NEW_LINE);
                }
            }
        }
        return baos.toByteArray();
    }

    /**
     * This field exists only to be annotated with a default {@link ModuleDescriptor} annotation.
     */
    @ModuleDescriptor
    private static final boolean DUMMY_FIELD = false;

    @SneakyThrows(ReflectiveOperationException.class)
    public static ImmutableMap<String, ModuleConfigBase> listModules() {
        if (MODULES != null) {
            return MODULES;
        }

        ModuleDescriptor defaultDescriptor = Objects.requireNonNull(PPatchesConfig.class.getDeclaredField("DUMMY_FIELD").getAnnotation(ModuleDescriptor.class));

        ImmutableMap.Builder<String, ModuleConfigBase> builder = ImmutableMap.builder();
        builder.orderEntriesByValue(Comparator.naturalOrder());

        for (Field field : PPatchesConfig.class.getDeclaredFields()) {
            Object value = field.get(null);
            if (value instanceof ModuleConfigBase) {
                ModuleConfigBase module = (ModuleConfigBase) value;

                ModuleDescriptor descriptor = field.getAnnotation(ModuleDescriptor.class);
                module.descriptor = descriptor != null ? descriptor : defaultDescriptor;
                module.field = field;

                builder.put(field.getName().replace('_', '.').intern(), module);
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
        DEFAULT,
    }

    /**
     * @author DaPorkchop_
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ModuleDescriptor {
        /**
         * Higher priority modules will be registered earlier.
         */
        int priority() default 0;

        Requirement[] requires() default {};

        PPatchesBootstrap.Phase registerPhase() default PPatchesBootstrap.Phase.MODS_ON_CLASSPATH;

        MixinConfig[] mixins() default @MixinConfig;

        String[] transformerClass() default {};

        String[] eventHandlerClass() default {};
    }

    /**
     * @author DaPorkchop_
     */
    @Target({})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Requirement {
        String classPresent() default "";

        String classAbsent() default "";

        String moduleEnabled() default "";

        String moduleDisabled() default "";
    }

    /**
     * @author DaPorkchop_
     */
    @Target({})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MixinConfig {
        String suffix() default "";

        Requirement[] requires() default {};
    }

    /**
     * @author DaPorkchop_
     */
    @Target({ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    private @interface UseGetterForCommentDefault {
    }

    @SubscribeEvent
    public static void configChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (PPatchesMod.MODID.equals(event.getModID())) {
            load(false);
        }
    }

    static {
        if (PPatchesConfig.class.getClassLoader() != Launch.classLoader) {
            throw new IllegalStateException("PPatchesConfig was loaded by " + PPatchesConfig.class.getClassLoader());
        }

        CONFIGURATION = new Configuration(new File("config", "ppatches.cfg"), true);
        load(true);
    }
}
