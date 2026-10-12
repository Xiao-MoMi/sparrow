package net.momirealms.sparrow.teleport;

import net.momirealms.sparrow.yaml.serializer.auto.annotation.Configuration;

public sealed interface TeleportSearchOptions {

    default void validate() {
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    final class Direct implements TeleportSearchOptions {
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    final class Nearby implements TeleportSearchOptions {
        private Range nearby = new Range();
        private int maxChecks = 10_000;
        private boolean allowNetherRoof = false;
        private boolean forceOnFailure = false;
        private int fallbackMinY = -63;

        @Override
        public void validate() {
            if (this.nearby.horizontalRadius < 0) throw new IllegalArgumentException("search.nearby.horizontal-radius must be non-negative");
            if (this.nearby.verticalRadius < 0) throw new IllegalArgumentException("search.nearby.vertical-radius must be non-negative");
            if (this.maxChecks <= 0) throw new IllegalArgumentException("search.max-checks must be positive");
        }

        int horizontalRadius() {
            return this.nearby.horizontalRadius;
        }

        int verticalRadius() {
            return this.nearby.verticalRadius;
        }

        int maxChecks() {
            return this.maxChecks;
        }

        boolean allowNetherRoof() {
            return this.allowNetherRoof;
        }

        boolean forceOnFailure() {
            return this.forceOnFailure;
        }

        int fallbackMinY() {
            return this.fallbackMinY;
        }

        @Configuration(naming = Configuration.Naming.KEBAB_CASE)
        private static final class Range {
            private int horizontalRadius = 8;
            private int verticalRadius = 16;
        }
    }
}