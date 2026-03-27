package scheme.moded;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.content.Blocks;
import mindustry.core.World;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Player;
import mindustry.graphics.Pal;
import mindustry.input.InputHandler;
import mindustry.input.Placement;
import mindustry.input.Placement.NormalizeDrawResult;
import mindustry.world.Tile;
import scheme.tools.BuildingTools.Mode;

import static arc.Core.*;
import static mindustry.Vars.*;
import static scheme.SchemeVars.*;

/** Last update - Jul 19, 2022 */
public interface ModedInputHandler {

    int sizeX = mobile ? 0 : -16;
    int sizeY = mobile ? 32 : -16;

    default void modedInput() {}

    default void buildInput() {}

    boolean hasMoved(int x, int y);

    void changePanSpeed(float value);

    void lockMovement();

    void lockShooting();

    void observe(Player target);

    void flush(Seq<BuildPlan> plans);

    default void flushLastRemoved() {
        flush(build.removed);
        build.removed.clear();
    }

    default void flushBuildingTools() {
        if (build.mode != Mode.remove) flush(build.plan);
        else build.plan.each(player.unit()::addBuild);
        build.plan.clear();
    }

    InputHandler asHandler();

    // methods that exist but, who knows why, not available
    default Tile tileAt() {
        return world.tiles.getc(tileX(), tileY());
    }

    default int tileX() {
        return World.toTile(input.mouseWorldX());
    }

    default int tileY() {
        return World.toTile(input.mouseWorldY());
    }

    // some drawing methods
    default void drawSize(int x1, int y1, int x2, int y2, int maxLength) {
        String x = getSize(Math.abs(x1 - x2), maxLength);
        String y = getSize(Math.abs(y1 - y2), maxLength);
        ui.showLabel(x + ", " + y, 61252, 0.02f, x2 * tilesize + sizeX, y2 * tilesize + sizeY);
    }

    default String getSize(int size, int maxLength) {
        return ++size >= maxLength ? "[accent]" + maxLength + "[]" : String.valueOf(size);
    }

    default void drawEditSelection(int x1, int y1, int x2, int y2, int maxLength){
        NormalizeDrawResult result = Placement.normalizeDrawArea(Blocks.air, x1, y1, x2, y2, false, maxLength, 1f);

        drawSize(x1, y1, x2, y2, maxLength);
        Lines.stroke(2f);

        Draw.color(Pal.darkerMetal);
        Lines.rect(result.x, result.y - 1, result.x2 - result.x, result.y2 - result.y);
        Draw.color(Pal.darkMetal);
        Lines.rect(result.x, result.y, result.x2 - result.x, result.y2 - result.y);
    }

    default void drawEditSelection(int x, int y, int radius) {
        Vec2[] polygons = Geometry.pixelCircle(radius, (index, cx, cy) -> Mathf.dst(cx, cy, index, index) < index);
        Lines.stroke(2f);

        Draw.color(Pal.darkerMetal);
        Lines.poly(polygons, x * tilesize - 4, y * tilesize - 5, tilesize);
        Draw.color(Pal.darkMetal);
        Lines.poly(polygons, x * tilesize - 4, y * tilesize - 4, tilesize);
    }

    default void drawLocked(float x, float y) {
        ui.showLabel(bundle.format(
                Mathf.absin(25f, 1f) < .5f ? "locked.info" : "locked.bind",
                Color.orange.cpy().lerp(Color.scarlet, Mathf.absin(3f, 1f))
                ),61252,0.02f,x,y);
    }
}
