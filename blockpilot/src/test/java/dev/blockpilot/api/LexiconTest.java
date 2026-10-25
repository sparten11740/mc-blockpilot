package dev.blockpilot.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Each production with input it accepts and input it rejects, including its value limits. */
class LexiconTest {
    @ParameterizedTest
    @ValueSource(strings = {"1,2,3", "~0,-1,0", "-1.5,64,0.25", "~1.5,0,-2", "20481820,189,-20508791"})
    void positions(String token) {
        assertTrue(Lexicon.isPosition(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "~", "1,2", "1,2,3,4", "1,,3", "~~1,2,3", "1.,2,3", "a,b,c", "1,2,3x", "+1,2,3",
        "1,2,", "~1,~2,~3", "~,~,~", "^0,^0,^1", ".5,1,1", "1234567890,0,0"})
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
        assertTrue(Lexicon.isEntity("e007"));
        assertTrue(Lexicon.isWidget("w0"));
        assertTrue(Lexicon.isMenuSlot("s35"));
        assertTrue(Lexicon.isCount("x6"));
        assertTrue(Lexicon.isEntity("e2147483647"));
        assertFalse(Lexicon.isEntity("e2147483648"));
        assertFalse(Lexicon.isEntity("e"));
        assertFalse(Lexicon.isEntity("e-1"));
        assertFalse(Lexicon.isEntity("e12a"));
        assertFalse(Lexicon.isEntity("E5"));
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
        assertFalse(Lexicon.isInteger("٣"));
    }

    @Test
    void decimals() {
        assertTrue(Lexicon.isDecimal("3"));
        assertTrue(Lexicon.isDecimal("-0.25"));
        assertTrue(Lexicon.isDecimal("123456789.123456789123"));
        assertFalse(Lexicon.isDecimal("1234567890"));
        assertFalse(Lexicon.isDecimal("1."));
        assertFalse(Lexicon.isDecimal(".5"));
        assertFalse(Lexicon.isDecimal("-"));
        assertFalse(Lexicon.isDecimal("1e5"));
        assertFalse(Lexicon.isDecimal("+1"));
        assertFalse(Lexicon.isDecimal("NaN"));
        assertFalse(Lexicon.isDecimal("Infinity"));
    }

    @Test
    void durationsAndSeconds() {
        assertTrue(Lexicon.isDuration("20t"));
        assertTrue(Lexicon.isDuration("3s"));
        assertTrue(Lexicon.isDuration("0.5s"));
        assertTrue(Lexicon.isDuration("2147483647t"));
        assertTrue(Lexicon.isDuration("107374182.35s"));
        assertFalse(Lexicon.isDuration("107374182.4s"));
        assertFalse(Lexicon.isDuration("2147483648t"));
        assertFalse(Lexicon.isDuration("1.5t"));
        assertFalse(Lexicon.isDuration("-5s"));
        assertFalse(Lexicon.isDuration("-5t"));
        assertFalse(Lexicon.isDuration("5"));
        assertFalse(Lexicon.isDuration("5m"));
        assertFalse(Lexicon.isDuration("500ms"));
        assertFalse(Lexicon.isDuration("t"));
        assertFalse(Lexicon.isDuration("s"));
        assertFalse(Lexicon.isDuration("5ts"));
        assertFalse(Lexicon.isDuration("5sx"));
        assertTrue(Lexicon.isSeconds("30"));
        assertTrue(Lexicon.isSeconds("0.5"));
        assertFalse(Lexicon.isSeconds("-5"));
        assertFalse(Lexicon.isSeconds("1e9"));
        assertFalse(Lexicon.isSeconds("200000000"));
        assertFalse(Lexicon.isSeconds("Infinity"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"stone", "minecraft:stone", "create:cogwheel", "c:ores/iron", "a.b-c_d", "full",
        "minecraft:x6", "minecraft:5", "e5x", "x6a", "5.5t", "-5t", "oak_log", "5.", "-"})
    void ids(String token) {
        assertTrue(Lexicon.isId(token));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ":stone", "minecraft:", "Stone", "a:b:c", "ores/iron:x", "#logs", "*ore*", "5", "-5",
        "1.5", "x6", "e5", "w3", "s5", "20t", "1.5s", "x99999999999", "0f3a9c2e-1b2c-4d5e-8f90-a1b2c3d4e5f6", "E5"})
    void notIds(String token) {
        assertFalse(Lexicon.isId(token));
    }

    @Test
    void tagsAndPatterns() {
        assertTrue(Lexicon.isTag("#logs"));
        assertTrue(Lexicon.isTag("#minecraft:logs"));
        assertTrue(Lexicon.isTag("#c:ores"));
        assertFalse(Lexicon.isTag("#"));
        assertFalse(Lexicon.isTag("logs"));
        assertFalse(Lexicon.isTag("#Logs"));
        assertFalse(Lexicon.isTag("#*ore*"));
        assertTrue(Lexicon.isPattern("*ore*"));
        assertTrue(Lexicon.isPattern("create:*"));
        assertTrue(Lexicon.isPattern("*"));
        assertFalse(Lexicon.isPattern("stone"));
        assertFalse(Lexicon.isPattern(""));
        assertFalse(Lexicon.isPattern("*Ore"));
    }

