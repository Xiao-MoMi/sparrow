package net.momirealms.sparrow.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.Map;
import java.util.Objects;

public final class MessageWrapper {
    private final String raw;
    private final boolean needsMiniMessageResolve;
    private transient volatile Component cached;

    public MessageWrapper(String raw) {
        this.raw = raw != null ? raw : "";
        this.needsMiniMessageResolve = this.raw.contains("<gradient") || this.raw.contains("<rainbow>");
    }

    public static MessageWrapper messageWrapper(String raw) {
        return new MessageWrapper(raw);
    }

    public Component get() {
        Component c = this.cached;
        if (c == null) {
            c = Components.miniMessage(this.raw);
            this.cached = c;
        }
        return c;
    }

    public Component withArgs(Map<String, Object> args) {
        if (this.needsMiniMessageResolve) {
            return Components.miniMessage(this.raw, args);
        }
        return Components.withArgs(this.get(), args);
    }

    public Component withArgsMiniMessage(Map<String, Object> args) {
        return Components.miniMessage(this.raw, args);
    }

    public Component withResolvers(TagResolver... resolvers) {
        return Components.miniMessage(this.raw, resolvers);
    }

    public String raw() {
        return raw;
    }

    @Override
    public String toString() {
        return raw;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MessageWrapper that)) return false;
        return Objects.equals(raw, that.raw);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(raw);
    }
}
