package net.momirealms.sparrow.feature.rtp;

import net.momirealms.sparrow.locale.MessageConstants;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.plugin.command.CommandManager;
import net.momirealms.sparrow.plugin.command.parser.ServerParser;
import net.momirealms.sparrow.plugin.command.parser.ServerWorldParser;
import net.momirealms.sparrow.teleport.position.rtp.RtpArea;
import org.bukkit.command.CommandSender;
import org.incendo.cloud.Command;
import org.incendo.cloud.bukkit.parser.PlayerParser;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.DoubleParser;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.permission.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class RtpRangeCommand extends AbstractRtpCommand {

    public RtpRangeCommand(@NotNull CommandManager commandManager, @NotNull SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    @Override
    public void registerCommand(@NotNull org.incendo.cloud.CommandManager<CommandSender> manager, @NotNull Command.Builder<CommandSender> builder) {
        Permission otherPermission = this.otherPermission(builder);
        manager.command(builder
                .required("player", PlayerParser.playerParser())
                .required("server", ServerParser.all())
                .required("world", ServerWorldParser.byServerId(context -> context.<String>get("server")))
                .required("min-radius", IntegerParser.integerParser(0))
                .required("max-radius", IntegerParser.integerParser(0))
                .flag(manager.flagBuilder("center-x").withComponent(IntegerParser.integerParser()))
                .flag(manager.flagBuilder("center-z").withComponent(IntegerParser.integerParser()))
                .flag(manager.flagBuilder("min-y").withComponent(DoubleParser.doubleParser()))
                .flag(manager.flagBuilder("max-y").withComponent(DoubleParser.doubleParser()))
                .flag(manager.flagBuilder("silent").withAliases("s"))
                .handler(context -> {
                    this.checkOtherPermission(manager, otherPermission, context);
                    this.execute(context);
                }));
    }

    private void execute(@NotNull CommandContext<CommandSender> context) {
        int minRadius = context.get("min-radius");
        int maxRadius = context.get("max-radius");
        Double minY = context.flags().<Double>getValue("min-y").orElse(null);
        Double maxY = context.flags().<Double>getValue("max-y").orElse(null);
        if (minRadius > maxRadius || (minY == null) != (maxY == null) || (minY != null && minY > maxY)) {
            this.handleFeedback(context, MessageConstants.COMMAND_RTP_RANGE_INVALID_AREA);
            return;
        }
        String world = context.get("world");
        int centerX = context.flags().getValue("center-x", 0);
        int centerZ = context.flags().getValue("center-z", 0);
        RtpArea area = squareRing(world, centerX, centerZ, minRadius, maxRadius, minY, maxY);
        this.start(context, world, null, context.get("server"), List.of(area));
    }

    // 到中心的切比雪夫距离在 [min, max] 内的方块列. 最小半径为 0 时是整个正方形, 否则拆成互不重叠的四条边带
    static RtpArea squareRing(@NotNull String world, int centerX, int centerZ, int minRadius, int maxRadius, @Nullable Double minY, @Nullable Double maxY) {
        RtpArea.Builder builder = RtpArea.builder(world);
        if (minRadius == 0) {
            rectangle(builder, centerX - maxRadius, centerZ - maxRadius, centerX + maxRadius, centerZ + maxRadius, minY, maxY);
            return builder.build();
        }
        // 左右两条占满整个高度, 上下两条只覆盖中间剩下的宽度
        rectangle(builder, centerX - maxRadius, centerZ - maxRadius, centerX - minRadius, centerZ + maxRadius, minY, maxY);
        rectangle(builder, centerX + minRadius, centerZ - maxRadius, centerX + maxRadius, centerZ + maxRadius, minY, maxY);
        rectangle(builder, centerX - minRadius + 1, centerZ - maxRadius, centerX + minRadius - 1, centerZ - minRadius, minY, maxY);
        rectangle(builder, centerX - minRadius + 1, centerZ + minRadius, centerX + minRadius - 1, centerZ + maxRadius, minY, maxY);
        return builder.build();
    }

    private static void rectangle(RtpArea.Builder builder, int minX, int minZ, int maxX, int maxZ, @Nullable Double minY, @Nullable Double maxY) {
        if (minY == null) {
            builder.rectangle(minX, minZ, maxX, maxZ);
        } else {
            builder.rectangle(minX, minZ, maxX, maxZ, minY, maxY);
        }
    }

    @NotNull
    @Override
    public String getFeatureID() {
        return "rtp_range";
    }
}