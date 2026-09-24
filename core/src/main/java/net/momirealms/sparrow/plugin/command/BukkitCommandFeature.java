package net.momirealms.sparrow.plugin.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import net.momirealms.sparrow.util.Pair;
import org.bukkit.entity.Entity;
import org.incendo.cloud.bukkit.data.Selector;

import java.util.Collection;

public abstract class BukkitCommandFeature extends AbstractCommandFeature {

    public BukkitCommandFeature(CommandManager commandManager, SparrowPlugin plugin) {
        super(commandManager, plugin);
    }

    /**
     * 根据 Cloud Selector 解析实体选择结果, 并返回对应的翻译键与参数组件.
     * 当选择到 1 个实体时返回 single, 参数为实体名称. 当选择到多个实体时返回 multiple, 参数为实体数量.
     *
     * @param selector Cloud 选择器, 用于获取实体集合
     * @param single 单个实体时使用的翻译键构建器
     * @param multiple 多个实体时使用的翻译键构建器
     * @return left 为翻译键构建器, right 为用于填充翻译参数的组件
     */
    public Pair<TranslatableComponent.Builder, Component> resolveSelector(Selector<? extends Entity> selector, TranslatableComponent.Builder single, TranslatableComponent.Builder multiple) {
        Collection<? extends Entity> entities = selector.values();
        if (entities.size() == 1) {
            return Pair.of(single, Component.text(entities.iterator().next().getName()));
        } else {
            return Pair.of(multiple, Component.text(entities.size()));
        }
    }

    /**
     * 根据实体集合解析选择结果, 并返回对应的翻译键与参数组件.
     * 当集合大小为 1 时返回 single, 参数为实体名称. 当集合大小大于 1 时返回 multiple, 参数为实体数量.
     *
     * @param selector 实体集合
     * @param single 单个实体时使用的翻译键构建器
     * @param multiple 多个实体时使用的翻译键构建器
     * @return left 为翻译键构建器, right 为用于填充翻译参数的组件
     */
    public Pair<TranslatableComponent.Builder, Component> resolveSelector(Collection<? extends Entity> selector, TranslatableComponent.Builder single, TranslatableComponent.Builder multiple) {
        if (selector.size() == 1) {
            return Pair.of(single, Component.text(selector.iterator().next().getName()));
        } else {
            return Pair.of(multiple, Component.text(selector.size()));
        }
    }

    @Override
    public SparrowPlugin plugin() {
        return (SparrowPlugin) super.plugin();
    }
}
