package net.momirealms.sparrow.plugin;

import net.momirealms.sparrow.compatibility.CompatibilityManager;
import net.momirealms.sparrow.feature.FeatureManager;
import net.momirealms.sparrow.teleport.TeleportService;
import net.momirealms.sparrow.database.DataStorage;
import net.momirealms.sparrow.data.DataAccess;
import net.momirealms.sparrow.player.PlayerManager;
import net.momirealms.sparrow.plugin.configuration.ConfigurationManager;
import net.momirealms.sparrow.plugin.dependency.Dependency;
import net.momirealms.sparrow.plugin.dependency.DependencyManager;
import net.momirealms.sparrow.locale.TranslationManager;
import net.momirealms.sparrow.plugin.classpath.ClassPathAppender;
import net.momirealms.sparrow.plugin.logger.PluginLogger;
import net.momirealms.sparrow.plugin.scheduler.SchedulerAdapter;
import net.momirealms.sparrow.redis.RedisConnector;
import net.momirealms.sparrow.redis.MessageBrokerManager;
import net.momirealms.sparrow.server.ServerManager;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public interface Plugin {

    boolean isReloading();

    boolean isInitializing();

    String pluginVersion();

    PluginLogger logger();

    File dataFolderFile();

    Path dataFolderPath();

    void onPluginLoad();

    void onPluginEnable();

    void onPluginDisable();

    List<Dependency> platformDependencies();

    void setupProxy();

    InputStream resourceStream(String filePath);

    void saveResource(String filePath);

    ClassPathAppender sharedClassPathAppender();

    ClassPathAppender privateClassPathAppender();

    SchedulerAdapter scheduler();

    DependencyManager dependencyManager();

    DataStorage dataStorage();

    DataAccess dataAccess();

    RedisConnector redisConnector();

    MessageBrokerManager messageBrokerManager();

    ServerManager serverManager();

    CompatibilityManager compatibilityManager();

    ConfigurationManager configurationManager();

    TranslationManager translationManager();

    PlayerManager playerManager();

    TeleportService teleportService();

    FeatureManager featureManager();
}