package scheme.tools.admins;

import arc.math.geom.Position;
import arc.struct.Seq;
import mindustry.entities.Units;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Team;
import mindustry.gen.Player;
import mindustry.type.Item;
import mindustry.type.UnitType;

import static arc.Core.*;
import static mindustry.Vars.*;

public interface AdminsTools {

    String disabled = bundle.format("admins.notenabled");
    String unabailable = bundle.format("admins.notavailable");

    void manageRuleBool(boolean value, String name);

    void manageRuleStr(String value, String name);

    void manageUnit();

    void spawnUnits();

    void manageEffect();

    void manageItem();

    void manageTeam();

    void placeCore();

    void despawn(Player target);

    default void despawn() {
        despawn(player);
    }

    void teleport(Position pos);

    default void teleport() {
        teleport(camera.position);
    }

    default void look() {
        for (int i = 0; i < 10; i++) player.unit().lookAt(input.mouseWorld());
    }

    void fill(int sx, int sy, int ex, int ey);

    void brush(int x, int y, int radius);

    void flush(Seq<BuildPlan> plans);

    boolean unusable();

    default int fixAmount(Item item, Float amount) {
        int items = player.core().items.get(item);
        return amount == 0f || items + amount < 0 ? -items : amount.intValue();
    }

    default boolean canCreate(Team team, UnitType type) {
        boolean can = Units.canCreate(team, type);
        if (!can) ui.showInfoFade("@admins.nounit");
        return can;
    }

    default boolean hasCore(Team team) {
        boolean has = team.core() != null;
        if (!has) ui.showInfoFade("@admins.nocore");
        return has;
    }
}
