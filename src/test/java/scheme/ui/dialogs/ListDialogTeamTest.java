package scheme.ui.dialogs;

import org.junit.Test;

import static org.junit.Assert.*;

public class ListDialogTeamTest {

    @Test
    public void parsesTeamIdsInRange() {
        assertEquals(0, ListDialog.parseTeam("0").id);
        assertEquals(42, ListDialog.parseTeam("42").id);
        assertEquals(255, ListDialog.parseTeam("255").id);
        assertEquals(7, ListDialog.parseTeam("007").id);
    }

    @Test
    public void rejectsOutOfRangeAndGarbage() {
        assertNull(ListDialog.parseTeam("256"));
        assertNull(ListDialog.parseTeam("1000"));
        assertNull(ListDialog.parseTeam("99999999999999999999")); // would overflow an int parse
        assertNull(ListDialog.parseTeam("-1"));
        assertNull(ListDialog.parseTeam("4a"));
        assertNull(ListDialog.parseTeam(""));
        assertNull(ListDialog.parseTeam(null));
    }
}
