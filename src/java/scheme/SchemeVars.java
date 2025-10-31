package scheme;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.Texture;
import arc.graphics.Texture.TextureFilter;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.graphics.gl.FrameBuffer;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.content.StatusEffects;
import mindustry.core.UI;
import mindustry.game.EventType;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.StatusEffect;
import mindustry.type.UnitType;
import scheme.moded.*;
import scheme.tools.*;
import scheme.tools.admins.AdminsTools;
import scheme.ui.*;
import scheme.ui.dialogs.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class SchemeVars {

    public static ModedSchematics m_schematics;
    public static ModedInputHandler m_input;

    public static AdminsTools admins;
    public static RendererTools render;
    public static BuildingTools build;
    public static UnitsCache units;
    public static BuildsCache builds;

    public static AdminsConfigDialog adminscfg;
    public static RendererConfigDialog rendercfg;

    public static AISelectDialog ai;
    public static TeamSelectDialog team;
    public static TileSelectDialog tile;
    public static TagSelectDialog tag;

    public static ContentSelectDialog<UnitType> unit;
    public static ContentSelectDialog<StatusEffect> effect;
    public static ContentSelectDialog<Item> item;

    public static SettingsMenuDialog m_settings;
    public static SchemasDialog schemas;
    public static ImageParserDialog parser;
    public static WaveApproachingDialog approaching;

    public static HudFragment hudfrag;
    public static PlayerListFragment listfrag;
    public static ShortcutFragment shortfrag;
    public static CoreInfoFragment corefrag;

    public static void load() {
        Events.on(EventType.ClientLoadEvent.class, e -> {
            try {
                TextureRegion base = atlas.find("scheme-size-status-invincible");
                if (base == null) {
                    Log.err("Region scheme-size-status-invincible not found in atlas!");
                    return;
                }

                FrameBuffer fb = new FrameBuffer(base.width, base.height);
                fb.begin();

                Draw.proj().setOrtho(0, 0, base.width, base.height);
                Draw.reset();

                for (int dx = -3; dx <= 3; dx++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        if (Math.abs(dx) + Math.abs(dy) <= 3) {
                            Draw.color(Pal.gray);
                            Draw.rect(base, base.width / 2f + dx, base.height / 2f + dy, base.width, base.height);
                        }
                    }
                }

                Draw.color(Color.white);
                Draw.rect(base, base.width / 2f, base.height / 2f, base.width, base.height);
                Draw.flush();

                fb.end();

                Texture outlined = fb.getTexture();
                outlined.setFilter(Texture.TextureFilter.linear);

                atlas.addRegion("status-invincible-ui", outlined, 0, 0, base.width, base.height);

                StatusEffects.invincible.uiIcon = atlas.find("status-invincible-ui");

                fb.dispose();
            } catch (Throwable ex) {
                Log.err("Failed to create invincible outlined icon", ex);
            }
        });
        // m_schematics is created in Main to prevent dual loading
        m_input = mobile ? new ModedMobileInput() : new ModedDesktopInput();

        admins = AdminsConfigDialog.getTools();
        render = new RendererTools();
        build = new BuildingTools();
        units = new UnitsCache();
        builds = new BuildsCache();

        adminscfg = new AdminsConfigDialog();
        rendercfg = new RendererConfigDialog();

        ai = new AISelectDialog();
        team = new TeamSelectDialog();
        tile = new TileSelectDialog();
        tag = new TagSelectDialog();

        unit = new ContentSelectDialog<>("@select.unit", content.units(), 0, 100, 1, value -> {
            return value == 0 ? "@select.unit.clear" : bundle.format("select.units", value);
        });
        effect = new ContentSelectDialog<>("@select.effect", content.statusEffects(), 0, 500 * 3600, 60, value -> {
            return value == 0 ? "@select.effect.clear" : bundle.format("select.seconds", value / 60f);
        });
        item = new ContentSelectDialog<>("@select.item", content.items(), -1000000, 1000000, 500, value -> {
            return value == 0 ? "@select.item.clear" : bundle.format("select.items", UI.formatAmount(value.longValue()));
        });

        m_settings = new SettingsMenuDialog();
        schemas = new SchemasDialog();
        parser = new ImageParserDialog();
        approaching = new WaveApproachingDialog();

        hudfrag = new HudFragment();
        listfrag = new PlayerListFragment();
        shortfrag = new ShortcutFragment();
        corefrag = new CoreInfoFragment();
    }

}