    @Test
    void namesAndNamespaces() {
        assertTrue(Lexicon.isName("mine"));
        assertTrue(Lexicon.isName("tick_step2"));
        assertFalse(Lexicon.isName("Gear"));
        assertFalse(Lexicon.isName("2mine"));
        assertFalse(Lexicon.isName("max-dist"));
        assertFalse(Lexicon.isName(""));
        assertTrue(Lexicon.isNamespace("aerolarge"));
        assertTrue(Lexicon.isNamespace("my.mod-id_2"));
        assertFalse(Lexicon.isNamespace("Sable"));
        assertFalse(Lexicon.isNamespace(""));
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
        assertFalse(Lexicon.isFieldPath("rows.99999999999"));
        assertFalse(Lexicon.isFieldPath("props."));
        assertFalse(Lexicon.isFieldPath(".lit"));
        assertFalse(Lexicon.isFieldPath("a..b"));
    }

    @Test
    void options() {
        assertEquals(4, Lexicon.optionEquals("near=2"));
        assertEquals(20, Lexicon.optionEquals("rule:doDaylightCycle=false"));
        assertEquals(7, Lexicon.optionEquals("timeout=<5"));
        assertEquals(-1, Lexicon.optionEquals("since="));
        assertEquals(-1, Lexicon.optionEquals("id==air"));
        assertEquals(-1, Lexicon.optionEquals("rule:=x"));
        assertEquals(-1, Lexicon.optionEquals("9a=b"));
        assertEquals(-1, Lexicon.optionEquals("near"));
        assertEquals(-1, Lexicon.optionEquals("x<=5"));
        assertTrue(Lexicon.isOptionName("near"));
        assertTrue(Lexicon.isOptionName("rule:keepInventory"));
        assertFalse(Lexicon.isOptionName("max-dist"));
        assertFalse(Lexicon.isOptionName("a:b:c"));
    }

    @Test
    void testShapesSplitAComparison() {
        assertEquals(new Lexicon.Test("id", "==", "air"), Lexicon.testShape("id==air"));
        assertEquals(new Lexicon.Test("x", "<=", "5"), Lexicon.testShape("x<=5"));
        assertEquals(new Lexicon.Test("pos.y", ">=", "64"), Lexicon.testShape("pos.y>=64"));
        assertEquals(new Lexicon.Test("a", "!=", "b"), Lexicon.testShape("a!=b"));
        assertEquals(new Lexicon.Test("a", ">=", "=b"), Lexicon.testShape("a>==b"));
        assertNull(Lexicon.testShape("id="));
        assertNull(Lexicon.testShape("id=="));
        assertNull(Lexicon.testShape("id"));
        assertNull(Lexicon.testShape("==air"));
        assertNull(Lexicon.testShape("near=2"));
    }

    @Test
    void testsCompareWithAValueANumberOrAField() {
        assertTrue(Lexicon.isTest("id==air"));
        assertTrue(Lexicon.isTest("id==minecraft:air"));
        assertTrue(Lexicon.isTest("pos==1,2,3"));
        assertTrue(Lexicon.isTest("hp<10"));
        assertTrue(Lexicon.isTest("hp<$max_hp"));
        assertTrue(Lexicon.isTest("r.luma>=-0.5"));
        assertFalse(Lexicon.isTest("hp<max_hp"));
        assertFalse(Lexicon.isTest("hp>5&&food>3"));
        assertFalse(Lexicon.isTest("a==b|c"));
        assertFalse(Lexicon.isTest("a==$9"));
        assertFalse(Lexicon.isTest("a>==b"));
        assertFalse(Lexicon.isTest("near=2"));
        assertEquals("max_hp", Lexicon.testShape("hp<$max_hp").reference());
        assertNull(Lexicon.testShape("hp<10").reference());
        assertNull(Lexicon.testShape("a==$a-b").reference());
        assertTrue(Lexicon.testShape("hp<10").isOrdering());
        assertFalse(Lexicon.testShape("id!=air").isOrdering());
    }

    @Test
    void words() {
        assertTrue(Lexicon.isWord("stone"));
        assertTrue(Lexicon.isWord("ok"));
        assertTrue(Lexicon.isWord("since="));
        assertTrue(Lexicon.isWord("stone[facing=north]"));
        assertFalse(Lexicon.isWord(""));
        assertFalse(Lexicon.isWord("near=2"));
        assertFalse(Lexicon.isWord("id==air"));
        assertFalse(Lexicon.isWord("hp<max_hp"));
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
        for (Position position : new Position[] {Position.relative(3, -1, 2), Position.absolute(104.5, 64, -33.25),
            Position.absolute(-0.0, 0.004, 1e8)}) {
            Position parsed = Position.parse(position.toString());
            assertEquals(Math.round(position.x() * 100), Math.round(parsed.x() * 100), position.toString());
            assertEquals(Math.round(position.z() * 100), Math.round(parsed.z() * 100), position.toString());
        }
        assertEquals("~3,-1,2", Position.relative(3, -1, 2).toString());
        assertEquals(new Duration(40), Duration.parse(new Duration(40).toString()));
        assertEquals(new EntityRef(7), EntityRef.parse(new EntityRef(7).toString()));
    }

    @Test
    void valuesThatCantBePrintedAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Duration(-5));
        assertThrows(IllegalArgumentException.class, () -> new EntityRef(-1));
        assertThrows(IllegalArgumentException.class, () -> Duration.seconds(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Duration.seconds(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> Duration.seconds(-1));
        assertThrows(IllegalArgumentException.class, () -> Duration.seconds(2e8));
        assertEquals(new Duration(600), Duration.seconds(30));
    }
}
