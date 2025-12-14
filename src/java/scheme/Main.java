package scheme;

import arc.graphics.g2d.Draw;
import arc.util.Log;
import arc.util.Tmp;
import mindustry.content.Blocks;
import mindustry.game.Schematics;
import mindustry.gen.Building;
import mindustry.mod.Mod;
import mindustry.mod.Scripts;
import mindustry.type.Item;
import mindustry.ui.CoreItemsDisplay;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.Router;
import mindustry.world.blocks.logic.LogicDisplay;
import scheme.moded.ModedGlyphLayout;
import scheme.moded.ModedSchematics;
import scheme.tools.MessageQueue;
import scheme.tools.RainbowTeam;
import scheme.ui.MapResizeFix;

// Добавлены Claj импорты

import static arc.Core.*;
import static mindustry.Vars.*;
import static scheme.SchemeVars.*;

public class Main extends Mod {

    private static String version; // добавлено из Claj Main

    public Main() {
        // well, after the 136th build, it became much easier
        maxSchematicSize = 512;

        // mod reimported through mods dialog
        if (schematics.getClass().getSimpleName().startsWith("Moded")) return;

        assets.load(schematics = m_schematics = new ModedSchematics());
        assets.unload(Schematics.class.getSimpleName()); // prevent dual loading
    }

    @Override
    public void init() {
        // Инициализация Scheme
        ServerIntegration.load();
        ModedGlyphLayout.load();
        SchemeVars.load();
        SchemeUpdater.load();
        MapResizeFix.load();
        MessageQueue.load();
        RainbowTeam.load();

        ui.schematics = schemas;
        ui.listfrag = listfrag;

        units.load();
        builds.load();

        m_settings.apply();

        hudfrag.build(ui.hudGroup);
        listfrag.build(ui.hudGroup);
        shortfrag.build(ui.hudGroup);
        corefrag.build(ui.hudGroup);

        control.setInput(m_input.asHandler());
        renderer.addEnvRenderer(0, render::draw);

        if (m_schematics.requiresDialog) ui.showOkText("@rename.name", "@rename.text", () -> {});
        if (settings.getBool("welcome")) ui.showOkText("@welcome.name", "@welcome.text", () -> {});
        if (settings.getBool("check4update")) SchemeUpdater.check();

        if (SchemeUpdater.installed("miner-tools")) {
            ui.showOkText("@incompatible.name", "@incompatible.text", () -> {});
            ui.hudGroup.fill(cont -> {
                cont.visible = false;
                cont.add(new CoreItemsDisplay());
            });
        }

        try {
            Scripts scripts = mods.getScripts();
            scripts.context.evaluateReader(scripts.scope, SchemeUpdater.script().reader(), "main.js", 0);
            log("Added constant variables to developer console.");
        } catch (Throwable e) {
            error(e);
        }

        Blocks.distributor.buildType = () -> ((Router) Blocks.distributor).new RouterBuild() {
            @Override
            public boolean canControl() { return true; }

            @Override
            public Building getTileTarget(Item item, Tile from, boolean set) {
                Building target = super.getTileTarget(item, from, set);

                if (unit != null && isControlled() && unit.isShooting()) {
                    float angle = angleTo(unit.aimX(), unit.aimY());
                    Tmp.v1.set(block.size * tilesize, 0f).rotate(angle).add(this);

                    Building other = world.buildWorld(Tmp.v1.x, Tmp.v1.y);
                    if (other != null && other.acceptItem(this, item)) target = other;
                }

                return target;
            }
        };

        content.blocks().each(block -> block instanceof LogicDisplay, block -> block.buildType = () -> ((LogicDisplay) block).new LogicDisplayBuild() {
            @Override
            public void draw() {
                super.draw();
                if (render.borderless) Draw.draw(Draw.z(), () -> {
                    Draw.rect(Draw.wrap(buffer.getTexture()), x, y, block.region.width * Draw.scl, -block.region.height * Draw.scl);
                });
            }
        });

        // === ИНИЦИАЛИЗАЦИЯ Claj ===
        try {
            new JoinViaClajDialog();
            new CreateClajRoomDialog();
            log("Claj dialogs initialized.");
        } catch (Throwable e) {
            error(new RuntimeException("Failed to initialize Claj dialogs", e));
        }
    }

    // ==== Методы Scheme ====
    public static void log(String info) {
        app.post(() -> Log.infoTag("Scheme", info));
    }

    public static void error(Throwable info) {
        app.post(() -> Log.err("Scheme", info));
    }

    public static void copy(String text) {
        if (text == null) return;
        app.setClipboardText(text);
        ui.showInfoFade("@copied");
    }
    /** @return the mod version, using this class, or {@code null} if mod is not loaded yet. */
    public static String getVersion() {return "2.3.3";}
}
