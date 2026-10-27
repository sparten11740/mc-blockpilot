package dev.blockpilot.game.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * A* over block positions (the cell the player's feet are in). Moves: walk and diagonal walk, step up one block,
 * drop up to three, swim, climb ladders. It never breaks or places blocks. The search runs in slices of a node
 * budget, so a long search spreads over several ticks instead of stalling one frame.
 */
public final class PathSearch {
    public static final int MAX_DROP = 3;

    /** A cell on the path. */
    public record Node(int x, int y, int z) {
        long key() {
            return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
        }

        double distance(int toX, int toY, int toZ) {
            double dx = toX - x;
            double dy = toY - y;
            double dz = toZ - z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    public enum State { SEARCHING, FOUND, NO_PATH }

    /** Why the player can't be at a cell. */
    public enum Blocker { UNLOADED, FILLED, NO_HEADROOM, DANGEROUS, NO_FLOOR }

    private record Open(Node node, double cost, double estimate) {
    }

    private static final int[][] FLAT = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONAL = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private final Terrain terrain;
    private final int goalX;
    private final int goalY;
    private final int goalZ;
    private final double near;
    private final int maxNodes;
    private final PriorityQueue<Open> open = new PriorityQueue<>((a, b) -> Double.compare(a.estimate(), b.estimate()));
    private final Map<Long, Double> best = new HashMap<>();
    private final Map<Long, Node> parent = new HashMap<>();
    private int expanded;
    private State state = State.SEARCHING;
    private List<Node> path = List.of();

    public PathSearch(Terrain terrain, Node start, int goalX, int goalY, int goalZ, double near, int maxNodes) {
        this.terrain = terrain;
        this.goalX = goalX;
        this.goalY = goalY;
        this.goalZ = goalZ;
        this.near = Math.max(near, 0.0);
        this.maxNodes = maxNodes;
        open.add(new Open(start, 0, start.distance(goalX, goalY, goalZ)));
        best.put(start.key(), 0.0);
    }

    public State state() {
        return state;
    }

    public List<Node> path() {
        return path;
    }

    public int expanded() {
        return expanded;
    }

    /** Expands up to {@code budget} nodes; returns the state afterwards. */
    public State step(int budget) {
        for (int index = 0; index < budget && state == State.SEARCHING; index++) {
            Open current = open.poll();
            if (current == null || expanded >= maxNodes) {
                state = State.NO_PATH;
                break;
            }
            Node node = current.node();
            if (current.cost() > best.getOrDefault(node.key(), Double.MAX_VALUE)) {
                continue;
            }
            expanded++;
            if (reached(node)) {
                state = State.FOUND;
                path = trace(node);
                break;
            }
            for (Move move : neighbours(node)) {
                double cost = current.cost() + move.cost();
                long key = move.node().key();
                if (cost < best.getOrDefault(key, Double.MAX_VALUE)) {
                    best.put(key, cost);
                    parent.put(key, node);
                    open.add(new Open(move.node(), cost,
                        cost + move.node().distance(goalX, goalY, goalZ)));
                }
            }
        }
        return state;
    }

    private boolean reached(Node node) {
        if (near < 1) {
            return node.x() == goalX && node.y() == goalY && node.z() == goalZ;
        }
        return node.distance(goalX, goalY, goalZ) <= near;
    }

    private List<Node> trace(Node end) {
        List<Node> nodes = new ArrayList<>();
        for (Node node = end; node != null; node = parent.get(node.key())) {
            nodes.add(node);
        }
        Collections.reverse(nodes);
        return nodes;
    }

    private record Move(Node node, double cost) {
    }

    /**
     * Why the player can't be at this cell, or null when it can: two blocks of room and something to stand on (or
     * water, a ladder).
     */
    public static Blocker blocker(Terrain terrain, int x, int y, int z) {
        if (!terrain.loaded(x, y, z)) {
            return Blocker.UNLOADED;
        }
        if (!terrain.passable(x, y, z)) {
            return Blocker.FILLED;
        }
        if (!terrain.passable(x, y + 1, z)) {
            return Blocker.NO_HEADROOM;
        }
        if (terrain.dangerous(x, y, z) || terrain.dangerous(x, y + 1, z)) {
            return Blocker.DANGEROUS;
        }
        if (terrain.water(x, y, z) || terrain.climbable(x, y, z)) {
            return null;
        }
        if (!terrain.solid(x, y - 1, z)) {
            return Blocker.NO_FLOOR;
        }
        return terrain.dangerous(x, y - 1, z) ? Blocker.DANGEROUS : null;
    }

    boolean standable(int x, int y, int z) {
        return blocker(terrain, x, y, z) == null;
    }

    private List<Move> neighbours(Node node) {
        List<Move> moves = new ArrayList<>();
        int x = node.x();
        int y = node.y();
        int z = node.z();
        boolean swimming = terrain.water(x, y, z);
        for (int[] direction : FLAT) {
            int nx = x + direction[0];
            int nz = z + direction[1];
            if (standable(nx, y, nz)) {
                moves.add(new Move(new Node(nx, y, nz), penalty(nx, y, nz, swimming ? 2 : 1)));
            } else if (standable(nx, y + 1, nz) && terrain.passable(x, y + 2, z)) {
                moves.add(new Move(new Node(nx, y + 1, nz), penalty(nx, y + 1, nz, 2)));
            } else if (terrain.passable(nx, y, nz) && terrain.passable(nx, y + 1, nz)) {
                for (int drop = 1; drop <= MAX_DROP; drop++) {
                    if (standable(nx, y - drop, nz)) {
                        moves.add(new Move(new Node(nx, y - drop, nz), penalty(nx, y - drop, nz, 1 + 0.5 * drop)));
                        break;
                    }
                    if (!terrain.passable(nx, y - drop, nz)) {
                        break;
                    }
                }
            }
        }
        for (int[] direction : DIAGONAL) {
            int nx = x + direction[0];
            int nz = z + direction[1];
            boolean clear = terrain.passable(nx, y, z) && terrain.passable(nx, y + 1, z)
                && terrain.passable(x, y, nz) && terrain.passable(x, y + 1, nz);
            if (clear && standable(nx, y, nz)) {
                moves.add(new Move(new Node(nx, y, nz), penalty(nx, y, nz, swimming ? 2.8 : 1.414)));
            }
        }
        if ((terrain.climbable(x, y, z) || swimming) && standable(x, y + 1, z)) {
            moves.add(new Move(new Node(x, y + 1, z), swimming ? 2 : 1.5));
        }
        if ((terrain.climbable(x, y - 1, z) || terrain.water(x, y - 1, z)) && standable(x, y - 1, z)) {
            moves.add(new Move(new Node(x, y - 1, z), 1.5));
        }
        return moves;
    }

    /** Extra cost for cells next to danger, so paths keep a margin from lava and fire. */
    private double penalty(int x, int y, int z, double base) {
        for (int[] direction : FLAT) {
            if (terrain.dangerous(x + direction[0], y, z + direction[1])
                || terrain.dangerous(x + direction[0], y - 1, z + direction[1])) {
                return base + 4;
            }
        }
        return base;
    }
}
