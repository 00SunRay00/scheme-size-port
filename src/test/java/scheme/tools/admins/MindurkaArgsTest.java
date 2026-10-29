package scheme.tools.admins;

import org.junit.Test;

import static org.junit.Assert.*;

public class MindurkaArgsTest {

    @Test
    public void quotesPlainText() {
        assertEquals("\"abc\"", Mindurka.Args.quote("abc"));
        assertEquals("\"\"", Mindurka.Args.quote(""));
    }

    @Test
    public void escapesQuoteAndBackslash() {
        assertEquals("\"a\\\"b\\\\c\"", Mindurka.Args.quote("a\"b\\c"));
    }

    @Test
    public void escapesLineBreaksAsLetters() {
        String quoted = Mindurka.Args.quote("a\nb\rc");

        assertEquals("\"a\\nb\\rc\"", quoted);
        assertFalse(quoted.contains("\n"));
        assertFalse(quoted.contains("\r"));
    }

    @Test
    public void keepsTheTailAfterAnEscape() {
        // the old code appended value[i, length - i) and lost or overran the tail
        assertEquals("\"ab\\\"cdefgh\"", Mindurka.Args.quote("ab\"cdefgh"));
        assertEquals("\"abcdef\\\"gh\"", Mindurka.Args.quote("abcdef\"gh"));
    }

    @Test
    public void numbersGoUnquoted() {
        assertEquals("1.5", Mindurka.Args.ruleValue("1.5"));
        assertEquals("-3", Mindurka.Args.ruleValue("-3"));
        assertEquals("1.0E10", Mindurka.Args.ruleValue("1.0E10"));
    }

    @Test
    public void banListIsACompactJsonArray() {
        assertEquals("[]", Mindurka.Args.jsonArray(java.util.Collections.emptyList()));
        assertEquals("[\"duo\",\"scatter\"]", Mindurka.Args.jsonArray(java.util.Arrays.asList("duo", "scatter")));
    }

    @Test
    public void textGoesQuoted() {
        assertEquals("\"abc\"", Mindurka.Args.ruleValue("abc"));
        assertEquals("\"1 2\"", Mindurka.Args.ruleValue("1 2"));
        assertEquals("\"\"", Mindurka.Args.ruleValue(""));
    }
}
