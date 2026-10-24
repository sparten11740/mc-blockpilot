package dev.blockpilot.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Duration;
import dev.blockpilot.api.EntityRef;
import dev.blockpilot.api.Position;
import dev.blockpilot.api.Result;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParsingTest {
    private static final CommandSpec MINE = CommandSpec.action("mine", (context, args) -> () -> Result.ok())
        .arg("target", ArgType.WORD).optionalArg("count", ArgType.COUNT).option("near", "radius");
    private static final CommandSpec CHAT = CommandSpec.action("chat", (context, args) -> () -> Result.ok())
        .arg("text", ArgType.TEXT);
    private static final CommandSpec LOOK = CommandSpec.query("look", (context, args) -> Result.ok())
        .optionalArg("radius", ArgType.INT);

    @Test
    void splitsScriptsAtSemicolonsAndNewlines() {
        List<RawCommand> commands = ScriptParser.parse("select iron_pickaxe; mine ~0,-1,0\nstatus;;  ");
        assertEquals(List.of("select", "mine", "status"), commands.stream().map(RawCommand::name).toList());
        assertEquals("~0,-1,0", commands.get(1).tokens().get(0).text());
    }

    @Test
    void escapedSemicolonStaysInFreeText() throws CommandException {
        List<RawCommand> commands = ScriptParser.parse("chat hi\\; brb; status");
        assertEquals(2, commands.size());
        assertEquals("hi; brb", ArgParser.parse(CHAT, commands.get(0)).text("text"));
    }

    @Test
    void parsesPositionTokens() {
        assertEquals(Position.absolute(104.5, 64, -33.2), Position.parse("104.5,64,-33.2"));
        assertEquals(Position.relative(3, -1, 2), Position.parse("~3,-1,2"));
        assertNull(Position.parse("3,4"));
        assertNull(Position.parse("a,b,c"));
        assertEquals("~3,-1,2", Position.relative(3, -1, 2).toString());
        assertEquals("104.5,64,-33.2", Position.absolute(104.5, 64, -33.2).toString());
    }

    @Test
    void parsesEntityAndDurationTokens() {
        assertEquals(new EntityRef(812), EntityRef.parse("e812"));
        assertNull(EntityRef.parse("eye"));
        assertEquals(40, Duration.parse("40t").ticks());
        assertEquals(60, Duration.parse("3s").ticks());
        assertNull(Duration.parse("3m"));
    }

    @Test
    void parsesCountsAndOptionsAnywhere() throws CommandException {
        Args args = ArgParser.parse(MINE, RawCommand.of("mine timeout=90 #logs x6 near=3"));
        assertEquals("#logs", args.word("target"));
        assertEquals(6, args.count("count", 1));
        assertEquals("3", args.option("near"));
        assertEquals(Duration.seconds(90), args.durationOption("timeout"));
    }

    @Test
    void skipsOptionalArgumentsThatDoNotMatch() throws CommandException {
        Args args = ArgParser.parse(MINE, RawCommand.of("mine oak_log"));
        assertFalse(args.has("count"));
        assertEquals(1, args.count("count", 1));
        assertEquals(16, ArgParser.parse(LOOK, RawCommand.of("look")).integer("radius", 16));
    }

    @Test
    void reportsSyntaxErrors() {
        CommandException missing = assertThrows(CommandException.class,
            () -> ArgParser.parse(MINE, RawCommand.of("mine")));
        assertEquals("syntax", missing.code());
        CommandException unexpected = assertThrows(CommandException.class,
            () -> ArgParser.parse(LOOK, RawCommand.of("look 12 extra")));
        assertTrue(unexpected.argument().contains("unexpected extra"));
        CommandException unknownOption = assertThrows(CommandException.class,
            () -> ArgParser.parse(LOOK, RawCommand.of("look bogus=1")));
        assertEquals("syntax", unknownOption.code());
    }

    @Test
    void timeoutIsOnlyAnOptionForActions() {
        assertThrows(CommandException.class, () -> ArgParser.parse(LOOK, RawCommand.of("look timeout=5")));
    }
}
