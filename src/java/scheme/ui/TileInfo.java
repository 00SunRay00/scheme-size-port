package scheme.ui;

import arc.func.Floatp;
import arc.graphics.Color;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Reflect;
import arc.util.Strings;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.ui.Bar;
import mindustry.ui.Displayable;

import static arc.Core.*;
import static mindustry.Vars.*;

public class TileInfo {

    private static float lastUnitHealth = -1f;
    private static float lastUnitShield = -1f;
    private static int lastUnitItemAmount = -1;
    private static Object lastUnitItem = null;

    public static void apply(Table infoTable) {
        if (infoTable == null || ui == null || ui.hudfrag == null || ui.hudfrag.blockfrag == null) return;

        try {
            Displayable displayable = ui.hudfrag.blockfrag.hovered();
            Unit targetUnit = displayable instanceof Unit u ? u : null;
            Building targetBuilding = displayable instanceof Building b ? b : null;

            if (targetBuilding == null && targetUnit == null && world != null && input != null) {
                targetBuilding = world.buildWorld(input.mouseWorldX(), input.mouseWorldY());
            }

            if (targetUnit != null) {
                float hp = targetUnit.health;
                float shield = targetUnit.shield;
                Object item = targetUnit.stack != null ? targetUnit.stack.item : null;
                int itemAmt = targetUnit.stack != null ? targetUnit.stack.amount : 0;

                if (hp != lastUnitHealth || shield != lastUnitShield || item != lastUnitItem || itemAmt != lastUnitItemAmount) {
                    lastUnitHealth = hp;
                    lastUnitShield = shield;
                    lastUnitItem = item;
                    lastUnitItemAmount = itemAmt;

                    // Force PlacementFragment to update lastDisplayState so infoTable rebuilds for unit structural changes
                    Reflect.set(ui.hudfrag.blockfrag, "lastDisplayState", null);
                }
            } else {
                lastUnitHealth = -1f;
                lastUnitShield = -1f;
                lastUnitItem = null;
                lastUnitItemAmount = -1;
            }

            final Unit unitRef = targetUnit;
            final Building buildRef = targetBuilding;

            Seq<Bar> bars = new Seq<>();
            findBars(infoTable, bars);

            for (int i = 0; i < bars.size; i++) {
                Bar bar = bars.get(i);
                Color blink = getBlinkColor(bar);

                if (isHealthBar(bar, blink) || (i == 0 && (unitRef != null || buildRef != null))) {
                    if (unitRef != null) {
                        Reflect.set(Bar.class, bar, "name", coreHealthHeader(unitRef.health, unitRef.maxHealth));
                        Reflect.set(Bar.class, bar, "fraction", (Floatp) unitRef::healthf);
                    } else if (buildRef != null) {
                        Reflect.set(Bar.class, bar, "name", coreHealthHeader(buildRef.health, buildRef.maxHealth));
                        Reflect.set(Bar.class, bar, "fraction", (Floatp) buildRef::healthf);
                    }
                } else if (isShieldBar(bar, blink) && unitRef != null) {
                    Reflect.set(Bar.class, bar, "name", coreShieldHeader(unitRef.shield, unitRef.maxHealth));
                    Reflect.set(Bar.class, bar, "fraction", (Floatp) () -> unitRef.maxHealth > 0f ? unitRef.shield / unitRef.maxHealth : 0f);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static String coreHealthHeader(float health, float maxHealth) {
        String title = bundle.get("bar.health", "Прочность");
        float cur = Math.max(0f, health);
        return title + " (" + Strings.fixed(cur, 1) + " / " + Strings.fixed(maxHealth, 1) + ")";
    }

    private static String coreShieldHeader(float shield, float maxHealth) {
        String title = bundle.get("bar.shield", "Прочность щита");
        float cur = Math.max(0f, shield);
        return title + " (" + Strings.fixed(cur, 1) + " / " + Strings.fixed(maxHealth, 1) + ")";
    }

    private static Color getBlinkColor(Bar bar) {
        try {
            return Reflect.get(Bar.class, bar, "blinkColor");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isHealthBar(Bar bar, Color blink) {
        if (bar.color != null && (bar.color == Pal.health || Pal.health.equals(bar.color))) return true;
        if (blink != null && (blink == Pal.health || Pal.health.equals(blink))) return true;
        return false;
    }

    private static boolean isShieldBar(Bar bar, Color blink) {
        if (bar.color != null && (bar.color == Pal.shield || Pal.shield.equals(bar.color))) return true;
        if (blink != null && (blink == Pal.shield || Pal.shield.equals(blink))) return true;
        return false;
    }

    private static void findBars(Group group, Seq<Bar> result) {
        for (Element e : group.getChildren()) {
            if (e instanceof Bar b) {
                result.add(b);
            } else if (e instanceof Group g) {
                findBars(g, result);
            }
        }
    }
}
