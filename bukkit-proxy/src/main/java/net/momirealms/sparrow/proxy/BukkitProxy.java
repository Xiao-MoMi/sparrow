package net.momirealms.sparrow.proxy;

import net.momirealms.sparrow.reflection.SReflection;
import net.momirealms.sparrow.reflection.remapper.Remapper;

import java.util.List;

public final class BukkitProxy {
    private static boolean init;

    private BukkitProxy() {}

    public static void init(String version, List<String> patches, String asmClassPrefix) {
        if (!init) {
            SReflection.setAsmClassPrefix(asmClassPrefix);
            SReflection.setActivePredicate(new MinecraftPredicate(version, patches));
            Remapper remapper = Remapper.createFromPaperJar();
            if (remapper != Remapper.noOp()) {
                SReflection.setRemapper(CraftBukkitRemapper.create(remapper));
            }
            init = true;
        }
    }
}
