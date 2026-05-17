package scheme;

import arc.util.Log;
import mindustry.game.Schematics;
import arc.Events;
import mindustry.game.EventType;
import mindustry.mod.Mod;
import mindustry.mod.Scripts;
import mindustry.ui.CoreItemsDisplay;
import scheme.input.SBinding;
import scheme.moded.ModedSchematics;
import scheme.tools.MessageQueue;
import scheme.tools.RainbowTeam;
import scheme.tools.UpdateContent;
import scheme.ui.MapResizeFix;

import static arc.Core.*;
import static mindustry.Vars.*;
import static scheme.SchemeVars.*;

public class Main extends Mod {

    private static String version;

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
        ServerIntegration.load();
        SchemeVars.load();
        SchemeUpdater.load();
        MapResizeFix.load();
        MessageQueue.load();
        RainbowTeam.load();
        SBinding.load();
        new com.xpdustry.claj.client.Main().init();

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
        Events.on(EventType.ClientLoadEvent.class, e -> control.setInput(m_input.asHandler()));
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

        UpdateContent.update();
    }

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
}
