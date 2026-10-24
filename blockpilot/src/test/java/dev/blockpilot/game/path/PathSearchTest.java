package dev.blockpilot.game.path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PathSearchTest {
    /** A small world: a floor at y=0 (stand at y=1) unless overridden; everything else air. */
    private static final class Grid implements Terrain {
        final Set<String> solid = new HashSet<>();
        final Set<String> water = new HashSet<>();
        final Set<String> ladders = new HashSet<>();
        final Set<String> lava = new HashSet<>();

        Grid floor(int fromX, int toX, int fromZ, int toZ, int y) {
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    solid.add(key(x, y, z));
                }
            }
            return this;
        }

        Grid wall(int x, int fromY, int toY, int fromZ, int toZ) {
            for (int y = fromY; y <= toY; y++) {
                for (int z = fromZ; z <= toZ; z++) {
                    solid.add(key(x, y, z));
                }
            }
            return this;
        }

        static String key(int x, int y, int z) {
            return x + "," + y + "," + z;
        }

        @Override
        public boolean loaded(int x, int y, int z) {
            return Math.abs(x) < 64 && Math.abs(z) < 64 && y > -16 && y < 64;
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return solid.contains(key(x, y, z));
        }

        @Override
        public boolean passable(int x, int y, int z) {
            return !solid.contains(key(x, y, z)) && !lava.contains(key(x, y, z));
        }

        @Override
        public boolean water(int x, int y, int z) {
            return water.contains(key(x, y, z));
        }

        @Override
        public boolean climbable(int x, int y, int z) {
            return ladders.contains(key(x, y, z));
        }

        @Override
        public boolean dangerous(int x, int y, int z) {
            return lava.contains(key(x, y, z));
        }
    }

    private static PathSearch search(Terrain terrain, int toX, int toY, int toZ, double near) {
        PathSearch search = new PathSearch(terrain, new PathSearch.Node(0, 1, 0), toX, toY, toZ, near, 20000);
        while (search.step(500) == PathSearch.State.SEARCHING) {
            // Keep stepping, as the game would over several ticks.
        }
        return search;
    }

    @Test
    void walksAcrossAFloor() {
        Grid grid = new Grid().floor(-5, 10, -5, 5, 0);
        PathSearch search = search(grid, 8, 1, 0, 0);
        assertEquals(PathSearch.State.FOUND, search.state());
        assertEquals(new PathSearch.Node(8, 1, 0), search.path().get(search.path().size() - 1));
        assertEquals(9, search.path().size());
    }

    @Test
    void stepsUpOneBlockButNotTwo() {
        Grid grid = new Grid().floor(-5, 10, -5, 5, 0).floor(4, 10, -5, 5, 1);
        assertEquals(PathSearch.State.FOUND, search(grid, 6, 2, 0, 0).state());
        Grid tall = new Grid().floor(-5, 3, -5, 5, 0).wall(4, 1, 6, -5, 5).floor(4, 10, -5, 5, 6);
        assertEquals(PathSearch.State.NO_PATH, search(tall, 6, 7, 0, 0).state());
    }

    @Test
    void dropsUpToThreeBlocks() {
        Grid ledge = new Grid().floor(-5, 2, -5, 5, 0).floor(3, 10, -5, 5, -3);
        assertEquals(PathSearch.State.FOUND, search(ledge, 6, -2, 0, 0).state());
        Grid cliff = new Grid().floor(-5, 2, -5, 5, 0).floor(3, 10, -5, 5, -5);
        assertEquals(PathSearch.State.NO_PATH, search(cliff, 6, -4, 0, 0).state());
    }

    @Test
    void climbsLadders() {
        Grid grid = new Grid().floor(-5, 10, -5, 5, 0).wall(2, 1, 6, -5, 5).floor(-5, 10, -5, 5, 6);
        for (int y = 1; y <= 6; y++) {
            grid.ladders.add(Grid.key(1, y, 0));
        }
        grid.solid.remove(Grid.key(1, 6, 0));
        PathSearch search = search(grid, 1, 7, 1, 0);
        assertEquals(PathSearch.State.FOUND, search.state(), "expanded " + search.expanded());
    }

    @Test
    void reportsUnreachableTargets() {
        Grid boxed = new Grid().floor(-5, 10, -5, 5, 0).wall(5, 1, 3, -1, 1).wall(7, 1, 3, -1, 1);
        boxed.wall(6, 1, 3, -1, -1).wall(6, 1, 3, 1, 1).wall(6, 3, 3, 0, 0);
        assertEquals(PathSearch.State.NO_PATH, search(boxed, 6, 1, 0, 0).state());
    }

    @Test
    void stopsWithinNear() {
        Grid grid = new Grid().floor(-5, 10, -5, 5, 0);
        List<PathSearch.Node> path = search(grid, 8, 1, 0, 2).path();
        assertTrue(path.get(path.size() - 1).distance(8, 1, 0) <= 2);
        assertTrue(path.size() < 9);
    }

    @Test
    void keepsAwayFromLava() {
        Grid grid = new Grid().floor(-5, 10, -5, 5, 0);
        for (int x = 1; x <= 6; x++) {
            grid.lava.add(Grid.key(x, 1, 0));
            grid.solid.remove(Grid.key(x, 0, 0));
            grid.lava.add(Grid.key(x, 0, 0));
        }
        PathSearch search = search(grid, 8, 1, 0, 0);
        assertEquals(PathSearch.State.FOUND, search.state());
        assertTrue(search.path().stream().noneMatch(node -> node.z() == 0 && node.x() >= 1 && node.x() <= 6));
    }
}
