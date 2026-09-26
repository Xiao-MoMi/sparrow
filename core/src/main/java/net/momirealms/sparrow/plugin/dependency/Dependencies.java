package net.momirealms.sparrow.plugin.dependency;

public final class Dependencies {
    private Dependencies() {}

    /**
     * JarInJar
     */
    public static final Dependency PLUGIN_BUKKIT_PROXY = Dependency.builder()
            .groupId(DependencyVersions.PROJECT_PACKAGE)
            .artifactId(DependencyVersions.PROJECT_ID + "-bukkit-proxy")
            .jarInJarPath(DependencyVersions.PROXY_JAR_NAME)
            .visibility(Dependency.Visibility.PUBLIC)
            .build();


    /**
     * ASM
     */
    public static final Dependency ASM = Dependency.builder()
            .groupId("org.ow2.asm")
            .artifactId("asm")
            .version(DependencyVersions.ASM)
            .autoLoad(false)
            .build();

    public static final Dependency ASM_COMMONS = Dependency.builder()
            .groupId("org.ow2.asm")
            .artifactId("asm-commons")
            .version(DependencyVersions.ASM_COMMONS)
            .autoLoad(false)
            .build();

    public static final Dependency JAR_RELOCATOR = Dependency.builder()
            .groupId("me.lucko")
            .artifactId("jar-relocator")
            .version(DependencyVersions.JAR_RELOCATOR)
            .autoLoad(false)
            .build();

    /**
     * Common
     */
    public static final Dependency CAFFEINE = Dependency.builder()
            .groupId("com{}github{}ben-manes{}caffeine")
            .artifactId("caffeine")
            .version(DependencyVersions.CAFFEINE)
            .addRelocation("caffeine", "com{}github{}benmanes{}caffeine")
            .build();

    /**
     * Cloud
     */
    public static final Dependency GEANTY_REF = Dependency.builder()
            .groupId("io{}leangen{}geantyref")
            .artifactId("geantyref")
            .version(DependencyVersions.GEANTYREF)
            .withArtifactGroupRelocation()
            .build();

    public static final Dependency CLOUD_CORE = Dependency.builder()
            .groupId("org{}incendo")
            .artifactId("cloud-core")
            .version(DependencyVersions.CLOUD_CORE)
            .addRelocation("cloud", "org{}incendo{}cloud")
            .addRelocation("geantyref", "io{}leangen{}geantyref")
            .build();

    public static final Dependency CLOUD_BRIGADIER = Dependency.builder()
            .groupId("org{}incendo")
            .artifactId("cloud-brigadier")
            .version(DependencyVersions.CLOUD_BRIGADIER)
            .addRelocations(CLOUD_CORE.relocations())
            .build();

    public static final Dependency CLOUD_SERVICES = Dependency.builder()
            .groupId("org{}incendo")
            .artifactId("cloud-services")
            .version(DependencyVersions.CLOUD_SERVICES)
            .addRelocations(CLOUD_CORE.relocations())
            .build();

    public static final Dependency CLOUD_BUKKIT = Dependency.builder()
            .groupId("org{}incendo")
            .artifactId("cloud-bukkit")
            .version(DependencyVersions.CLOUD_BUKKIT)
            .addRelocations(CLOUD_CORE.relocations())
            .addRelocation("adventure", "net{}kyori{}adventure")
            .addRelocation("examination", "net{}kyori{}examination")
            .addRelocation("option", "net{}kyori{}option")
            .build();

    public static final Dependency CLOUD_PAPER = Dependency.builder()
            .groupId("org{}incendo")
            .artifactId("cloud-paper")
            .version(DependencyVersions.CLOUD_PAPER)
            .addRelocations(CLOUD_BUKKIT.relocations())
            .build();

    public static final Dependency CLOUD_MINECRAFT_EXTRAS = Dependency.builder()
            .groupId("org{}incendo")
            .artifactId("cloud-minecraft-extras")
            .version(DependencyVersions.CLOUD_MINECRAFT_EXTRAS)
            .addRelocations(CLOUD_BUKKIT.relocations())
            .build();

    /**
     * Adventure
     */
    public static final Dependency OPTION = Dependency.builder()
            .groupId("net{}kyori")
            .artifactId("option")
            .version(DependencyVersions.OPTION)
            .addRelocation("option", "net{}kyori{}option")
            .addRelocation("examination", "net{}kyori{}examination")
            .addRelocation("adventure", "net{}kyori{}adventure")
            .visibility(Dependency.Visibility.PUBLIC)
            .build();

