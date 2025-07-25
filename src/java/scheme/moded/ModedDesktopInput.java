package scheme.moded;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Scl;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Blocks;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.input.*;
import mindustry.input.Placement.NormalizeDrawResult;
import mindustry.input.Placement.NormalizeResult;
import mindustry.world.blocks.power.PowerNode;
import scheme.ai.GammaAI;
import scheme.tools.BuildingTools.Mode;

import static arc.Core.*;
import static mindustry.Vars.*;
import static mindustry.input.PlaceMode.*;
import static scheme.SchemeVars.*;

/** Last update - Apr 18, 2023 */
public class ModedDesktopInput extends DesktopInput implements ModedInputHandler {

    public boolean using, movementLocked;
    public int buildX, buildY, lastX, lastY, lastSize = 8;

    public Vec2 lastCamera = new Vec2();
    public Player observed;

    @Override
    protected void removeSelection(int x1, int y1, int x2, int y2, int maxLength) {
        build.save(x1, y1, x2, y2, maxSchematicSize);
        super.removeSelection(x1, y1, x2, y2, maxSchematicSize);
    }

    @Override
    protected void flushPlans(Seq<BuildPlan> plans) {
        if (m_schematics.isCursed(plans)) admins.flush(plans);
        else super.flushPlans(plans);
    }

    @Override
    public void drawTop() {
        Lines.stroke(1f);
        int cursorX = tileX();
        int cursorY = tileY();

        if (mode == breaking) {
            drawBreakSelection(selectX, selectY, cursorX, cursorY, maxSchematicSize);
            drawSize(selectX, selectY, cursorX, cursorY, maxSchematicSize);
        } else if (input.keyDown(Binding.schematicSelect) && !scene.hasKeyboard()) {
            drawSelection(schemX, schemY, cursorX, cursorY, maxSchematicSize);
            drawSize(schemX, schemY, cursorX, cursorY, maxSchematicSize);
        } else if (input.keyDown(Binding.rebuildSelect) && !scene.hasKeyboard()) {
            drawSelection(schemX, schemY, cursorX, cursorY, 0, Pal.sapBulletBack, Pal.sapBullet, false);

            NormalizeDrawResult result = Placement.normalizeDrawArea(Blocks.air, schemX, schemY, cursorX, cursorY, false, 0, 1f);
            Tmp.r1.set(result.x, result.y, result.x2 - result.x, result.y2 - result.y);

            for (var plan : player.team().data().plans) {
                var block = plan.block;
                if (block.bounds(plan.x, plan.y, Tmp.r2).overlaps(Tmp.r1))
                    drawSelected(plan.x, plan.y, plan.block, Pal.sapBullet);
            }
        }

        if (using) {
            if (build.mode == Mode.edit)
                drawEditSelection(buildX, buildY, cursorX, cursorY, maxSchematicSize);

            if (build.mode == Mode.connect && isPlacing())
                drawEditSelection(cursorX - build.size, cursorY - build.size, cursorX + build.size, cursorY + build.size, maxSchematicSize);
        }

        if (build.mode == Mode.brush)
            drawEditSelection(cursorX, cursorY, build.size);

        drawCommanded();

        Draw.reset();
    }

    @Override
    public void drawBottom() {
        if (!build.isPlacing()) super.drawBottom();
        else build.plan.each(plan -> {
            plan.animScale = 1f;
            if (build.mode != Mode.remove) drawPlan(plan);
            else drawBreaking(plan);
        });
        if (ai.ai instanceof GammaAI gamma) gamma.draw();
    }

    @Override
    public void update() {
        lastCamera.set(camera.position);
        super.update(); // prevent unit clear, is it a crutch?

        if (locked()) return;

        if (observed != null) {
            camera.position.set(observed.unit()); // idk why, but unit moves smoother
            panning = true;

            // stop viewing a player if movement key is pressed
            if ((input.axis(Binding.moveX) != 0 || input.axis(Binding.moveY) != 0 || input.keyDown(Binding.pan)) && !scene.hasKeyboard()) observed = null;
        }

        if (movementLocked && !scene.hasKeyboard() && observed == null) {
            drawLocked(player.unit().x, player.unit().y);
            panning = true; // panning is always enabled when unit movement is locked

            float speed = (input.keyDown(Binding.boost) ? panBoostSpeed : panSpeed) * Time.delta;

            movement.set(input.axis(Binding.moveX), input.axis(Binding.moveY)).nor().scl(speed);
            camera.position.set(lastCamera).add(movement);

            if (input.keyDown(Binding.pan)) {
                camera.position.x += Mathf.clamp((input.mouseX() - graphics.getWidth() / 2f) * panScale, -1, 1) * speed;
                camera.position.y += Mathf.clamp((input.mouseY() - graphics.getHeight() / 2f) * panScale, -1, 1) * speed;
            }
        }

        if (scene.hasField()) {
            if (ai.ai != null && !player.dead() && !state.isPaused()) ai.update();
            return; // update the AI even if the player is typing a message
        }

        if (scene.hasKeyboard()) return;

        modedInput();
        buildInput();
    }

