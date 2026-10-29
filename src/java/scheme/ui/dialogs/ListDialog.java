package scheme.ui.dialogs;

import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.scene.ui.Dialog;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.TextField;
import arc.util.Structs;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Player;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import scheme.ui.List;

import static arc.Core.*;
import static mindustry.Vars.*;

public class ListDialog extends BaseDialog {

    private static final String[] baseTextures = {
            "team-derelict",
            "team-sharded",
            "team-crux",
            "team-malis",
            "status-electrified-ui",
            "status-wet-ui"
    };

    public List<Player> players;
    public List<Team> teams;

    public ListDialog(String title) {
        super(title);
        addCloseButton();

        players = new List<>(Groups.player::each, Player::coloredName, Player::icon, player -> player.team().color);
        teams = new List<>(cons -> Structs.each(cons, Team.baseTeams), Team::localized, ListDialog::texture, team -> team.color);
        teams.header = this::teamIdRow;
    }

    @Override
    public Dialog show() {
        players.set(player);
        return super.show();
    }

    public void addPlayer() {
        players.build(cont);
        players.pane.left();
    }

    public void addTeam() {
        teams.build(cont);
        teams.pane.right();

        // not via ListFragment.set because some dialogs are closed after selecting a team
        players.onChanged = player -> teams.selected = player.team();
    }

    /** Team id 0-255 typed in the field, or null if the text is not one. Digits only, at most 3, so no overflow. */
    public static Team parseTeam(String text) {
        if (text == null || text.isEmpty() || text.length() > 3) return null;
        int id = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') return null;
            id = id * 10 + (c - '0');
        }
        return id <= 255 ? Team.get(id) : null;
    }

    /**
     * First row of the team list: any team by id. Not added to the list; the color square and the name preview the typed team.
     * Shows the selected team's id when it is not a base team (e.g. the selected player's team).
     */
    private void teamIdRow(arc.scene.ui.layout.Table list) {
        list.table(t -> {
            Image swatch = t.image(Tex.whiteui).size(40f).padRight(6f).get();
            TextField field = t.field("", text -> {}).growX().get();
            field.setMessageText("@scheme.team.id");
            field.setFilter(TextField.TextFieldFilter.digitsOnly);
            field.setMaxLength(3);

            Runnable pick = () -> {
                Team team = parseTeam(field.getText());
                if (team != null) teams.set(team);
            };
            field.keyDown(KeyCode.enter, pick);
            t.button(Icon.ok, Styles.clearNonei, pick).size(46f).padLeft(4f).disabled(b -> parseTeam(field.getText()) == null);
            t.row();

            Label name = t.add("").colspan(3).left().padTop(2f).get();
            t.update(() -> {
                Team selected = teams.selected;
                boolean custom = selected != null && selected.id >= Team.baseTeams.length;
                if (custom && !field.hasKeyboard() && field.getText().isEmpty()) field.setText(String.valueOf(selected.id));

                Team typed = parseTeam(field.getText());
                swatch.setColor(typed == null ? Color.darkGray : typed.color);
                name.setText(typed == null ? "" : typed.localized());
                name.setColor(typed == null ? Color.gray : typed.color);
            });
        }).height(74f).row();
    }

    public void addEmpty() {
        cont.table().width(288f);
    }

    public static TextureRegion texture(Team team) {
        // only the base teams have their own icon
        return team.id < baseTextures.length ? atlas.find(baseTextures[team.id]) : Icon.players.getRegion();
    }
}
