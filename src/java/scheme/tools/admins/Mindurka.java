package scheme.tools.admins;

import arc.math.geom.Position;
import arc.struct.Seq;
import mindustry.ctype.MappableContent;
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

import java.util.regex.Pattern;

import static arc.Core.*;
import static mindustry.Vars.*;
import static scheme.SchemeVars.*;

public class Mindurka implements AdminsTools {

    public String keyName() { return "darkdustry"; }

    public boolean manageRuleBool(boolean value, String name) {
        if (unusable()) return false;
        send("setrule", name, Boolean.toString(value));
        return true;
    }

    public boolean manageRuleStr(String value, String name) {
        if (unusable()) return false;
        send("setrule", name, Args.ruleValue(value));
        return true;
    }

    public boolean manageTeamRuleBool(int teamId, boolean value, String name) {
        ui.showInfoFade("@admins.notsupported");
        return false;
    }

    public boolean manageTeamRuleStr(int teamId, String value, String name) {
        ui.showInfoFade("@admins.notsupported");
        return false;
    }

    /** CorePlugin /setrule reads the JSON into the rule field: a ban list is an array of content names. */
    public boolean manageRuleObjectSet(String fieldName, arc.struct.ObjectSet<?> value) {
        if (unusable()) return false;

        Seq<String> names = new Seq<>();
        for (Object content : value) names.add(((MappableContent) content).name);
        String command = netServer.clientCommands.getPrefix() + "setrule " + fieldName + " " + Args.jsonArray(names);

        // chars (String.length), as NetClient.sendChatMessage compares the message with maxTextLength (NetClient.java:328)
        if (command.length() > maxTextLength) {
            ui.showInfoFade(bundle.format("scheme.rule.toolong", command.length(), maxTextLength));
            return false;
        }

        MessageQueue.send(command);
        return true;
    }

    /** Command argument formatting; a nested class so it loads without the game (AdminsTools needs a bundle). */
    public static final class Args {
        private static final Pattern number = Pattern.compile("-?\\d+(\\.\\d+)?([eE][-+]?\\d+)?");

        /** Plain numbers go as is, anything else as a quoted string. */
        public static String ruleValue(String value) {
            return number.matcher(value).matches() ? value : quote(value);
        }

        /** Double-quoted string with backslash, quote, \n and \r escaped. */
        public static String quote(String value) {
            StringBuilder out = new StringBuilder(value.length() + 2).append('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"': out.append("\\\""); break;
                    case '\\': out.append("\\\\"); break;
                    case '\n': out.append("\\n"); break;
                    case '\r': out.append("\\r"); break;
                    default: out.append(c);
                }
            }
            return out.append('"').toString();
        }

        /** JSON array of quoted strings, without spaces to save command length. */
        public static String jsonArray(Iterable<String> values) {
            StringBuilder out = new StringBuilder("[");
            for (String value : values) {
                if (out.length() > 1) out.append(',');
                out.append(quote(value));
            }
            return out.append(']').toString();
        }
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
                send("despawntype", unit.name);
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
        if (target == null) send("despawn");
        else if (target.unit() != null) send("despawn", target.team().id, "#" + target.unit().id);
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
