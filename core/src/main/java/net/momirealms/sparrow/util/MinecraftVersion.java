package net.momirealms.sparrow.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MinecraftVersion implements Comparable<MinecraftVersion> {
    private static final Map<String, MinecraftVersion> BY_NAME = new LinkedHashMap<>();
    public static final MinecraftVersion V1_21_8 = new MinecraftVersion("1.21.8");
    public static final MinecraftVersion V1_21_9 = new MinecraftVersion("1.21.9");
    public static final MinecraftVersion V1_21_10 = new MinecraftVersion("1.21.10");
    public static final MinecraftVersion V1_21_11 = new MinecraftVersion("1.21.11");
    public static final MinecraftVersion V26_1 = new MinecraftVersion("26.1");
    public static final MinecraftVersion V26_1_1 = new MinecraftVersion("26.1.1");
    public static final MinecraftVersion V26_1_2 = new MinecraftVersion("26.1.2");
    public static final MinecraftVersion V26_2 = new MinecraftVersion("26.2");
    public static final MinecraftVersion V26_3 = new MinecraftVersion("26.3");
    public static final MinecraftVersion FUTURE = new MinecraftVersion("99.99.99");

    private final int version;
    private final String versionString;

    public static MinecraftVersion byName(final String version) {
        MinecraftVersion mcV = BY_NAME.get(version);
        if (mcV == null) {
            throw new IllegalArgumentException("Unsupported version: " + version);
        }
        return mcV;
    }

    public String version() {
        return this.versionString;
    }

    private MinecraftVersion(String version) {
        this.version = VersionHelper.parseVersionToInteger(version);
        this.versionString = version;
        BY_NAME.put(this.versionString, this);
    }

    public boolean isAtOrAbove(MinecraftVersion other) {
        return this.version >= other.version;
    }

    public boolean isAtOrBelow(MinecraftVersion other) {
        return this.version <= other.version;
    }

    public boolean is(MinecraftVersion other) {
        return this.version == other.version;
    }

    public boolean isBelow(MinecraftVersion other) {
        return this.version < other.version;
    }

    public boolean isAbove(MinecraftVersion other) {
        return this.version > other.version;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof MinecraftVersion that)) return false;
        return this.version == that.version;
    }

    @Override
    public int hashCode() {
        return this.version;
    }

    @Override
    public int compareTo(MinecraftVersion other) {
        return Integer.compare(this.version, other.version);
    }
}
