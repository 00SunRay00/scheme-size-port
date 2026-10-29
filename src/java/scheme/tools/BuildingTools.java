package scheme.tools;

import arc.Events;
import arc.func.Boolf2;
import arc.func.Cons;
import arc.func.Cons2;
import arc.func.Cons4;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.struct.IntQueue;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.Seq;
import arc.util.Strings;
import mindustry.content.Items;
import mindustry.entities.Units;
import mindustry.entities.units.BuildPlan;
import mindustry.game.EventType.*;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.input.InputHandler;
import mindustry.input.Placement;
import mindustry.input.Placement.NormalizeResult;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.environment.Prop;
import mindustry.world.blocks.environment.StaticWall;
import mindustry.world.modules.ItemModule;
import scheme.Main;

import static arc.Core.*;
import static mindustry.Vars.*;
import static scheme.SchemeVars.*;

public class BuildingTools {

    public InputHandler input;
    public Seq<Mode> useful;

    /** Cap for a single replace flood fill on floor, overlay or environment blocks. */
    public static final int maxFillCells = 16384;

    public Mode mode = Mode.none;
    public int size = 8;

    public Seq<BuildPlan> plan = new Seq<>();
    public Seq<BuildPlan> removed = new Seq<>();

    public Cons<Building> iterator;
    public Block iterated;
    public int ibsize;
    public TileLayer tileLayer;

    public boolean schemesAllowed;

    public BuildingTools() {
        this.input = m_input.asHandler();
        this.useful = Seq.with(Mode.drop, Mode.replace, Mode.remove, Mode.connect);

        Events.on(WorldLoadEvent.class, event -> {
            schemesAllowed =  state.rules.schematicsAllowed;
            if (settings.getBool("hardscheme") && !net.client()) state.rules.schematicsAllowed = true;
        });
    }

    public void drop(int x, int y) {
        if (!MessageQueue.drop()) return;

        Tile tile = world.tile(x, y);
        if (tile == null || tile.build == null) return;

        ItemModule items = tile.build.items;
        if (items == null || items.empty()) return;
        else if (items.has(Items.sand)) drop(tile, Items.sand);
        else if (items.has(Items.coal)) drop(tile, Items.coal);
        else drop(tile, items.first());
    }

    private void drop(Tile tile, Item item) {
        if (player == null || player.unit() == null) return;
        Call.requestItem(player, tile.build, item, units.maxAccepted);
        if (player.unit().stack.amount > 0) Call.dropItem(units.maxAccepted);
    }

    public void replace(int x, int y) {
        if (block() == null) return;

        Tile tile = world.tile(x, y);
        if (tile == null) return;

        if (block().isFloor() && !block().isOverlay()) {
            iterated = tile.floor();
            if (iterated == null || block() == iterated) return;
            tileLayer = TileLayer.floor;
            fillTile(tile);
            return;
        }

        if (block().isOverlay()) {
            iterated = tile.overlay();
            if (iterated == null || block() == iterated) return;
            tileLayer = TileLayer.overlay;
            fillTile(tile);
            return;
        }

        if (block() instanceof Prop || block() instanceof StaticWall || block() == mindustry.content.Blocks.removeWall) {
            iterated = tile.block();
            if (!(iterated instanceof Prop) && !(iterated instanceof StaticWall)) return;
            if (block() == iterated) return;
            tileLayer = TileLayer.block;
            fillTile(tile);
            return;
        }

        if (tile.build == null) return;
        iterator = build -> plan(build.tileX(), build.tileY(), build.rotation);
        iterated = tile.block();
        ibsize = iterated.size;

        try {
            if (block().size == iterated.size && block() != iterated) iterate(tile);
        } catch (Throwable e) { Main.error(e); }
    }

    public void remove(int x, int y) {
        Tile tile = world.tile(x, y);
        if (tile == null || tile.build == null) return;

        iterator = build -> plan(build.tileX(), build.tileY());
        iterated = tile.block();
        ibsize = iterated.size;

        try { iterate(tile); } catch (Throwable e) { Main.error(e); }
    }

    private void iterate(Tile tile){
        if (tile.block() != iterated) return;

        int bx = tile.build.tileX(), by = tile.build.tileY();
        if (plan.contains(plan -> plan.x == bx && plan.y == by)) return;
        iterator.get(tile.build);
        
        for (int x = bx - ibsize + 1; x <= bx + ibsize - 1; x += ibsize) iterate(world.tile(x, by + ibsize));
        for (int y = by + ibsize - 1; y >= by - ibsize + 1; y -= ibsize) iterate(world.tile(bx + ibsize, y));
        for (int x = bx + ibsize - 1; x >= bx - ibsize + 1; x -= ibsize) iterate(world.tile(x, by - ibsize));
        for (int y = by - ibsize + 1; y <= by + ibsize - 1; y += ibsize) iterate(world.tile(bx - ibsize, y));
    } // bruhness is everywhere bruhness is everywhere bruhness is everywhere bruhness is everywhere bruhness

    private Block tileBlockAt(Tile tile) {
        if (tileLayer == TileLayer.floor) return tile.floor();
        if (tileLayer == TileLayer.overlay) return tile.overlay();
        return tile.block();
    }

