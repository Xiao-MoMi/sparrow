package net.momirealms.sparrow.plugin.dependency.relocation;

import net.momirealms.sparrow.plugin.dependency.DependencyVersions;

import java.util.Objects;

public final class Relocation {
    private static final String RELOCATION_PREFIX = DependencyVersions.PROJECT_PACKAGE + ".libraries.";

    /**
     * 创建一个新的重定位规则实例.
     *
     * @param id      目标依赖的 ArtifactId.
     * @param pattern 目标依赖的原始包名.
     * @return 重定位实例
     */
    public static Relocation of(String id, String pattern) {
        return new Relocation(pattern.replace("{}", "."), RELOCATION_PREFIX + id.replace("{}", "."));
    }

    private final String pattern;
    private final String relocatedPattern;

    private Relocation(String pattern, String relocatedPattern) {
        this.pattern = pattern;
        this.relocatedPattern = relocatedPattern;
    }

    public String getPattern() {
        return this.pattern;
    }

    public String getRelocatedPattern() {
        return this.relocatedPattern;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Relocation that = (Relocation) o;
        return Objects.equals(this.pattern, that.pattern) &&
                Objects.equals(this.relocatedPattern, that.relocatedPattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.pattern, this.relocatedPattern);
    }
}
