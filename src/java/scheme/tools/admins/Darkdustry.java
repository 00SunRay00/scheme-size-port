package scheme.tools.admins;

import arc.math.geom.Position;
import arc.struct.Seq;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.world.Block;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.Prop;
import mindustry.world.blocks.environment.StaticWall;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import scheme.tools.MessageQueue;
import scheme.tools.RainbowTeam;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static arc.Core.*;
import static mindustry.Vars.*;
import static scheme.SchemeVars.*;

public class Darkdustry implements AdminsTools {

    public void manageRuleBool(boolean value, String name) {
        if (unusable()) return;
        send("setrule", name, Boolean.toString(value));
    }

    private int min(int a, int b) {
        return a == -1 ? b : b == -1 ? a : Math.min(a, b);
    }
    public void manageRuleStr(String value, String name) {
        if (unusable()) return;
        StringBuilder actualValue = new StringBuilder();
        int i = 0;
        while (i != value.length()) {
            int o = min(
                    min(value.indexOf("\"", i), value.indexOf("\\", i)),
                    min(value.indexOf("\r", i), value.indexOf("\n", i))
            );
            if (o == -1) {
                actualValue.append(value, i, value.length() - i);
                break;
            }
            if (i != o) {
                actualValue.append(value, i, o);
            }
            switch (value.charAt(o)) {
                case '\"': actualValue.append("\\\""); break;
                case '\\': actualValue.append("\\\\"); break;
                case '\n': actualValue.append("\\\n"); break;
                case '\r': actualValue.append("\\\r"); break;
                default: throw new IllegalStateException("Cannot escape symbol '"+value.charAt(o)+"'");
            }
            i = o + 1;
        }
        send("setrule", name, '"'+actualValue.toString()+'"');
    }

    public void manageUnit() {
        if (unusable()) return;
        unit.select(false, true, false, (target, team, unit, amount) -> {
            send("unit", unit.id, "#" + target.id);
            units.refresh();
        });
    }

    public void spawnUnits() {
        if (unusable()) return;
        unit.select(true, false, true, (target, team, unit, amount) -> {
            if (amount == 0f) {
                send("despawn");
                return;
            }

            send("spawn", unit.id, amount.intValue(), team.id);
            units.refresh();
        });
    }

    public void manageEffect() {
        if (unusable()) return;
        effect.select(true, true, false, (target, team, effect, amount) -> send("effect", effect.id, amount.intValue() / 60, "#" + target.id));
    }

    public void manageItem() {
        if (unusable()) return;
        item.select(true, false, true, (target, team, item, amount) -> send("give", item.id, amount.intValue(), team.id));
    }

    public void manageTeam() {
        if (unusable()) return;
        team.select((target, team) -> {
            if (team != null) {
                RainbowTeam.remove(target);
                send("team", team.id, "#" + target.id);
            } else
                RainbowTeam.add(target, t -> send("team", t.id, "#" + target.id));
        });
    }

    public void manageTeam(Team team, Player target) {
        if (unusable()) return;
        if (team != null) {
            RainbowTeam.remove(target);
            send("team", team.id, "#" + target.id);
        } else
            RainbowTeam.add(target, t -> send("team", t.id, "#" + target.id));
    }
    public void placeCore() {
        if (unusable()) return;
        if (player.buildOn() instanceof CoreBuild)
            sendPacket("fill", "null 0 null", player.tileX(), player.tileY(), 1, 1);
        else send("core");
    }

    public void despawn(Player target) {
        if (unusable()) return;
        send("despawn", "#" + target.id);
    }

    public void teleport(Position pos) {
        if (unusable()) return;
        send("tp", pos.getX() / tilesize, pos.getY() / tilesize);
    }

    public void fill(int sx, int sy, int ex, int ey) {
        if (unusable()) return;
        //fuck you ion and yes this is a joel reference
        tile.select((floor, block, overlay, building) -> {
            block = building == null ? block : building;
            sendPacket("schemesize.fill",
                    block == null ? "null" : ""+block.id,
                    0,
                    floor == null ? "null" : ""+floor.id,
                    overlay == null ? "null" : ""+overlay.id,
                    sx, sy, ex - sx, ey - sy);
        });
    }

    public void brush(int x, int y, int radius) {
        if (unusable()) return;
        //fuck you ion and yes this is a joel reference
        tile.select((floor, block, overlay, building) -> {
            block = building == null ? block : building;
            sendPacket("schemesize.brush",
                    block == null ? "null" : ""+block.id,
                    0,
                    floor == null ? "null" : ""+floor.id,
                    overlay == null ? "null" : ""+overlay.id,
                    radius, x, y);
        });
    }

    public void edit(Floor floorPlace, Block blockPlace, Floor overlayPlace, Block buildingPlace, int sx, int sy) {
        if (unusable()) return;
        //fuck you ion too
        final Block[] blockSet = {blockPlace};
        tile.select((floor, block, overlay, building) -> {
            blockSet[0] = buildingPlace == null ? blockSet[0] : buildingPlace;
            sendPacket("schemesize.fill",
                    blockSet[0] == null ? "null" : ""+ blockSet[0].id,
                    0,
                    floorPlace == null ? "null" : ""+floorPlace.id,
                    overlayPlace == null ? "null" : ""+overlayPlace.id,
                    sx, sy, 0, 0);
        });
    }

    public void flush(Seq<BuildPlan> plans) {
        if (unusable()) return;
        plans.each(plan -> {
            if (plan.block.isFloor() && !plan.block.isOverlay())
                edit(plan.block.asFloor(), null, null, null, plan.x, plan.y);
            else if (plan.block instanceof Prop || plan.block instanceof StaticWall)
                edit(null, plan.block, null, null, plan.x, plan.y);
            else if (plan.block.isOverlay())
                edit(null, null, plan.block.asFloor(), null, plan.x, plan.y);
            else if (plan.block instanceof Block)
                edit(null, null, null, plan.block, plan.x, plan.y);
        });
    }

    public boolean unusable() {
        boolean admin = !player.admin && !settings.getBool("adminsalways");
        if (!settings.getBool("adminsenabled")) {
            ui.showInfoFade(disabled);
            return true;
        } else if (admin) ui.showInfoFade("@admins.notanadmin");
        return admin; // darkness was here
    }

    private static void send(String command, Object... args) {
        StringBuilder message = new StringBuilder(netServer.clientCommands.getPrefix()).append(command);
        for (var arg : args) message.append(" ").append(arg);
        MessageQueue.send(message.toString());
    }

    private static void sendPacket(String command, Object... args) {
        StringBuilder message = new StringBuilder();
        for (var arg : args) message.append(arg).append(" ");
        Call.serverPacketReliable(command, message.toString());
    }

    private static String id(Block block) {
        return block == null ? "null" : String.valueOf(block.id);
    }

}
