package net.momirealms.sparrow.player.cluster;

import net.momirealms.sparrow.player.PlayerManager;
import net.momirealms.sparrow.plugin.SparrowPlugin;
import org.incendo.cloud.suggestion.Suggestion;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ClusterRosterTest {
    @Test
    void prefixRangesPreserveCaseInsensitiveOrderAndIncludeExactNames() throws Exception {
        Fixture fixture = new Fixture();
        fixture.join("Zoe", "alex", "Alice", "Al", "Bob", "Albert", "Bobby", "A");

        assertEquals(List.of("Al", "Albert", "alex", "Alice"), fixture.names("aL"));
        assertEquals(List.of("A", "Al", "Albert", "alex", "Alice"), fixture.names("A"));
        assertEquals(List.of("Alice"), fixture.names("ALICE"));
        assertEquals(List.of("Bob", "Bobby"), fixture.names("bo"));
        assertEquals(List.of("Zoe"), fixture.names("z"));
    }

    @Test
    void caseEquivalentNamesAreAllIncludedAtTheRangeStart() throws Exception {
        Fixture fixture = new Fixture();
        fixture.join("Alex", "alex", "Alex", "Alexander", "Amy");

        List<String> names = fixture.names("aLEX").stream().map(name -> name.toLowerCase(Locale.ROOT)).toList();

        assertEquals(List.of("alex", "alex", "alex", "alexander"), names);
    }

    @Test
    void emptyRosterAndPrefixesBetweenNamesReturnNoSuggestions() throws Exception {
        Fixture fixture = new Fixture();
        assertTrue(fixture.names("").isEmpty());
        assertTrue(fixture.names("a").isEmpty());
        fixture.join("Al", "Alice", "Bob", "Zoe");

        for (String prefix : List.of("0", "AA", "Alix", "AliceX", "C", "zz")) {
            assertTrue(fixture.names(prefix).isEmpty(), prefix);
        }
    }

    @Test
    void cachedSuggestionsAreReadOnlyAndStayValidAfterRosterChanges() throws Exception {
        Fixture fixture = new Fixture();
        fixture.join("Bob");
        UUID alice = fixture.join("Alice");
        List<Suggestion> all = fixture.roster.suggest("");
        List<Suggestion> matched = fixture.roster.suggest("al");

        assertSame(all, fixture.roster.suggest(""));
        assertSame(all.getFirst(), matched.getFirst());
        assertSame(matched.getFirst(), fixture.roster.suggest("AL").getFirst());
        assertThrows(UnsupportedOperationException.class, () -> all.add(Suggestion.suggestion("Injected")));
        assertThrows(UnsupportedOperationException.class, () -> matched.set(0, Suggestion.suggestion("Injected")));

        fixture.accept.invoke(fixture.roster, new PlayerPresenceMessage("remote", alice, "Alice", false));
        fixture.join("Albert");

        assertEquals(List.of("Albert"), fixture.names("al"));
        assertEquals(List.of("Alice", "Bob"), all.stream().map(Suggestion::suggestion).toList());
        assertEquals(List.of("Alice"), matched.stream().map(Suggestion::suggestion).toList());
    }

    private static final class Fixture {
        private final ClusterRoster roster = new ClusterRoster(mock(SparrowPlugin.class), mock(PlayerManager.class));
        private final Method accept;

        private Fixture() throws Exception {
            this.accept = ClusterRoster.class.getDeclaredMethod("accept", PlayerPresenceMessage.class);
            this.accept.setAccessible(true);
        }

        private void join(String... names) throws Exception {
            for (String name : names) {
                this.join(name);
            }
        }

        private UUID join(String name) throws Exception {
            UUID uuid = UUID.randomUUID();
            this.accept.invoke(this.roster, new PlayerPresenceMessage("remote", uuid, name, true));
            return uuid;
        }

        private List<String> names(String prefix) {
            return this.roster.suggest(prefix).stream().map(Suggestion::suggestion).toList();
        }
    }
}
