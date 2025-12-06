package scheme.ui.dialogs;

import arc.func.*;
import arc.graphics.g2d.TextureRegion;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.content.Blocks;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.world.Build;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.blocks.environment.OverlayFloor;
import mindustry.world.blocks.environment.Prop;
import mindustry.world.blocks.power.Battery;
import scheme.ui.List;

import static arc.Core.*;
import static mindustry.Vars.*;
import static scheme.SchemeVars.*;

import com.github.bsideup.jabel.Desugar;

public class TileSelectDialog extends BaseDialog {

    public static final int row = mobile ? 8 : 15;
    public static final float size = mobile ? 54f : 64f;

    public Table blocks = new Table();

    public Block floor;
    public Block block;
    public Block overlay;
    public Block building;
    public List<Folder> list;

    public TileSelectDialog() {
        super("@select.tile");
        addCloseButton();

        Seq<Folder> folders = Seq.with(
                new Folder("select.floor", () -> floor, b -> b instanceof Floor && !(b instanceof OverlayFloor), b -> floor = b),
                new Folder("select.block", () -> block, b -> b instanceof Prop, b -> block = b),
                new Folder("select.overlay", () -> overlay, b -> b instanceof OverlayFloor, b -> overlay = b),
                new Folder("select.building", () -> building, b -> b instanceof Block, b -> building = b));

        list = new List<>(folders::each, Folder::name, Folder::icon, folder -> Pal.accent);
        list.onChanged = this::rebuild;
        list.set(folders.first());
        list.rebuild();

        list.build(cont);
        cont.add(blocks).growX();
        cont.table().width(288f);
    }

    public void rebuild(Folder folder) {
        blocks.clear();
        Table inner = new Table();
        inner.defaults().size(size);

        inner.button(Icon.none, () -> folder.callback(null));
        inner.button(Icon.line, () -> folder.callback(Blocks.air));

        content.blocks().each(folder::pred, block -> {
            TextureRegionDrawable drawable = new TextureRegionDrawable(block.uiIcon);
            inner.button(drawable, () -> folder.callback(block));

            if (inner.getChildren().size % row == 0) inner.row();
        });
        ScrollPane pane = new ScrollPane(inner, Styles.defaultPane);
        blocks.add(pane).grow();
    }

    public void select(Cons4<Floor, Block, Floor, Block> callback) {
        callback.get(floor != null ? floor.asFloor() : null, block, overlay != null ? overlay.asFloor() : null, building);
    }

    public void select(int x, int y) {
        Tile tile = world.tile(x, y);
        if (tile == null) return;

        floor = tile.floor();
        block = tile.build == null ? tile.block() : Blocks.air;
        overlay = tile.overlay();
        building = tile.build == null ? tile.block() : Blocks.air;
        list.rebuild();
    }

    public class Folder {
        public final String name;
        public final Prov<Block> block;
        public final Boolf<Block> pred;
        public final Cons<Block> callback;

        public Folder(String name, Prov<Block> block, Boolf<Block> pred, Cons<Block> callback) {
            this.name = name;
            this.block = block;
            this.pred = pred;
            this.callback = callback;
        }

        public String name() {
            Block selected = block.get();
            return bundle.format(name, selected == null ? bundle.get("none") : selected.localizedName);
        }

        public TextureRegion icon() {
            Block selected = block.get();
            if (selected == null) return Icon.none.getRegion();
            if (selected == Blocks.air) return Icon.line.getRegion();
            return selected.uiIcon;
        }

        public boolean pred(Block block) {
            return pred.get(block) && block.id > 1;
        }

        public void callback(Block block) {
            callback.get(block);
            tile.list.rebuild();
        }
    }

}
