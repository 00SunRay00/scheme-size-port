package scheme.moded;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.ui.layout.Scl;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.content.Blocks;
import mindustry.core.World;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Schematic;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.input.*;
import mindustry.input.Placement.NormalizeDrawResult;
import mindustry.input.Placement.NormalizeResult;
import mindustry.gen.Building;
import mindustry.gen.Mechc;
import scheme.tools.DisabledTools;
import mindustry.world.Block;
import mindustry.input.InputHandler.*;
import mindustry.world.blocks.power.PowerNode;
import mi2u.input.InputOverwrite;
import scheme.ai.GammaAI;
import scheme.input.SBinding;
import scheme.tools.BuildingTools.Mode;

import static arc.Core.*;
import static mindustry.Vars.*;
import static mindustry.input.PlaceMode.*;
import static scheme.SchemeVars.*;

public class ModedDesktopInput extends DesktopInput implements ModedInputHandler, InputOverwrite {

    public boolean using, movementLocked;
    public int buildX, buildY, lastX, lastY, lastSize = 8;

    public Vec2 lastCamera = new Vec2();
    public Player observed;

    public boolean ctrlBoost, mi2uBoost;
    public boolean ctrlShoot, mi2uShoot;
    public Vec2 shootXY = new Vec2();
    public boolean ctrlMove;
    public Vec2 mi2uMove = new Vec2();
    public Building forceTapped = null;

    @Override
    protected void removeSelection(int x1, int y1, int x2, int y2, int maxLength) {
        build.save(x1, y1, x2, y2, maxSchematicSize);
        super.removeSelection(x1, y1, x2, y2, maxSchematicSize);
    }

