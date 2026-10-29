package scheme.ui.dialogs;

import arc.func.Cons3;
import arc.func.Cons4;
import arc.func.Func;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.ctype.UnlockableContent;
import mindustry.game.Team;
import mindustry.gen.Icon;
import mindustry.gen.Player;
import scheme.ui.TextSlider;

import static arc.Core.*;
import static mindustry.Vars.*;

public class ContentSelectDialog<T extends UnlockableContent> extends ListDialog {

    public static final int row = mobile ? 5 : 10;
    public static final float size = mobile ? 52f : 64f;
    /** Hidden content that is still useful to select. */
    public static final Seq<UnlockableContent> specials = Seq.with(
            UnitTypes.latum, UnitTypes.renale, content.unit("quell-missile"), content.unit("disrupt-missile"), content.unit("scathe-missile"),
            StatusEffects.muddy, StatusEffects.shielded, StatusEffects.corroded, StatusEffects.disarmed, StatusEffects.invincible);

    public Cons4<Player, Team, T, Float> callback;
    public Cons3<Player, Team, T> shortCallback;
    public Func<Float, String> format;

    public boolean showSlider;
    public int items;

    public TextSlider amount;
    public Label label;
    public Slider slider;

    /** Slider units per value typed in the amount field. */
    public float inputScale = 1f;
    /** The amount field never sets the slider below this, in slider units. */
    public float inputFloor = Float.NEGATIVE_INFINITY;

    public ContentSelectDialog(String title, Seq<T> content, int min, int max, int step, Func<Float, String> format) {
        super(title);
        this.format = format;

        amount = new TextSlider(min, max, step, min, inputScale, format).fieldWidth(120f);
        label = amount.label;
        slider = amount.slider;

        Table table = new Table();
        table.pane(pane -> {
            pane.margin(0f, 24f, 0f, 24f);

            content.each(this::visible, item -> {
                pane.button(new TextureRegionDrawable(item.uiIcon), () -> {
                    callback.get(players.get(), teams.get(), item, slider.getValue());
                    hide();
                }).size(size).tooltip(item.localizedName);

                if (++items % row == 0) pane.row();
            });
        });

        addPlayer();

        cont.table(cont -> {
            cont.add(table).row();
            cont.table(slide -> {
                slide.button(Icon.add, () -> {
                    content.each(this::visible, item -> callback.get(players.get(), teams.get(), item, slider.getValue()));
                    hide();
                }).tooltip("@select.all").bottom();
                slide.add(amount).padLeft(8f).growX();
            }).fillX().padTop(16f).visible(() -> showSlider);
        }).growX();

        addTeam();
    }

    public ContentSelectDialog(String title, Seq<T> content, boolean turn, Seq<Object> contain){
        super(title);

        Table table = new Table();
        table.pane(pane -> {
            pane.margin(0f, 24f, 0f, 24f);

            content.each(item -> true, item -> {
                pane.button(new TextureRegionDrawable(item.uiIcon), () -> {
                    shortCallback.get(players.get(), teams.get(), item);
                    if(!turn) hide();
                }).size(size).tooltip(item.localizedName).update(i -> i.setChecked(contain.contains(item)));

                if (++items % row == 0) pane.row();
            });
        });
        addPlayer();
    }

    public void select(boolean showSlider, boolean showPlayers, boolean showTeams, Cons4<Player, Team, T, Float> callback) {
        // in portrait orientation, ui elements may not fit into the screen
        boolean minimize = graphics.getWidth() < Scl.scl(mobile ? 900f : 1250f);

        players.pane.visible(showPlayers);
        players.rebuild(minimize);

        teams.pane.visible(showTeams);
        teams.rebuild(minimize);

        // inputScale and inputFloor are set after construction
        amount.scale = inputScale;
        amount.floor = inputFloor;
        amount.refresh();

        this.showSlider = showSlider;
        this.callback = callback;
        show();
    }

    public void select(boolean showPlayers, boolean showTeams, Cons3<Player, Team, T> shortCallback) {
        // in portrait orientation, ui elements may not fit into the screen
        boolean minimize = graphics.getWidth() < Scl.scl(mobile ? 900f : 1250f);

        players.pane.visible(showPlayers);
        players.rebuild(minimize);

        teams.pane.visible(showTeams);
        teams.rebuild(minimize);

        this.shortCallback = shortCallback;
        show();
    }

    public boolean visible(T item) {
        return item.logicVisible() || specials.contains(item);
    }
}