    public static final Dependency EXAMINATION_API = Dependency.builder()
            .groupId("net{}kyori")
            .artifactId("examination-api")
            .version(DependencyVersions.EXAMINATION_API)
            .addRelocations(OPTION.relocations())
            .visibility(Dependency.Visibility.PUBLIC)
            .build();

    public static final Dependency EXAMINATION_STRING = Dependency.builder()
            .groupId("net{}kyori")
            .artifactId("examination-string")
            .version(DependencyVersions.EXAMINATION_API)
            .addRelocations(OPTION.relocations())
            .visibility(Dependency.Visibility.PUBLIC)
            .build();

    public static final Dependency ADVENTURE_API = Dependency.builder()
            .groupId("net{}kyori")
            .artifactId("adventure-api")
            .version(DependencyVersions.ADVENTURE)
            .addRelocations(OPTION.relocations())
            .visibility(Dependency.Visibility.PUBLIC)
            .build();

    public static final Dependency ADVENTURE_NBT = Dependency.builder()
            .groupId("net{}kyori")
            .artifactId("adventure-nbt")
            .version(DependencyVersions.ADVENTURE)
            .addRelocations(OPTION.relocations())
            .visibility(Dependency.Visibility.PUBLIC)
            .build();

    public static final Dependency ADVENTURE_KEY = Dependency.builder()
            .groupId("net{}kyori")
            .artifactId("adventure-key")
            .version(DependencyVersions.ADVENTURE)
            .addRelocations(OPTION.relocations())
            .visibility(Dependency.Visibility.PUBLIC)
            .build();

    public static final Dependency TEXT_SERIALIZER_COMMONS = Dependency.builder()
            .groupId("net{}kyori")
            .artifactId("adventure-text-serializer-commons")
            .version(DependencyVersions.ADVENTURE)
            .addRelocations(OPTION.relocations())
            .visibility(Dependency.Visibility.PUBLIC)
            .build();

    public static final Dependency TEXT_SERIALIZER_PLAIN = Dependency.builder()
            .groupId("net{}kyori")
            .artifactId("adventure-text-serializer-plain")
            .version(DependencyVersions.ADVENTURE)
            .addRelocations(OPTION.relocations())
            .visibility(Dependency.Visibility.PUBLIC)
            .build();

    public static final Dependency TEXT_SERIALIZER_LEGACY = Dependency.builder()
            .groupId("net{}kyori")
            .artifactId("adventure-text-serializer-legacy")
            .version(DependencyVersions.ADVENTURE)
            .addRelocations(OPTION.relocations())
            .visibility(Dependency.Visibility.PUBLIC)
            .build();

    public static final Dependency TEXT_SERIALIZER_GSON = Dependency.builder()
            .groupId("net{}kyori")
            .artifactId("adventure-text-serializer-gson")
            .version(DependencyVersions.ADVENTURE)
            .addRelocations(OPTION.relocations())
            .visibility(Dependency.Visibility.PUBLIC)
            .build();

    public static final Dependency TEXT_SERIALIZER_GSON_LEGACY = Dependency.builder()
            .groupId("net{}kyori")
            .artifactId("adventure-text-serializer-json-legacy-impl")
            .version(DependencyVersions.ADVENTURE)
            .addRelocations(OPTION.relocations())
            .visibility(Dependency.Visibility.PUBLIC)
            .build();

    public static final Dependency TEXT_SERIALIZER_JSON = Dependency.builder()
            .groupId("net{}kyori")
            .artifactId("adventure-text-serializer-json")
            .version(DependencyVersions.ADVENTURE)
            .addRelocations(OPTION.relocations())
            .visibility(Dependency.Visibility.PUBLIC)
            .build();

    /**
     * SQL
     */
    public static final Dependency JDBI_CORE = Dependency.builder()
            .groupId("org{}jdbi")
            .artifactId("jdbi3-core")
            .version(DependencyVersions.JDBI)
            .addRelocation("jdbi", "org{}jdbi")
            .addRelocations(GEANTY_REF.relocations())
            .build();

    public static final Dependency HIKARI_CP = Dependency.builder()
            .groupId("com{}zaxxer")
            .artifactId("HikariCP")
            .version(DependencyVersions.HIKARI_CP)
            .addRelocation("hikari", "com{}zaxxer{}hikari")
            .build();

    public static final Dependency MYSQL_DRIVER = Dependency.builder()
            .groupId("com{}mysql")
            .artifactId("mysql-connector-j")
            .version(DependencyVersions.MYSQL_DRIVER)
            .addRelocation("mysql", "com{}mysql")
            .build();