    @Override
    protected void flushPlans(Seq<BuildPlan> plans) {
        if (m_schematics.isCursed(plans) && !admins.isRestricted(DisabledTools.FLUSH)) admins.flush(plans);
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
        } else if (input.keyDown(Binding.schematicSelect) && schemX != -1 && !scene.hasKeyboard()) {
            drawSelection(schemX, schemY, cursorX, cursorY, maxSchematicSize);
            drawSize(schemX, schemY, cursorX, cursorY, maxSchematicSize);
        } else if (input.keyDown(Binding.rebuildSelect) && !scene.hasKeyboard()) {
            drawSelection(schemX, schemY, cursorX, cursorY, 0, Pal.sapBulletBack, Pal.sapBullet, false);

            NormalizeDrawResult result = Placement.normalizeDrawArea(Blocks.air, schemX, schemY, cursorX, cursorY, false, 0, 1f);
            Tmp.r1.set(result.x, result.y, result.x2 - result.x, result.y2 - result.y);

            for (mindustry.game.Teams.BlockPlan plan : player.team().data().plans) {
                Block block = plan.block;
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

        if (settings.getBool("forceTapTile") && block == null && !scene.hasMouse()) {
            Vec2 vec = input.mouseWorld(getMouseX(), getMouseY());
            Building build = world.buildWorld(vec.x, vec.y);
            if (build != null && build.team != player.team()) {
                build.drawSelect();
                if (!build.enabled && build.block.drawDisabled) build.drawDisabled();
            }
        }

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
        // alternative combos run before vanilla input, so vanilla skips the same key this frame
        if (!altTapInput()) super.update(); // prevent unit clear, is it a crutch?

        if (locked()) return;

        if (observed != null) {
            if (observed.unit() == null) return;
            camera.position.set(observed.unit()); // idk why, but unit moves smoother
            panning = true;

            // stop viewing a player if movement key is pressed
            if ((input.axis(Binding.moveX) != 0 || input.axis(Binding.moveY) != 0 || input.keyDown(Binding.pan)) && !scene.hasKeyboard()) observed = null;
        }

        if (movementLocked && !scene.hasKeyboard() && observed == null) {
            if (player.unit() == null) return;
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

        if (input.keyTap(Binding.select) && !scene.hasMouse() && corefrag.choosesNode) corefrag.trySetNode(tileX(getMouseX()), tileY(getMouseY()));

        if (settings.getBool("forceTapTile") && input.keyTap(Binding.select) && !scene.hasMouse()) {
            if (player.dead()) {
                var build = world.buildWorld(input.mouseWorldX(), input.mouseWorldY());
                forceTap(build, true);
            } else {
                forceTap(prevSelected == null ? null : prevSelected.build, false);
            }
        }

        modedInput();
        buildInput();

        Unit unit = player.unit();
        if (ctrlBoost) player.boosting = mi2uBoost;
        if (ctrlShoot && unit != null) {
            boolean boosted = unit instanceof Mechc && unit.isFlying();
            player.shooting = mi2uShoot && !boosted;
            if (player.shooting) {
                player.mouseX = shootXY.x;
                player.mouseY = shootXY.y;
                unit.aim(shootXY);
                unit.controlWeapons(true, player.shooting);
            }
        }
        if (ctrlMove && unit != null) unit.movePref(mi2uMove);
    }

    @Override
    protected void updateMovement(Unit unit) {
        if (ai.ai != null
                && input.axis(Binding.moveX) == 0 && input.axis(Binding.moveY) == 0
                && !input.keyDown(Binding.mouseMove) && !input.keyDown(Binding.select))
            ai.update();
        else if (!movementLocked) super.updateMovement(unit);
    }

    int tileX(float cursorX){
        Vec2 vec = Core.input.mouseWorld(cursorX, 0);
        if(selectedBlock()){
            vec.sub(block.offset, block.offset);
        }
        return World.toTile(vec.x);
    }

    int tileY(float cursorY){
        Vec2 vec = Core.input.mouseWorld(0, cursorY);
        if(selectedBlock()){
            vec.sub(block.offset, block.offset);
        }
        return World.toTile(vec.y);
    }

    @Override
    public void useSchematic(Schematic schem, boolean checkHidden){
        block = null;
        schematicX = tileX(getMouseX());
        schematicY = tileY(getMouseY());

        selectPlans.clear();
        selectPlans.addAll(m_schematics.toPlans(schem, schematicX, schematicY));
        mode = none;
    }

    public boolean altDown() {
        return state.isGame() && !locked() && !scene.hasKeyboard() && input.keyDown(SBinding.alternative);
    }

    /** Alternative combos on vanilla keys. Returns true if one fired. */
    public boolean altTapInput() {
        if (!altDown()) return false;

        if (input.keyTap(Binding.respawn)) {
            if (!admins.isRestricted(DisabledTools.DESPAWN)) admins.despawn();
        } else if (input.keyTap(Binding.mouseMove)) {
            if (!admins.isRestricted(DisabledTools.TELEPORT)) admins.teleport(input.mouseWorld().cpy());
        } else if (input.keyTap(Binding.pan)) lockMovement();
        else if (input.keyTap(Binding.schematicSelect)) corefrag.nextLayer();
        else if (input.keyTap(Binding.deselect)) hudfrag.building.flip();
        else if (input.keyTap(Binding.schematicMenu)) flushLastRemoved();
        else return false;

        return true;
    }

    public void buildInput() {
        boolean alt = altDown();
        if (alt) {
            if (!player.dead()) admins.look();
            if (input.keyDown(Binding.rotatePlaced)) build.drop(tileX(), tileY());
        }

        if(Core.input.keyTap(SBinding.aiBind)) {
            if (alt) ai.deselect();
            else ai.select();
        }
        if(Core.input.keyTap(SBinding.coreBind) && !admins.isRestricted(DisabledTools.CORE)) admins.placeCore();
        if(Core.input.keyTap(SBinding.despawnBind) && !admins.isRestricted(DisabledTools.DESPAWN)) admins.despawn();
        if(Core.input.keyTap(SBinding.effectBind) && !admins.isRestricted(DisabledTools.EFFECT)) admins.manageEffect();
        if(Core.input.keyTap(SBinding.itemBind) && !admins.isRestricted(DisabledTools.ITEM)) admins.manageItem();
        if(Core.input.keyTap(SBinding.teamBind) && !admins.isRestricted(DisabledTools.TEAM)) admins.manageTeam();
        if(Core.input.keyTap(SBinding.unitBind) && !admins.isRestricted(DisabledTools.SPAWN)) {
            if (alt) admins.spawnUnits();
            else admins.manageUnit();
        }
        if(Core.input.keyTap(SBinding.unitSpawnBind) && !admins.isRestricted(DisabledTools.SPAWN)) admins.spawnUnits();
        if(Core.input.keyTap(SBinding.teleportBind) && !admins.isRestricted(DisabledTools.TELEPORT)) admins.teleport();
        // deletePlayer moves players to derelict and despawns them
        if(Core.input.keyTap(SBinding.deletePLayer) && !admins.isRestricted(DisabledTools.TEAM | DisabledTools.DESPAWN)) admins.deletePlayer();
        if (!hudfrag.building.fliped) build.setMode(Mode.none);
        if (build.mode == Mode.none) return;

        int cursorX = tileX();
        int cursorY = tileY();

        boolean has = hasMoved(cursorX, cursorY);
        if (has) build.plan.clear();

        if (using) {
            if (build.mode == Mode.drop) build.drop(cursorX, cursorY);
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

            if (build.mode == Mode.brush && !admins.isRestricted(DisabledTools.BRUSH)) admins.brush(cursorX, cursorY, build.size);

            lastX = cursorX;
            lastY = cursorY;
            lastSize = build.size;
            linePlans.clear();

            if (input.keyRelease(Binding.select)) {
                flushBuildingTools();

                if (build.mode == Mode.pick) tile.select(cursorX, cursorY);
                if (build.mode == Mode.edit) {
                    NormalizeResult result = Placement.normalizeArea(buildX, buildY, cursorX, cursorY, 0, false, maxSchematicSize);
                    if (!admins.isRestricted(DisabledTools.FILL)) admins.fill(result.x, result.y, result.x2, result.y2);
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

    @Override
    public void boost(boolean boost) {
        ctrlBoost = true;
        mi2uBoost = boost;
    }

    @Override
    public void pan(boolean ctrl, float x, float y) {
        if (ctrl) {
            panning = true;
            camera.position.set(x, y);
        }
    }

    @Override
    public void shoot(Vec2 vec, boolean shoot, boolean ctrl) {
        ctrlShoot = ctrl;
        shootXY.set(vec);
        mi2uShoot = shoot;
    }

    @Override
    public void move(Vec2 movement) {
        ctrlMove = true;
        mi2uMove.set(movement);
    }

    private void forceTap(Building build, boolean includeSelfTeam) {
        if (build == null) return;
        if (!includeSelfTeam && build.interactable(player.team())) return;

        if (build == forceTapped) {
            inv.hide();
            config.hideConfig();
            forceTapped = null;
            return;
        }

        var ptm = state.playtestingMap;
        state.playtestingMap = state.map;

        if (build.block.configurable) {
            if ((!config.isShown() && build.shouldShowConfigure(player))
                    || (config.isShown() && config.getSelected().onConfigureBuildTapped(build))) {
                config.showConfig(build);
            }
        } else if (!config.hasConfigMouse()) {
            if (config.isShown() && config.getSelected().onConfigureBuildTapped(build)) {
                config.hideConfig();
            }
        }

        if (build.block.synthetic() && build.block.allowConfigInventory) {
            if (build.block.hasItems && build.items.total() > 0) {
                inv.showFor(build);
            }
        }

        forceTapped = build;
        state.playtestingMap = ptm;
    }

    @Override
    public void clear() {
        ctrlBoost = false;
        ctrlShoot = false;
        ctrlMove = false;
        mi2uMove.setZero();
        shootXY.setZero();
    }
}
