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

import static arc.Core.*;
import static mindustry.Vars.*;
import static scheme.SchemeVars.*;

public class Mindurka implements AdminsTools {

    public String keyName() { return "darkdustry"; }

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

    public void manageTeamRuleBool(int teamId, boolean value, String name) {
    }

    public void manageTeamRuleStr(int teamId, String value, String name) {
    }

    public void manageRuleObjectSet(String fieldName, arc.struct.ObjectSet<?> value) {
    }

    public void manageUnit() {
        if (unusable()) return;
        unit.select(false, true, false, (target, team, unit, amount) -> {
            send("unit", unit.name, "#" + target.id);
            units.refresh();
        });
    }

    public void spawnUnits() {
        if (unusable()) return;
        unit.select(true, false, true, (target, team, unit, amount) -> {
            if (amount == 0f) {
                if(unit!=null) send("despawntype",unit.name);
                else send("depsawn");
                return;
            }

            send("spawn", unit.name, amount.intValue(), team.id);
            units.refresh();
        });
    }

    public void manageEffect() {
        if (unusable()) return;
        effect.select(true, true, false, (target, team, effect, amount) -> {
            if (target.unit() != null) send("effect", effect.name, amount.intValue() / 60, target.unit().id);
            else send("effect", effect.name, amount.intValue() / 60);
        });
    }

    public void manageItem() {
        if (unusable()) return;
        item.select(true, false, true, (target, team, item, amount) -> send("give", item.name, amount.intValue(), team.id));
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
        if (target.unit() != null) send("despawn",target.team().id, "#" + target.unit().id);
        else if(target==null) send("despawn");
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
        //fuck you too, ion
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

        var groups = new java.util.LinkedHashMap<String, Seq<int[]>>();
        for (int i = 0; i < plans.size; i++) {
            BuildPlan plan = plans.get(i);
            String blockId, floorId, overlayId;
            if (plan.block.isFloor() && !plan.block.isOverlay()) {
                blockId = "null"; floorId = id(plan.block); overlayId = "null";
            } else if (plan.block instanceof Prop || plan.block instanceof StaticWall) {
                blockId = id(plan.block); floorId = "null"; overlayId = "null";
            } else if (plan.block.isOverlay()) {
                blockId = "null"; floorId = "null"; overlayId = id(plan.block);
            } else {
                blockId = id(plan.block); floorId = "null"; overlayId = "null";
            }
            String key = blockId + " " + floorId + " " + overlayId;
            groups.computeIfAbsent(key, k -> new Seq<>()).add(new int[]{plan.x, plan.y});
        }

        for (var entry : groups.entrySet()) {
            String[] params = entry.getKey().split(" ");
            Seq<int[]> points = entry.getValue();

            points.sort((a, b) -> a[1] != b[1] ? a[1] - b[1] : a[0] - b[0]);

            Seq<int[]> segs = new Seq<>();
            int i = 0;
            while (i < points.size) {
                int sx = points.get(i)[0], sy = points.get(i)[1], ex = sx;
                while (i + 1 < points.size && points.get(i + 1)[1] == sy && points.get(i + 1)[0] == ex + 1) {
                    ex = points.get(++i)[0];
                }
                segs.add(new int[]{sx, sy, ex - sx, 0});
                i++;
            }

            for (int j = 0; j < segs.size; j++) {
                int[] s = segs.get(j);
                for (int k = j + 1; k < segs.size; ) {
                    int[] n = segs.get(k);
                    if (n[0] == s[0] && n[2] == s[2] && n[1] == s[1] + s[3] + 1) {
                        s[3] = n[1] - s[1];
                        segs.remove(k);
                    } else k++;
                }
            }

            for (int j = 0; j < segs.size; j++) {
                int[] s = segs.get(j);
                sendPacket("schemesize.fill", params[0], 0, params[1], params[2], s[0], s[1], s[2], s[3]);
            }
        }
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
