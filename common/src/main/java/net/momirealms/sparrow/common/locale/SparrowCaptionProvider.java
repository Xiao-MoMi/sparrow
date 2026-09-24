package net.momirealms.sparrow.common.locale;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.caption.CaptionProvider;
import org.incendo.cloud.caption.DelegatingCaptionProvider;

public final class SparrowCaptionProvider<C> extends DelegatingCaptionProvider<C> {

    private static final CaptionProvider<?> PROVIDER = CaptionProvider.constantProvider()
            .putCaption(SparrowCaptionKeys.ARGUMENT_PARSE_FAILURE_URL, "")
            .putCaption(SparrowCaptionKeys.ARGUMENT_PARSE_FAILURE_NAMEDTEXTCOLOR, "")
            .build();

    @SuppressWarnings("unchecked")
    @Override
    public @NonNull CaptionProvider<C> delegate() {
        return (CaptionProvider<C>) PROVIDER;
    }
}
