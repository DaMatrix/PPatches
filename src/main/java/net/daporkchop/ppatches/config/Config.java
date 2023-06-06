package net.daporkchop.ppatches.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * @author DaPorkchop_
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class Config {
    private static Config INSTANCE;

    public static Config instance() {
        assert INSTANCE != null : "not loaded!";
        return INSTANCE;
    }

    @SneakyThrows(IOException.class)
    public static Map<String, Boolean> load() {
        Path configPath = Paths.get("config", "ppatches.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        String existingConfig = null;
        if (Files.exists(configPath)) {
            existingConfig = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
            INSTANCE = gson.fromJson(existingConfig, Config.class);
        } else {
            INSTANCE = new Config();
        }

        JsonObject sortedRootTree = sort(gson.toJsonTree(INSTANCE).getAsJsonObject());
        String newConfig = gson.toJson(sortedRootTree);

        if (!Objects.equals(existingConfig, newConfig)) {
            Files.createDirectories(configPath.getParent());

            Path tempFilePath = configPath.resolveSibling("ppatches.json.tmp");
            Files.write(tempFilePath, newConfig.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC, StandardOpenOption.DSYNC);
            Files.move(tempFilePath, configPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }

        Map<String, Boolean> modules = new TreeMap<>();
        listModules(sortedRootTree, modules, "");
        return modules;
    }

    private static JsonObject sort(JsonObject obj) {
        List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(obj.entrySet());
        entries.sort(Map.Entry.comparingByKey());

        JsonObject next = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : entries) {
            next.add(entry.getKey(), entry.getValue().isJsonObject() ? sort(entry.getValue().getAsJsonObject()) : entry.getValue());
        }
        return next;
    }

    private static void listModules(JsonObject obj, Map<String, Boolean> modules, String prefix) {
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            JsonObject value = entry.getValue().getAsJsonObject();
            if (!value.has("enabled")) {
                listModules(entry.getValue().getAsJsonObject(), modules, prefix + entry.getKey() + '.');
            } else {
                modules.put(prefix + entry.getKey(), value.get("enabled").getAsBoolean());
            }
        }
    }

    private final FoamFix foamFix = new FoamFix();
    private final JourneyMap journeyMap = new JourneyMap();
    private final JustPlayerHeads justPlayerHeads = new JustPlayerHeads();
    private final Vanilla vanilla = new Vanilla();

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @Data
    public static class BaseModule {
        private final boolean enabled = false;
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    public static final class FoamFix {
        private final BaseModule optimizeTextureInterpolation = new BaseModule();
        private final BaseModule respectOptiFineSmartAnimations = new BaseModule();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    public static final class JourneyMap {
        private final BaseModule skipRenderingOffscreenTooltips = new BaseModule();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    public static final class JustPlayerHeads {
        private final BaseModule fixSkinRetrieval = new BaseModule();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    public static final class Vanilla {
        private final BaseModule fontRendererBatching = new BaseModule();
        private final BaseModule fontRendererFixStyleResetShadow = new BaseModule();
        private final BaseModule optimizeTessellatorDraw = new BaseModule();
        private final BaseModule optimizeWorldHashing = new BaseModule();
    }
}