    public static final Dependency MARIADB_DRIVER = Dependency.builder()
            .groupId("org{}mariadb{}jdbc")
            .artifactId("mariadb-java-client")
            .version(DependencyVersions.MARIADB_DRIVER)
            .addRelocation("mariadb", "org{}mariadb{}jdbc")
            .build();

    public static final Dependency POSTGRESQL_DRIVER = Dependency.builder()
            .groupId("org{}postgresql")
            .artifactId("postgresql")
            .version(DependencyVersions.POSTGRESQL_DRIVER)
            .addRelocation("postgresql", "org{}postgresql")
            .build();

    public static final Dependency CHECKER_QUAL = Dependency.builder()
            .groupId("org{}checkerframework")
            .artifactId("checker-qual")
            .version(DependencyVersions.CHECKER_QUAL)
            .build();

    /**
     * MongoDB
     */
    public static final Dependency MONGODB_DRIVER_CORE = Dependency.builder()
            .groupId("org{}mongodb")
            .artifactId("mongodb-driver-core")
            .version(DependencyVersions.MONGODB_DRIVER)
            .build();

    public static final Dependency MONGODB_DRIVER_SYNC = Dependency.builder()
            .groupId("org{}mongodb")
            .artifactId("mongodb-driver-sync")
            .version(DependencyVersions.MONGODB_DRIVER)
            .build();

    public static final Dependency MONGODB_DRIVER_REACTIVESTREAMS = Dependency.builder()
            .groupId("org{}mongodb")
            .artifactId("mongodb-driver-reactivestreams")
            .version(DependencyVersions.MONGODB_DRIVER)
            .build();

    public static final Dependency MONGODB_DRIVER_BSON = Dependency.builder()
            .groupId("org{}mongodb")
            .artifactId("bson")
            .version(DependencyVersions.MONGODB_DRIVER)
            .build();

    public static final Dependency MONGODB_DRIVER_KOTLIN_COROUTINE = Dependency.builder()
            .groupId("org{}mongodb")
            .artifactId("mongodb-driver-kotlin-coroutine")
            .version(DependencyVersions.MONGODB_DRIVER)
            .build();

    public static final Dependency REACTIVE_STREAMS = Dependency.builder()
            .groupId("org{}reactivestreams")
            .artifactId("reactive-streams")
            .version(DependencyVersions.REACTIVE_STREAMS)
            .build();

    /**
     * Lettuce
     */
    public static final Dependency LETTUCE = Dependency.builder()
            .groupId("io{}lettuce")
            .artifactId("lettuce-core")
            .version(DependencyVersions.LETTUCE)
            .addRelocation("netty{}handler{}codec{}dns", "io{}netty{}handler{}codec{}dns")
            .addRelocation("netty{}resolver{}dns", "io{}netty{}resolver{}dns")
            .build();

    public static final Dependency JACKSON_CORE = Dependency.builder()
            .groupId("tools{}jackson{}core")
            .artifactId("jackson-core")
            .version(DependencyVersions.JACKSON)
            .build();

    public static final Dependency JACKSON_ANNOTATIONS = Dependency.builder()
            .groupId("com{}fasterxml{}jackson{}core")
            .artifactId("jackson-annotations")
            .version(DependencyVersions.JACKSON_ANNOTATIONS)
            .build();

    public static final Dependency JACKSON_DATABIND = Dependency.builder()
            .groupId("tools{}jackson{}core")
            .artifactId("jackson-databind")
            .version(DependencyVersions.JACKSON)
            .build();

    public static final Dependency JACKSON_DATATYPE = Dependency.builder()
            .groupId("com{}fasterxml{}jackson{}datatype")
            .artifactId("jackson-datatype-jsr310")
            .version(DependencyVersions.JACKSON_DATATYPE)
            .build();

    public static final Dependency NETTY_RESOLVER = Dependency.builder()
            .groupId("io{}netty")
            .artifactId("netty-resolver")
            .version(DependencyVersions.NETTY)
            .build();

    public static final Dependency NETTY_RESOLVER_DNS = Dependency.builder()
            .groupId("io{}netty")
            .artifactId("netty-resolver-dns")
            .version(DependencyVersions.NETTY)
            .addRelocations(LETTUCE.relocations())
            .build();

    public static final Dependency NETTY_CODEC_DNS = Dependency.builder()
            .groupId("io{}netty")
            .artifactId("netty-codec-dns")
            .version(DependencyVersions.NETTY)
            .addRelocations(LETTUCE.relocations())
            .build();

    public static final Dependency REACTOR_CORE = Dependency.builder()
            .groupId("io{}projectreactor")
            .artifactId("reactor-core")
            .version(DependencyVersions.REACTOR)
            .build();
}
