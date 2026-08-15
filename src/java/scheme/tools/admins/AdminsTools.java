package scheme.tools.admins;

import arc.math.geom.Point2;
import arc.math.geom.Position;
import arc.scene.Group;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.type.Item;
import mindustry.type.UnitType;
import scheme.tools.DisabledTools;
import scheme.tools.PositionBuild;

import java.awt.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public interface AdminsTools {

    AdminsTools[] implementations = { new Internal(), new SlashJs(), new Mindurka() };

    String disabled = bundle.format("admins.notenabled");
    String unavailable = bundle.format("admins.notavailable");
    String restricted = bundle.format("admins.restricted");

    default boolean isRestricted(int flag) {
        if (DisabledTools.disabled(flag)) {
            ui.showInfoFade(restricted);
            return true;
        }
        return false;
    }

    String keyName();

    void manageRuleBool(boolean value, String name);

    void manageRuleStr(String value, String name);

    void manageTeamRuleBool(int teamId, boolean value, String name);

    void manageTeamRuleStr(int teamId, String value, String name);

    void manageRuleObjectSet(String fieldName, arc.struct.ObjectSet<?> value);

    void manageUnit();

    void spawnUnits();

    void manageEffect();

    void manageItem();

    void manageTeam();

    void manageTeam(Team derelict, Player player);

    void placeCore();

    void despawn(Player target);

    default void despawn() {
        despawn(player);
    }

    void teleport(Position pos);

    default void teleport(boolean isButton){teleport(getTeleportPosition(isButton));}

    default Position getTeleportPosition(boolean isButton) {
        if (mobile || isButton) return PositionBuild.GetPosition(camera.position.x,camera.position.y);
        else return PositionBuild.GetPosition( player.mouseX, player.mouseY);
    }

    default void teleport() {
        teleport(getTeleportPosition(false));
    }

    default void deletePlyaer(){
        Position mousePostion = PositionBuild.GetPosition(player.mouseX(),player.mouseY);
        Groups.player.each(player -> {
            float distance = PositionBuild.GetPosition(player.x,player.y).dst(mousePostion);
            if(distance>3*tilesize || player.equals(Vars.player)) return;
            manageTeam(Team.derelict,player);
            despawn(player);
        });
    }

    default void look() {
        for (int i = 0; i < 10; i++) player.unit().lookAt(input.mouseWorld());
    }

    void fill(int sx, int sy, int ex, int ey);

    void brush(int x, int y, int radius);

    void flush(Seq<BuildPlan> plans);

    boolean unusable();

    default int fixAmount(Item item, float amount) {
        int items = player.core().items.get(item);
        return amount == 0f || items + amount < 0 ? -items : (int) amount;
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