    @Override
    protected void updateMovement(Unit unit) {
        if (ai.ai != null
                && input.axis(Binding.moveX) == 0 && input.axis(Binding.moveY) == 0
                && !input.keyDown(Binding.mouseMove) && !input.keyDown(Binding.select))
            ai.update();
        else if (!movementLocked) super.updateMovement(unit);
    }

    public void buildInput() {
        if (!hudfrag.building.fliped) build.setMode(Mode.none);
        if (build.mode == Mode.none) return;

        int cursorX = tileX();
        int cursorY = tileY();

        boolean has = hasMoved(cursorX, cursorY);
        if (has) build.plan.clear();

        if (using) {
            if (build.mode == Mode.drop) build.drop(cursorX, cursorY);
            if (has) {
                if (build.mode == Mode.replace) build.replace(cursorX, cursorY);
                if (build.mode == Mode.remove) build.remove(cursorX, cursorY);
                if (build.mode == Mode.connect) {
                    if (block instanceof PowerNode == false) block = Blocks.powerNode;
                    build.connect(cursorX, cursorY, (x, y) -> {
                        updateLine(x, y);
                        build.plan.addAll(linePlans).remove(0);
                    });
                }

                if (build.mode == Mode.fill) build.fill(buildX, buildY, cursorX, cursorY, maxSchematicSize);
                if (build.mode == Mode.circle) build.circle(cursorX, cursorY);
                if (build.mode == Mode.square) build.square(cursorX, cursorY, (x1, y1, x2, y2) -> {
                    updateLine(x1, y1, x2, y2);
                    build.plan.addAll(linePlans);
                });

                if (build.mode == Mode.brush) admins.brush(cursorX, cursorY, build.size);

                lastX = cursorX;
                lastY = cursorY;
                lastSize = build.size;
                linePlans.clear();
            }

            if (input.keyRelease(Binding.select)) {
                flushBuildingTools();

                if (build.mode == Mode.pick) tile.select(cursorX, cursorY);
                if (build.mode == Mode.edit) {
                    NormalizeResult result = Placement.normalizeArea(buildX, buildY, cursorX, cursorY, 0, false, maxSchematicSize);
                    admins.fill(result.x, result.y, result.x2, result.y2);
                }
            } else build.resize(input.axis(Binding.zoom));
        }

        if (input.keyTap(Binding.select) && !scene.hasMouse()) {
            buildX = cursorX;
            buildY = cursorY;
            using = true;

            var scl = renderer.getScale() == Scl.scl(renderer.minZoom) ? renderer.getScale() : Mathf.round(renderer.getScale(), 0.5f);
            renderer.minZoom = renderer.maxZoom = scl / Scl.scl(); // a crutch to lock camera zoom
        }

        if (input.keyRelease(Binding.select) || input.keyTap(Binding.deselect) || input.keyTap(Binding.breakBlock)) {
            using = false;
            build.plan.clear();
            m_settings.apply();
        }
    }

    public boolean hasMoved(int x, int y) {
        return lastX != x || lastY != y || lastSize != build.size;
    }

    public void changePanSpeed(float value) {
        panSpeed = 4.5f * value / 4f;
        panBoostSpeed = 15f * Mathf.sqrt(value / 4f + .1f);
    }

    public void lockMovement() {
        movementLocked = !movementLocked;
    }

    // there is nothing because, you know, it's desktop
    public void lockShooting() {}

    public void observe(Player target) {
        observed = target;
    }

    public void flush(Seq<BuildPlan> plans) {
        flushPlans(plans);
    }

    public InputHandler asHandler() {
        return this;
    }
}
