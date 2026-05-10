package scheme.tools;

import arc.graphics.Blending;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;
import arc.util.Reflect;
import arc.util.Tmp;
import mindustry.content.Blocks;
import mindustry.gen.Building;
import mindustry.graphics.Layer;
import mindustry.type.Item;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.Router;
import mindustry.world.blocks.logic.LogicDisplay;
import mindustry.world.blocks.logic.TileableLogicDisplay;

import static mindustry.Vars.*;
import static mindustry.Vars.content;
import static mindustry.Vars.tilesize;
import static scheme.SchemeVars.render;

public class UpdateContent {
    public static void update(){
        Blocks.distributor.buildType = () -> ((Router) Blocks.distributor).new RouterBuild() {
            @Override
            public boolean canControl() { return true; }

            @Override
            public Building getTileTarget(Item item, Tile from, boolean set) {
                Building target = super.getTileTarget(item, from, set);

                if (unit != null && isControlled() && unit.isShooting()) {
                    float angle = angleTo(unit.aimX(), unit.aimY());
                    Tmp.v1.set(block.size * tilesize, 0f).rotate(angle).add(this);

                    Building other = world.buildWorld(Tmp.v1.x, Tmp.v1.y);
                    if (other != null && other.acceptItem(this, item)) target = other;
                }

                return target;
            }
        };

        content.blocks().each(
                block -> block instanceof LogicDisplay && !(block instanceof TileableLogicDisplay),
                block -> {
                    LogicDisplay ld = (LogicDisplay) block;
                    block.buildType = () -> ld.new LogicDisplayBuild() {
                        @Override
                        public void draw() {
                            super.draw();
                            if (render.borderless) {
                                Draw.draw(Draw.z(), () -> Draw.rect(
                                        Draw.wrap(buffer.getTexture()),
                                        x, y,
                                        block.region.width * Draw.scl,
                                        -block.region.height * Draw.scl
                                ));
                            }
                        }
                    };
                }
        );
        content.blocks().each(
                block -> block instanceof TileableLogicDisplay,
                block -> {
                    TileableLogicDisplay tld = (TileableLogicDisplay) block;
                    block.buildType = () -> tld.new TileableLogicDisplayBuild() {
                        @Override
                        public void draw() {
                            super.draw();

                            if (render.borderless) {
                                Draw.z(Layer.block + 0.021f);
                                Draw.blend(Blending.disabled);
                                Draw.draw(Draw.z(), () -> {
                                    Object root = Reflect.get(LogicDisplay.LogicDisplayBuild.class, this, "rootDisplay");
                                    FrameBuffer buf = null;
                                    if (root != null) {
                                        buf = Reflect.get(LogicDisplay.LogicDisplayBuild.class, root, "buffer");
                                    }
                                    if (buf == null) {
                                        buf = Reflect.get(LogicDisplay.LogicDisplayBuild.class, this, "buffer");
                                    }
                                    if (buf != null) {
                                        int originX = Reflect.get(TileableLogicDisplay.TileableLogicDisplayBuild.class, this, "originX");
                                        int originY = Reflect.get(TileableLogicDisplay.TileableLogicDisplayBuild.class, this, "originY");
                                        int rtx = tile.x - originX, rty = tile.y - originY;
                                        int fs = Reflect.get(TileableLogicDisplay.class, tld, "frameSize");
                                        Tmp.tr1.set(buf.getTexture(),
                                                rtx * 32 - fs, rty * 32 - fs, 32, 32);
                                        Draw.rect(Tmp.tr1, x, y, tilesize, -tilesize);
                                    }
                                });
                                Draw.blend();
                            }
                        }
                    };
                }
        );
    }
}
