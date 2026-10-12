package net.momirealms.sparrow.teleport.search;

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

        public int horizontalRadius() {
            return this.nearby.horizontalRadius;
        }

        public int verticalRadius() {
            return this.nearby.verticalRadius;
        }

        public int maxChecks() {
            return this.maxChecks;
        }

        public boolean allowNetherRoof() {
            return this.allowNetherRoof;
        }

        public boolean forceOnFailure() {
            return this.forceOnFailure;
        }

        public int fallbackMinY() {
            return this.fallbackMinY;
        }

        @Configuration(naming = Configuration.Naming.KEBAB_CASE)
        private static final class Range {
            private int horizontalRadius = 8;
            private int verticalRadius = 16;
        }
    }

    @Configuration(naming = Configuration.Naming.KEBAB_CASE)
    final class Random implements TeleportSearchOptions {
        private int maxAttempts = 32;
        private int maxChecks = 10_000;
        private Range nearby = new Range();
        private boolean allowNetherRoof = false;

        @Override
        public void validate() {
            if (this.maxAttempts <= 0) throw new IllegalArgumentException("search.max-attempts must be positive");
            if (this.maxChecks <= 0) throw new IllegalArgumentException("search.max-checks must be positive");
            if (this.nearby.horizontalRadius < 0) throw new IllegalArgumentException("search.nearby.horizontal-radius must be non-negative");
            if (this.nearby.verticalRadius < 0) throw new IllegalArgumentException("search.nearby.vertical-radius must be non-negative");
        }

        int maxAttempts() {
            return this.maxAttempts;
        }

        int maxChecks() {
            return this.maxChecks;
        }

        int horizontalRadius() {
            return this.nearby.horizontalRadius;
        }

        int verticalRadius() {
            return this.nearby.verticalRadius;
        }

        boolean allowNetherRoof() {
            return this.allowNetherRoof;
        }

        @Configuration(naming = Configuration.Naming.KEBAB_CASE)
        private static final class Range {
            private int horizontalRadius = 2;
            private int verticalRadius = 4;
        }
    }
}