package net.momirealms.sparrow.plugin.dependency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DependencyTest {

    @Test
    void equalDependenciesHaveEqualHashCodesRegardlessOfClassifier() {
        Dependency first = dependency("");
        Dependency second = dependency("sources");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    private static Dependency dependency(String classifier) {
        return Dependency.builder()
                .groupId("org.example")
                .artifactId("example")
                .classifier(classifier)
                .version("1.0")
                .build();
    }
}
