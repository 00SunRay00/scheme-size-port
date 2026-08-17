package scheme.ui.dialogs;

import arc.func.Cons3;
import arc.func.Cons4;
import arc.func.Func;
import arc.scene.event.Touchable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.ctype.UnlockableContent;
import mindustry.game.Team;
import mindustry.gen.Icon;
import mindustry.gen.Player;
import mindustry.ui.Styles;

import static arc.Core.*;
import static mindustry.Vars.*;

public class ContentSelectDialog<T extends UnlockableContent> extends ListDialog {

    public static final int row = mobile ? 5 : 10;
    public static final float size = mobile ? 52f : 64f;

    public Cons4<Player, Team, T, Float> callback;
    public Cons3<Player, Team, T> shortCallback;
    public Func<Float, String> format;

    public boolean showSlider;
    public int items;

    public Label label;
    public Slider slider;

    public ContentSelectDialog(String title, Seq<T> content, int min, int max, int step, Func<Float, String> format) {
        super(title);
        this.format = format;

        label = new Label("", Styles.outlineLabel);
        label.touchable = Touchable.enabled;
        slider = new Slider(min, max, step, false);

        slider.moved(value -> label.setText(format.get(value)));
        slider.change(); // update label

        label.clicked(() -> showAmountInput(slider, format));

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
            cont.table(lblTable -> {
                lblTable.add(label);
                lblTable.button(Icon.pencil, Styles.clearNonei, () -> showAmountInput(slider, format))
                        .size(28f).padLeft(6f).tooltip("@edit");
            }).center().padTop(16f).visible(() -> showSlider).row();

            cont.table(slide -> {
                slide.button(Icon.add, () -> {
                    content.each(this::visible, item -> callback.get(players.get(), teams.get(), item, slider.getValue()));
                    hide();
                }).tooltip("@select.all");
                slide.add(slider).padLeft(8f).growX();
            }).fillX().visible(() -> showSlider);
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
                }).size(size).tooltip(item.localizedName).update(i -> i.setChecked(contain.contains(i)));

                if (++items % row == 0) pane.row();
            });
        });
        addPlayer();
    }

    private void showAmountInput(Slider slider, Func<Float, String> format) {
        ui.showTextInput(
            bundle.get("amount", "Количество"),
            bundle.get("enter.amount", "Введите количество:"),
            String.valueOf((int) slider.getValue()),
            text -> {
                try {
                    float val = Float.parseFloat(text);
                    if (val > slider.getMaxValue()) {
                        slider.setRange(slider.getMinValue(), val);
                    } else if (val < slider.getMinValue()) {
                        slider.setRange(val, slider.getMaxValue());
                    }
                    slider.setValue(val);
                    label.setText(format.get(val));
                    slider.change();
                } catch (Exception ignored) {}
            }
        );
    }

    public void select(boolean showSlider, boolean showPlayers, boolean showTeams, Cons4<Player, Team, T, Float> callback) {
        // in portrait orientation, ui elements may not fit into the screen
        boolean minimize = graphics.getWidth() < Scl.scl(mobile ? 900f : 1250f);

        players.pane.visible(showPlayers);
        players.rebuild(minimize);

        teams.pane.visible(showTeams);
        teams.rebuild(minimize);

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
        return item.logicVisible() ||
                item.uiIcon != atlas.getRegionMap().get("error") ||
                !item.isHidden();
    }
}
