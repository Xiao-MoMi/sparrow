package net.momirealms.sparrow.util;

import com.google.gson.JsonObject;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;
import net.momirealms.sparrow.proxy.MinecraftVersionParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class VersionHelper {
    public static final boolean IS_RUNNING_IN_DEV = Boolean.getBoolean(DependencyVersions.PROJECT_PACKAGE + ".dev");
    public static final MinecraftVersion MINECRAFT_VERSION;
    public static final int WORLD_VERSION;
    public static final int VERSION;
    public static final int MAJOR_VERSION;
    public static final int MINOR_VERSION;
    public static final boolean IS_MOJMAP;
    public static final boolean HAS_SPIGOT_PATCH;
    public static final boolean HAS_FOLIA_PATCH;
    public static final boolean HAS_PAPER_PATCH;
    public static final boolean HAS_LEAVES_PATCH;
    public static final boolean HAS_CANVAS_PATCH;
    public static final boolean HAS_LEAF_PATCH;
    public static final boolean HAS_LITHIUM_PATCH;
    public static final boolean HAS_UNIVERSE_SPIGOT_PATCH;
    public static final boolean HAS_PURPUR_PATCH;
    public static final boolean IS_OR_ABOVE_1_21_8;
    public static final boolean IS_OR_ABOVE_1_21_9;
    public static final boolean IS_OR_ABOVE_1_21_10;
    public static final boolean IS_OR_ABOVE_1_21_11;
    public static final boolean IS_OR_ABOVE_26_1;
    public static final boolean IS_OR_ABOVE_26_1_1;
    public static final boolean IS_OR_ABOVE_26_1_2;
    public static final boolean IS_OR_ABOVE_26_2;
    public static final boolean IS_OR_ABOVE_26_3;
    private static final Class<?> UNOBFUSCATED_CLAZZ = Objects.requireNonNull(ReflectionUtils.getClazz(
            "net.minecraft.obfuscate.DontObfuscate", // 因为无混淆版本没有这个类所以说多写几个防止找不到了
            "net.minecraft.data.Main",
            "net.minecraft.server.Main",
            "net.minecraft.gametest.Main",
            "net.minecraft.client.main.Main",
            "net.minecraft.client.data.Main"
    ));

    static {
        try (InputStream inputStream = UNOBFUSCATED_CLAZZ.getResourceAsStream("/version.json")) {
            if (inputStream == null) {
                throw new IOException("Failed to load version.json");
            }
            JsonObject json = GsonHelper.parseJsonToJsonObject(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            WORLD_VERSION = GsonHelper.getAsInt(json.get("world_version"), -1);
            if (WORLD_VERSION == -1) {
                throw new IllegalStateException("Failed to get world_version from version.json");
            }
            String versionString = json.getAsJsonPrimitive("id").getAsString()
                    .split("-", 2)[0]  // 1.21.10-rc1          -> 1.21.10
                    .split("_", 2)[0]; // 1.21.11_unobfuscated -> 1.21.11

            MINECRAFT_VERSION = MinecraftVersion.byName(versionString);

            String[] split = versionString.split("\\.");
            int major = Integer.parseInt(split[1]);
            int minor = split.length == 3 ? Integer.parseInt(split[2]) : 0;

            // 1.21.8 -> 12108, 26.3 -> 260300
            VERSION = parseVersionToInteger(versionString);

            IS_OR_ABOVE_1_21_8 = VERSION >= 12108;
            IS_OR_ABOVE_1_21_9 = VERSION >= 12109;
            IS_OR_ABOVE_1_21_10 = VERSION >= 12110;
            IS_OR_ABOVE_1_21_11 = VERSION >= 12111;
            IS_OR_ABOVE_26_1 = VERSION >= 260100;
            IS_OR_ABOVE_26_1_1 = VERSION >= 260101;
            IS_OR_ABOVE_26_1_2 = VERSION >= 260102;
            IS_OR_ABOVE_26_2 = VERSION >= 260200;
            IS_OR_ABOVE_26_3 = VERSION >= 260300;

            MAJOR_VERSION = major;
            MINOR_VERSION = minor;

            IS_MOJMAP = checkMojMap() || IS_OR_ABOVE_26_1;
            HAS_SPIGOT_PATCH = checkSpigot();
            HAS_FOLIA_PATCH = checkFolia();
            HAS_PAPER_PATCH = checkPaper();
            HAS_LEAVES_PATCH = checkLeaves();
            HAS_CANVAS_PATCH = checkCanvas();
            HAS_LEAF_PATCH = checkLeaf();
            HAS_LITHIUM_PATCH = checkLithium();
            HAS_UNIVERSE_SPIGOT_PATCH = checkUniverseSpigot();
            HAS_PURPUR_PATCH = checkPurpur();
        } catch (Exception e) {
            throw new RuntimeException("Failed to init VersionHelper", e);
        }
    }

    private VersionHelper() {}

    public static int parseVersionToInteger(String versionString) {
        return MinecraftVersionParser.parseVersionToInteger(versionString);
    }

    private static boolean exists(String... classNames) {
        for (int i = 0; i < classNames.length; i++) {
            String className = classNames[i];
            try {
                Class.forName(className.replace("{}", "."), false, VersionHelper.class.getClassLoader());
                return true;
            } catch (ClassNotFoundException ignored) {
            }
        }
        return false;
    }

    private static boolean checkMojMap() {
        return exists("net.neoforged.art.internal.RenamerImpl");
    }

    private static boolean checkSpigot() {
        return exists("org.spigotmc.SpigotConfig");
    }

    private static boolean checkFolia() {
        return exists("io.papermc.paper.threadedregions.RegionizedServer");
    }

    private static boolean checkPaper() {
        return exists("io.papermc.paper.adventure.PaperAdventure");
    }

    private static boolean checkLeaves() {
        return exists("org.leavesmc.leaves.bot.BotList");
    }

    private static boolean checkCanvas() {
        return exists("io.canvasmc.canvas.Config") || exists("io.canvasmc.canvas.GlobalConfiguration");
    }

    private static boolean checkLeaf() {
        return exists("org.dreeam.leaf.config.LeafConfig") || exists("org.dreeam.leaf.async.chunk.AsyncChunkSender");
    }

    private static boolean checkLithium() {
        return exists("net.caffeinemc.mods.lithium.common.world.chunk.LithiumHashPalette");
    }

    private static boolean checkUniverseSpigot() {
        return exists("com.universeprojects.util.palette.CompactHashPalette");
    }

    private static boolean checkPurpur() {
        return exists("org.purpurmc.purpur.PurpurConfig");
    }
}