    private void fillTile(Tile tile) {
        // air is the empty overlay/block layer: filling it would cover the whole map
        if (iterated == mindustry.content.Blocks.air) return;
        // replace() runs every frame while held; the region is already planned
        if (plan.contains(p -> p.x == tile.x && p.y == tile.y)) return;

        IntSeq cells = floodFill(tile.x, tile.y, world.width(), world.height(), maxFillCells,
                (x, y) -> tileBlockAt(world.tile(x, y)) == iterated);
        for (int i = 0; i < cells.size; i++) plan(Point2.x(cells.items[i]), Point2.y(cells.items[i]), 0);
    }

    /** Packed positions (Point2.pack) of the 4-connected region around (x, y) whose cells pass the filter, at most limit cells. */
    public static IntSeq floodFill(int x, int y, int width, int height, int limit, Boolf2<Integer, Integer> filter) {
        IntSeq cells = new IntSeq();
        if (x < 0 || y < 0 || x >= width || y >= height || !filter.get(x, y)) return cells;

        IntSet visited = new IntSet();
        IntQueue queue = new IntQueue();
        int start = Point2.pack(x, y);
        visited.add(start);
        queue.addLast(start);

        while (!queue.isEmpty() && cells.size < limit) {
            int pos = queue.removeFirst();
            cells.add(pos);

            int cx = Point2.x(pos), cy = Point2.y(pos);
            for (Point2 d : Geometry.d4) {
                int nx = cx + d.x, ny = cy + d.y;
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                if (visited.add(Point2.pack(nx, ny)) && filter.get(nx, ny)) queue.addLast(Point2.pack(nx, ny));
            }
        }
        return cells;
    }

    public void connect(int x, int y, Cons2<Integer, Integer> callback) {
        if (block() == null) return;

        Building power = Units.closestBuilding(player.team(), x * tilesize, y * tilesize, 999999f, build -> build.power != null && ((build.tileX() < x - size || build.tileX() > x + size) || (build.tileY() < y - size || build.tileY() > y + size))); // search for a power build that is not in the zone

        if (power == null) return;
        int px = power.tileX(), py = power.tileY();
        callback.get(x > px ? px - 1 : px + 1, y > py ? py - 1 : py + 1); // magic
    }

    public void fill(int x1, int y1, int x2, int y2, int maxLength) {
        if (block() == null) return;

        NormalizeResult result = Placement.normalizeArea(x1, y1, x2, y2, 0, false, maxLength);
        for (int x = result.x; x <= result.x2; x += block().size)
            for (int y = result.y; y <= result.y2; y += block().size)
                plan(x, y, 0);
    }

    public void square(int x, int y, Cons4<Integer, Integer, Integer, Integer> callback) {
        if (block() == null) return;

        callback.get(x - size, y + size, x + size - 1, y + size);
        callback.get(x + size, y + size, x + size, y - size + 1);
        callback.get(x + size, y - size, x - size + 1, y - size);
        callback.get(x - size, y - size, x - size, y + size - 1);
    }

    public void circle(int x, int y){
        if (block() == null) return;

        ibsize = block().size;
        for (int dx = -size; dx <= size; dx += ibsize)
            for (int dy = -size; dy <= size; dy += ibsize)
                if (Mathf.within(dx, dy, size) && !Mathf.within(dx, dy, size - ibsize)) plan(x + dx, y + dy, 0);
    }

    public void save(int x1, int y1, int x2, int y2, int maxLength) {
        removed.clear();

        NormalizeResult result = Placement.normalizeArea(x1, y1, x2, y2, 0, false, maxLength);
        for (int x = result.x; x <= result.x2; x++)
            for (int y = result.y; y <= result.y2; y++) {
                Building build = world.build(x, y);
                if (build != null) plan(build);
        }
    }

    public boolean isPlacing() {
        return (!plan.isEmpty() || mode == Mode.connect) && mode != Mode.none && (input.isPlacing() || mode == Mode.remove);
    }

    public void resized() {
        size = Mathf.clamp(size, 1, 512);
        hudfrag.size.setText(String.valueOf(size));
    }

    public void resize(int amount) {
        size += amount;
        resized();
    }

    public void resize(float amount) {
        if (amount == 0) return;
        amount = (size / 16f) * amount;
        size += amount > 0 ? Mathf.clamp(amount, 1, 8) : Mathf.clamp(amount, -8, -1);
        resized();
    }

    public void resize(String amount) {
        if (amount.isEmpty()) return;
        size = Strings.parseInt(amount);
        resized();
    }

    public void setMode(Mode set) {
        if (set == Mode.none && useful.contains(mode)) return;
        mode = mode == set ? Mode.none : set;
    }

    private void plan(int x, int y) {
        plan.add(new BuildPlan(x, y));
    }

    private void plan(int x, int y, int rotation) {
        plan.add(new BuildPlan(x, y, rotation, block(), block().nextConfig()));
    }

    private void plan(Building build) {
        removed.add(new BuildPlan(build.tileX(), build.tileY(), build.rotation, build.block, build.config()));
    }

    private Block block() {
        return input.block;
    }

    public enum Mode {
        none, drop, replace, remove, connect, fill, square, circle, pick, edit, brush;
    }

    public enum TileLayer {
        floor, overlay, block;
    }
}
