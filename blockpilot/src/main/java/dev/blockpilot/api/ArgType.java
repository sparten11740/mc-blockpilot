package dev.blockpilot.api;

/** The token kinds a command argument can take. */
public enum ArgType {
    /** {@code x,y,z} or {@code ~dx,dy,dz}. */
    POSITION,
    /** {@code e<id>}. */
    ENTITY,
    /** A position or an entity reference. */
    TARGET,
    /** Any single token, e.g. an identifier, a {@code #tag} or a name. */
    WORD,
    INT,
    NUMBER,
    /** {@code x<n>}. */
    COUNT,
    /** {@code <n>t} or {@code <n>s}. */
    DURATION,
    /** The rest of the command as free text; {@code \;} stands for a literal semicolon. */
    TEXT,
    /** One of a fixed set of words. */
    CHOICE
}
