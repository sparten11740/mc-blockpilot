package dev.blockpilot.game.path;

/** What the pathfinder needs to know about the world, so it can be tested without one. */
public interface Terrain {
    /** Whether the block's chunk is loaded; paths never leave loaded terrain. */
    boolean loaded(int x, int y, int z);

    /** Something the player can stand on top of. */
    boolean solid(int x, int y, int z);

    /** Space the player's body can be in. */
    boolean passable(int x, int y, int z);

    boolean water(int x, int y, int z);

    /** Ladders, vines and the like. */
    boolean climbable(int x, int y, int z);

    /** Lava, fire, cactus, magma…: never stood in or next to if avoidable. */
    boolean dangerous(int x, int y, int z);
}
