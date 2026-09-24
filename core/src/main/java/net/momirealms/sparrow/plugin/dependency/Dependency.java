package net.momirealms.sparrow.plugin.dependency;

import net.momirealms.sparrow.plugin.dependency.relocation.Relocation;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Supplier;

public class Dependency {
    private static final String MAVEN_FORMAT = "%s/%s/%s/%s.jar";
    private final String groupId;
    private final String artifactId;
    private final String classifier;
    private final Set<Relocation> relocations;
    private final Visibility visibility;
    private final Source source;
    private final boolean autoLoad;

    /**
     * 构造一个新的依赖项.
     *
     * @param groupId         组 ID
     * @param artifactId      构件 ID
     * @param relocations     重定位规则列表
     * @param source          插件来源
     * @param visibility      依赖可见度设置
     * @param autoLoad        是否自动加载
     */
    public Dependency(
            @NotNull String groupId,
            @NotNull String artifactId,
            @NotNull String classifier,
            @NotNull Set<Relocation> relocations,
            @NotNull Source source,
            @NotNull Visibility visibility,
            boolean autoLoad
    ) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.classifier = classifier;
        this.relocations = relocations;
        this.visibility = visibility;
        this.source = source;
        this.autoLoad = autoLoad;
    }


    public String groupId() {
        return this.groupId;
    }

    public String artifactId() {
        return this.artifactId;
    }

    public Set<Relocation> relocations() {
        return this.relocations;
    }

    public Source source() {
        return this.source;
    }

    public Visibility visibility() {
        return this.visibility;
    }

    public boolean autoLoad() {
        return this.autoLoad;
    }

    /**
     * 获取依赖的版本号.<br>
     * 如果是Maven依赖则返回正常版本号, 如果是JarInJar依赖则返回插件构建时间作为版本号.
     */
    public String version() {
        return this.source instanceof Source.Maven ? this.source.dataSupplier().get() : DependencyVersions.COMPILE_TIME;
    }

    /**
     * 获取依赖的本地存储路径.
     *
     * @return 本地路径字符串
     */
    public String toLocalPath() {
        return rewriteEscaping(this.groupId).replace(".", "/") + "/" + this.artifactId + "/" + version();
    }

    /**
     * 获取 Maven 仓库中的相对路径.
     *
     * @return Maven 路径字符串
     */
    public String mavenPath() {
        return String.format(MAVEN_FORMAT,
                rewriteEscaping(this.groupId).replace(".", "/"),
                rewriteEscaping(this.artifactId),
                version(),
                rewriteEscaping(this.artifactId) + "-" + version() + (classifier.isEmpty() ? "" : "-" + classifier)
        );
    }

    /**
     * 重写转义字符, 将 "{}" 替换为 ".".
     *
     * @param s 原始字符串
     * @return 替换后的字符串
     */
    public static String rewriteEscaping(String s) {
        return s.replace("{}", ".");
    }

    /**
     * 获取依赖的文件名.
     *
     * @param suffix 分类器, 可为 null 或空字符串
     * @return 文件名字符串
     */
    public String fileName(String suffix) {
        String name = this.artifactId.toLowerCase(Locale.ENGLISH).replace('_', '-');
        String extra = suffix == null || suffix.isEmpty()
                ? ""
                : "-" + suffix;
        return name + "-" + this.version() + (classifier.isEmpty() ? "" : "-" + classifier)  + extra + ".jar";
    }

    public boolean hasJarInJarPath() {
        return this.source instanceof Source.JarInJar;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Dependency that)) return false;
        return Objects.equals(this.groupId, that.groupId) && Objects.equals(this.artifactId, that.artifactId);
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }

    @Override
    public String toString() {
        return "Dependency{" +
                "groupId='" + groupId + '\'' +
                "artifactId='" + artifactId + '\'' +
                "classifier='" + classifier + '\'' +
                '}';
    }

    /**
     * 创建一个新的构建器.
     *
     * @return 构建器实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 依赖项构建器.
     */
    public static class Builder {
        private String groupId;
        private String artifactId;
        private String classifier = "";
        private Set<Relocation> relocations = Set.of();
        private Source source;
        private Visibility visibility = Visibility.INTERNAL;
        private boolean autoLoad = true;

        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder artifactId(String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        public Builder classifier(String classifier) {
            this.classifier = classifier;
            return this;
        }

        public Builder visibility(Visibility visibility) {
            this.visibility = visibility;
            return this;
        }

        public Builder autoLoad(boolean autoLoad) {
            this.autoLoad = autoLoad;
            return this;
        }

        public Builder withArtifactGroupRelocation() {
            this.addRelocation(this.artifactId, this.groupId);
            return this;
        }

        public Builder addRelocation(String id, String pattern) {
            this.addRelocation(Relocation.of(id, pattern));
            return this;
        }

        public Builder addRelocation(Relocation relocation) {
            if (this.relocations.isEmpty()) {
                this.relocations = new HashSet<>();
            }
            this.relocations.add(relocation);
            return this;
        }

        public Builder addRelocations(Collection<Relocation> relocations) {
            if (this.relocations.isEmpty()) {
                this.relocations = new HashSet<>();
            }
            this.relocations.addAll(relocations);
            return this;
        }

        public Builder dependencySource(Source source) {
            this.source = source;
            return this;
        }

        public Builder jarInJarPath(String jarInJarPath) {
            this.source = new Source.JarInJar(() -> jarInJarPath);
            return this;
        }

        public Builder version(String version) {
            this.source = new Source.Maven(() -> version);
            return this;
        }

        /**
         * 构建依赖项实例.
         *
         * @return 构建好的依赖项
         * @throws NullPointerException 如果 groupId 或 artifactId 为 null
         */
        public Dependency build() {
            Objects.requireNonNull(this.groupId, "groupId is null");
            Objects.requireNonNull(this.artifactId, "artifactId is null");
            Objects.requireNonNull(this.source, "Either 'version' or 'jarInJar path' must be specified.");
            return new Dependency(this.groupId, this.artifactId, this.classifier, this.relocations, this.source, this.visibility, this.autoLoad);
        }
    }

    /**
     * 依赖可见度标识
     */
    public enum Visibility {
        INTERNAL, PUBLIC
    }

    /**
     * 依赖获取来源
     */
    public sealed interface Source {
        Supplier<String> dataSupplier();

        record Maven(Supplier<String> versionSupplier) implements Source {
            @Override
            public Supplier<String> dataSupplier() {
                return versionSupplier;
            }

            public String version() {
                return versionSupplier.get();
            }
        }

        record JarInJar(Supplier<String> pathSupplier) implements Source {
            @Override
            public Supplier<String> dataSupplier() {
                return pathSupplier;
            }

            public String jarInJarPath() {
                return this.pathSupplier.get();
            }
        }
    }

}