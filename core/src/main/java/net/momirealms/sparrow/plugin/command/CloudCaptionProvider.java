package net.momirealms.sparrow.plugin.command;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.caption.CaptionProvider;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.DelegatingCaptionProvider;

public final class CloudCaptionProvider<C> extends DelegatingCaptionProvider<C> {
    private static final CaptionProvider<?> PROVIDER = CaptionProvider.constantProvider()
            .putCaption(Caption.of("argument.parse.failure.time"), "Invalid duration '{input}'. Use ticks or d/h/m/s/t units, up to 2147483647 ticks")
            .putCaption(Caption.of("argument.parse.failure.duration"), "Invalid duration '{input}'. Units: y, mo, w, d, h, m, s; decimals and combinations work, e.g. 1.5h, 1mo2w")
            .putCaption(Caption.of("argument.parse.failure.namedtextcolor"), "Unknown named color '{input}'")
            .putCaption(Caption.of("argument.parse.failure.server-offline"), "No matching servers are online")
            .putCaption(Caption.of("argument.parse.failure.server-unknown"), "Server '{input}' is not available among the matching online servers")
            .putCaption(Caption.of("argument.parse.failure.server-world"), "World '{input}' does not exist on any matching online server")
            .build();

    @SuppressWarnings("unchecked")
    @Override
    public @NonNull CaptionProvider<C> delegate() {
        return (CaptionProvider<C>) PROVIDER;
    }
}
