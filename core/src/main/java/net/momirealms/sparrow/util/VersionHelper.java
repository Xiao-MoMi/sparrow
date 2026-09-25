package net.momirealms.sparrow.util;

import com.google.gson.JsonObject;
import net.momirealms.sparrow.plugin.dependency.DependencyVersions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class VersionHelper {
    public static final boolean IS_RUNNING_IN_DEV = Boolean.getBoolean(DependencyVersions.PROJECT_PACKAGE + ".dev");
    public static final MinecraftVersion MINECRAFT_VERSION;
    public static final int WORLD_VERSION;
    public static final int version;
    public static final int majorVersion;
    public static final int minorVersion;
    public static final boolean isMojmap;
    public static final boolean hasSpigotPatch;
    public static final boolean hasFoliaPatch;
    public static final boolean hasPaperPatch;
    public static final boolean hasLeavesPatch;
    public static final boolean hasCanvasPatch;
    public static final boolean hasLeafPatch;
    public static final boolean hasLithiumPatch;
    public static final boolean hasUniverseSpigotPatch;
    public static final boolean hasPurpurPatch;
    public static final boolean isOrAbove1_21_8;
    public static final boolean isOrAbove1_21_9;
    public static final boolean isOrAbove1_21_10;
    public static final boolean isOrAbove1_21_11;
    public static final boolean isOrAbove26_1;
    public static final boolean isOrAbove26_1_1;
    public static final boolean isOrAbove26_1_2;
    public static final boolean isOrAbove26_2;
    public static final boolean isOrAbove26_3;
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
            version = parseVersionToInteger(versionString);

            isOrAbove1_21_8 = version >= 12108;
            isOrAbove1_21_9 = version >= 12109;
            isOrAbove1_21_10 = version >= 12110;
            isOrAbove1_21_11 = version >= 12111;
            isOrAbove26_1 = version >= 260100;
            isOrAbove26_1_1 = version >= 260101;
            isOrAbove26_1_2 = version >= 260102;
            isOrAbove26_2 = version >= 260200;
            isOrAbove26_3 = version >= 260300;

            majorVersion = major;
            minorVersion = minor;

            isMojmap = checkMojMap() || isOrAbove26_1;
            hasSpigotPatch = checkSpigot();
            hasFoliaPatch = checkFolia();
            hasPaperPatch = checkPaper();
            hasLeavesPatch = checkLeaves();
            hasCanvasPatch = checkCanvas();
            hasLeafPatch = checkLeaf();
            hasLithiumPatch = checkLithium();
            hasUniverseSpigotPatch = checkUniverseSpigot();
            hasPurpurPatch = checkPurpur();
        } catch (Exception e) {
            throw new RuntimeException("Failed to init VersionHelper", e);
        }
    }

    private VersionHelper() {}

    public static int parseVersionToInteger(String versionString) {
        int v1 = 0;
        int v2 = 0;
        int v3 = 0;
        int currentNumber = 0;
        int part = 0;
        for (int i = 0; i < versionString.length(); i++) {
            char c = versionString.charAt(i);
            if (c >= '0' && c <= '9') {
                currentNumber = currentNumber * 10 + (c - '0');
            } else if (c == '.') {
                if (part == 0) {
                    v1 = currentNumber;
                }
                if (part == 1) {
                    v2 = currentNumber;
                }
                part++;
                currentNumber = 0;
                if (part > 2) {
                    break;
                }
            }
        }
        // 处理最后一个数字部分
        if (part == 0) {  // 没有点号：如 "26"
            v1 = currentNumber;
        } else if (part == 1) {  // 一个点号：如 "26.1"
            v2 = currentNumber;
        } else if (part == 2) {  // 两个点号：如 "1.2.3"
            v3 = currentNumber;
        }
        return 10000 * v1 + v2 * 100 + v3;
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
