package scheme.tools;

import arc.math.geom.Point2;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import org.junit.Test;

import static org.junit.Assert.*;

public class BuildingToolsFloodFillTest {

    /** Rows top to bottom, '#' passes the filter; y grows downwards here, which does not matter for the fill. */
    private static IntSeq fill(String[] rows, int x, int y, int limit) {
        int width = rows[0].length(), height = rows.length;
        return BuildingTools.floodFill(x, y, width, height, limit, (cx, cy) -> rows[cy].charAt(cx) == '#');
    }

    private static IntSet set(IntSeq cells) {
        IntSet set = new IntSet();
        for (int i = 0; i < cells.size; i++) assertTrue("duplicate cell", set.add(cells.items[i]));
        return set;
    }

    @Test
    public void fillsOnlyTheConnectedRegion() {
        String[] rows = {
                "##..#",
                "#..##",
                "###.#",
                "....#"};

        IntSet cells = set(fill(rows, 0, 0, 100));

        assertEquals(6, cells.size);
        for (int[] p : new int[][]{{0, 0}, {1, 0}, {0, 1}, {0, 2}, {1, 2}, {2, 2}})
            assertTrue(cells.contains(Point2.pack(p[0], p[1])));
        assertFalse("other region", cells.contains(Point2.pack(4, 0)));
    }

    @Test
    public void diagonalCellsAreNotConnected() {
        String[] rows = {
                "#.",
                ".#"};

        assertEquals(1, fill(rows, 0, 0, 100).size);
    }

    @Test
    public void startOutsideFilterOrGridGivesNothing() {
        String[] rows = {"#."};

        assertEquals(0, fill(rows, 1, 0, 100).size);
        assertEquals(0, fill(rows, -1, 0, 100).size);
        assertEquals(0, fill(rows, 2, 0, 100).size);
        assertEquals(0, fill(rows, 0, 1, 100).size);
    }

    @Test
    public void largeRegionDoesNotOverflowTheStack() {
        // the recursive fill this replaces threw StackOverflowError far below 10 000 cells
        int size = 100;
        IntSeq cells = BuildingTools.floodFill(0, 0, size, size, BuildingTools.maxFillCells, (x, y) -> true);

        assertEquals(size * size, set(cells).size);
    }

    @Test
    public void stopsAtTheLimit() {
        int size = 300; // 90 000 cells, like a whole empty overlay layer
        IntSeq cells = BuildingTools.floodFill(150, 150, size, size, BuildingTools.maxFillCells, (x, y) -> true);

        assertEquals(BuildingTools.maxFillCells, set(cells).size);
    }
}
