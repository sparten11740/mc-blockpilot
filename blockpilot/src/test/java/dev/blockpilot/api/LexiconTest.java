package dev.blockpilot.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Each production with input it accepts and input it rejects, including where its scanner backs off. */
class LexiconTest {
    @ParameterizedTest
    @ValueSource(strings = {"1,2,3", "~0,-1,0", "-1.5,64,0.25", "~1.5,0,-2"})
    void positions(String token) {
        assertTrue(Lexicon.isPosition(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "~", "1,2", "1,2,3,4", "1,,3", "~~1,2,3", "1.,2,3", "a,b,c", "1,2,3x", "+1,2,3", "1,2,"})
    void notPositions(String token) {
        assertFalse(Lexicon.isPosition(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"120,40", "0.5,-3"})
    void points(String token) {
        assertTrue(Lexicon.isPoint(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"120", "120,", "1,2,3", "~1,2", "1;2"})
    void notPoints(String token) {
        assertFalse(Lexicon.isPoint(token));
    }

    @Test
    void numberedReferences() {
        assertTrue(Lexicon.isEntity("e812"));
        assertTrue(Lexicon.isWidget("w0"));
        assertTrue(Lexicon.isMenuSlot("s35"));
        assertTrue(Lexicon.isCount("x6"));
        assertTrue(Lexicon.isEntity("e2147483647"));
        assertFalse(Lexicon.isEntity("e2147483648"));
        assertFalse(Lexicon.isEntity("e"));
        assertFalse(Lexicon.isEntity("e-1"));
        assertFalse(Lexicon.isEntity("e12a"));
        assertFalse(Lexicon.isEntity("x12"));
        assertFalse(Lexicon.isWidget("w"));
        assertFalse(Lexicon.isMenuSlot("slot"));
        assertFalse(Lexicon.isCount("x1.5"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0f3a9c2e-1b2c-4d5e-8f90-a1b2c3d4e5f6", "0F3A9C2E-1B2C-4D5E-8F90-A1B2C3D4E5F6"})
    void uuids(String token) {
        assertTrue(Lexicon.isUuid(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0f3a9c2e-1b2c-4d5e-8f90-a1b2c3d4e5f", "0f3a9c2e-1b2c-4d5e-8f90-a1b2c3d4e5f6a",
        "0f3a9c2e1b2c-4d5e-8f90-a1b2c3d4e5f6", "0f3a9c2g-1b2c-4d5e-8f90-a1b2c3d4e5f6", "", "0f3a"})
    void notUuids(String token) {
        assertFalse(Lexicon.isUuid(token));
    }

    @Test
    void integers() {
        assertTrue(Lexicon.isInteger("0"));
        assertTrue(Lexicon.isInteger("-12"));
        assertTrue(Lexicon.isInteger("007"));
        assertTrue(Lexicon.isInteger("2147483647"));
        assertTrue(Lexicon.isInteger("-2147483648"));
        assertFalse(Lexicon.isInteger("2147483648"));
        assertFalse(Lexicon.isInteger("-2147483649"));
        assertFalse(Lexicon.isInteger("99999999999999999999"));
        assertFalse(Lexicon.isInteger("1.5"));
        assertFalse(Lexicon.isInteger("-"));
        assertFalse(Lexicon.isInteger(""));
        assertFalse(Lexicon.isInteger("12a"));
    }

    @Test
    void decimals() {
        assertTrue(Lexicon.isDecimal("3"));
        assertTrue(Lexicon.isDecimal("-0.25"));
        assertTrue(Lexicon.isDecimal("12345678901234567890.5"));
        assertFalse(Lexicon.isDecimal("1."));
        assertFalse(Lexicon.isDecimal(".5"));
        assertFalse(Lexicon.isDecimal("-"));
        assertFalse(Lexicon.isDecimal("1e5"));
        assertFalse(Lexicon.isDecimal("+1"));
    }

    @Test
    void durations() {
        assertTrue(Lexicon.isDuration("20t"));
        assertTrue(Lexicon.isDuration("3s"));
        assertTrue(Lexicon.isDuration("0.5s"));
        assertTrue(Lexicon.isDuration("2147483647t"));
        assertFalse(Lexicon.isDuration("2147483648t"));
        assertFalse(Lexicon.isDuration("200000000s"));
        assertFalse(Lexicon.isDuration("1.5t"));
        assertFalse(Lexicon.isDuration("-5s"));
        assertFalse(Lexicon.isDuration("-5t"));
        assertFalse(Lexicon.isDuration("5"));
        assertFalse(Lexicon.isDuration("5m"));
        assertFalse(Lexicon.isDuration("t"));
        assertFalse(Lexicon.isDuration("5ts"));
        assertFalse(Lexicon.isDuration("5sx"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"stone", "minecraft:stone", "create:cogwheel", "c:ores/iron", "a.b-c_d", "5"})
    void ids(String token) {
        assertTrue(Lexicon.isId(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ":stone", "minecraft:", "Stone", "a:b:c", "ores/iron:x", "#logs", "*ore*", "a b"})
    void notIds(String token) {
        assertFalse(Lexicon.isId(token));
    }

    @Test
    void tagsAndPatterns() {
        assertTrue(Lexicon.isTag("#logs"));
        assertTrue(Lexicon.isTag("#minecraft:logs"));
        assertFalse(Lexicon.isTag("#"));
        assertFalse(Lexicon.isTag("logs"));
        assertFalse(Lexicon.isTag("#Logs"));
        assertTrue(Lexicon.isPattern("*ore*"));
        assertTrue(Lexicon.isPattern("create:*"));
        assertTrue(Lexicon.isPattern("*"));
        assertFalse(Lexicon.isPattern("stone"));
        assertFalse(Lexicon.isPattern(""));
        assertFalse(Lexicon.isPattern("*Ore"));
    }

    @Test
    void identifiersAndFieldPaths() {
        assertTrue(Lexicon.isIdentifier("max_hp"));
        assertTrue(Lexicon.isIdentifier("_x9"));
        assertTrue(Lexicon.isIdentifier("Ab"));
        assertFalse(Lexicon.isIdentifier("[a"));
        assertFalse(Lexicon.isIdentifier("~a"));
        assertFalse(Lexicon.isIdentifier("9x"));
        assertFalse(Lexicon.isIdentifier(""));
        assertFalse(Lexicon.isIdentifier("a-b"));
        assertTrue(Lexicon.isFieldPath("props.lit"));
        assertTrue(Lexicon.isFieldPath("rows.0.id"));
        assertFalse(Lexicon.isFieldPath("props."));
        assertFalse(Lexicon.isFieldPath(".lit"));
        assertFalse(Lexicon.isFieldPath("a..b"));
    }

    @Test
    void options() {
        assertEquals(4, Lexicon.optionEquals("near=2"));
        assertEquals(5, Lexicon.optionEquals("since="));
        assertEquals(20, Lexicon.optionEquals("rule:doDaylightCycle=false"));
        assertEquals(7, Lexicon.optionEquals("timeout=<5"));
        assertEquals(-1, Lexicon.optionEquals("id==air"));
        assertEquals(-1, Lexicon.optionEquals("rule:=x"));
        assertEquals(-1, Lexicon.optionEquals("9a=b"));
        assertEquals(-1, Lexicon.optionEquals("near"));
        assertEquals(-1, Lexicon.optionEquals("x<=5"));
    }

    @Test
    void tests() {
        assertEquals(new Lexicon.Test("id", "==", "air"), Lexicon.test("id==air"));
        assertEquals(new Lexicon.Test("x", "<=", "5"), Lexicon.test("x<=5"));
        assertEquals(new Lexicon.Test("hp", "<", "$max_hp"), Lexicon.test("hp<$max_hp"));
        assertEquals(new Lexicon.Test("pos.y", ">=", "64"), Lexicon.test("pos.y>=64"));
        assertEquals(new Lexicon.Test("a", "!=", "b"), Lexicon.test("a!=b"));
        assertEquals(new Lexicon.Test("a", ">=", "=b"), Lexicon.test("a>==b"));
        assertNull(Lexicon.test("id="));
        assertNull(Lexicon.test("id=="));
        assertNull(Lexicon.test("id"));
        assertNull(Lexicon.test("==air"));
        assertNull(Lexicon.test("near=2"));
    }

    @Test
    void words() {
        assertTrue(Lexicon.isWord("stone"));
        assertTrue(Lexicon.isWord("ok"));
        assertTrue(Lexicon.isWord("stone[facing=north]"));
        assertFalse(Lexicon.isWord(""));
        assertFalse(Lexicon.isWord("near=2"));
        assertFalse(Lexicon.isWord("id==air"));
    }

    @Test
    void parsersUseTheProductions() {
        assertEquals(Position.relative(0, -1, 0), Position.parse("~0,-1,0"));
        assertEquals(Position.absolute(1.5, 64, -2), Position.parse("1.5,64,-2"));
        assertNull(Position.parse("1,2"));
        assertEquals(new Duration(20), Duration.parse("20t"));
        assertEquals(new Duration(10), Duration.parse("0.5s"));
        assertNull(Duration.parse("-5t"));
        assertEquals(new EntityRef(812), EntityRef.parse("e812"));
        assertNull(EntityRef.parse("e99999999999"));
    }

    @Test
    void printedTokensParseBack() {
        for (Position position : new Position[] {Position.relative(3, -1, 2), Position.absolute(104.5, 64, -33.25)}) {
            assertEquals(position, Position.parse(position.toString()));
        }
        assertEquals("~3,-1,2", Position.relative(3, -1, 2).toString());
        assertEquals(new Duration(40), Duration.parse(new Duration(40).toString()));
        assertEquals(new EntityRef(7), EntityRef.parse(new EntityRef(7).toString()));
    }
}
