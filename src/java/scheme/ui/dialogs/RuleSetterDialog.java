package scheme.ui.dialogs;

import arc.scene.event.FocusListener;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.*;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import scheme.tools.admins.Mindurka;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import static arc.Core.*;
import static mindustry.Vars.*;
import static scheme.SchemeVars.admins;

public class RuleSetterDialog extends BaseDialog {

    private String searchText = "";
    private int customTeamId = -1;
    private Table main;

    private String resolveKey(String name) {
        String camelKey = "rules." + name;
        String lowerKey = "rules." + name.toLowerCase();
        if (bundle.has(camelKey)) return camelKey;
        if (bundle.has(lowerKey)) return lowerKey;
        if (bundle.has(name.toLowerCase())) return name.toLowerCase();
        return name;
    }

    @SuppressWarnings("unchecked")
    private void buildRuleFields(Table t, Rules rules) {
        for (Field field : Rules.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;

            String name = field.getName();
            String key = resolveKey(name);
            Class<?> type = field.getType();

            try {
                if (type == boolean.class) {
                    check(t, key, field.getBoolean(rules), v -> apply(name, v));
                } else if (type == float.class) {
                    number(t, key, field.getFloat(rules), v -> applyFloat(name, v));
                } else if (type == int.class) {
                    integer(t, key, field.getInt(rules), v -> applyInt(name, v));
                } else if (type == ObjectSet.class) {
                    Type generic = field.getGenericType();
                    if (generic instanceof ParameterizedType) {
                        Type arg = ((ParameterizedType) generic).getActualTypeArguments()[0];
                        if (arg instanceof Class) {
                            Seq allContent = contentForType((Class<?>) arg);
                            if (allContent != null) {
                                bannedButton(t, name, allContent, (ObjectSet) field.get(rules));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void buildTeams(Table t, Rules rules) {
        if (admins instanceof Mindurka) return;
        t.table(row -> {
            row.left();
            row.add("Team ID (0-255):").left().padRight(5);
            TextField teamField = row.field(customTeamId >= 0 ? String.valueOf(customTeamId) : "", s -> {}).width(80f).valid(s -> {
                if (s.isEmpty()) return true;
                int v = Strings.parseInt(s, -1);
                return v >= 0 && v <= 255;
            }).left().get();
            teamField.addListener(new FocusListener() {
                @Override
                public void keyboardFocusChanged(FocusListener.FocusEvent event, arc.scene.Element element, boolean focused) {
                    if (!focused) {
                        int id = Strings.parseInt(teamField.getText(), -1);
                        customTeamId = (id >= 0 && id <= 255) ? id : -1;
                        rebuild();
                    }
                }
            });
        }).pad(6).left().fillX();
        t.row();

        if (customTeamId >= 0) {
            Team team = Team.all[customTeamId];
            Rules.TeamRule tr = rules.teams.get(team);
            t.add("[#" + team.color + "]#" + customTeamId + " " + team.localized() + "[]").color(team.color).pad(6).padTop(12).left().row();
            t.image().color(team.color).height(2f).pad(2).padLeft(10).padRight(10).fillX().row();
            buildTeamSection(t, team, tr, rules);
        }

        for (Team team : Team.baseTeams) {
            if (team.data() == null) continue;
            if (team.data().cores.size == 0 && team != rules.defaultTeam && team != rules.waveTeam) continue;
            if (team.id == customTeamId) continue;

            Rules.TeamRule tr = rules.teams.get(team);
            t.add("[#" + team.color + "]" + team.localized() + "[]").color(team.color).pad(6).padTop(12).left().row();
            t.image().color(team.color).height(2f).pad(2).padLeft(10).padRight(10).fillX().row();
            buildTeamSection(t, team, tr, rules);
        }
    }

    private void buildTeamSection(Table t, Team team, Rules.TeamRule tr, Rules rules) {
        int tid = team.id;
        for (Field field : Rules.TeamRule.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) continue;

            String name = field.getName();
            String key = resolveKey(name);
            Class<?> type = field.getType();

            try {
                if (type == boolean.class) {
                    check(t, key, field.getBoolean(tr), v -> applyTeam(tid, name, v));
                } else if (type == float.class) {
                    number(t, key, field.getFloat(tr), v -> applyTeamFloat(tid, name, v));
                } else if (type == int.class) {
                    integer(t, key, field.getInt(tr), v -> applyTeamInt(tid, name, v));
                }
            } catch (Exception ignored) {}
        }
    }

    public RuleSetterDialog() {
        super("Rule Setter");
        addCloseButton();
        hidden(() -> {
            if (scene.getKeyboardFocus() != null) scene.setKeyboardFocus(null);
        });
    }

    @Override
    public Dialog show() {
        super.show();
        cont.clear();

        cont.table(t -> {
            t.add("@search").padRight(10);
            TextField field = t.field(searchText, text -> {
                searchText = text.trim().toLowerCase();
                rebuild();
            }).width(200f).pad(8).get();
            field.setCursorPosition(searchText.length());
            scene.setKeyboardFocus(field);
            t.button(Icon.cancel, Styles.emptyi, () -> {
                searchText = "";
                rebuild();
            }).padLeft(10f).size(35f);
        }).fillX().row();

        cont.pane(m -> main = m).left().grow();
        rebuild();

        return this;
    }

    private void rebuild() {
        main.clear();
        main.top().defaults().pad(4).left();

        Rules rules = Vars.state.rules;

        category("Rules", t -> buildRuleFields(t, rules));
        category("rules.title.teams", t -> buildTeams(t, rules));
    }

    private boolean matchesSearch(String key) {
        if (searchText.isEmpty()) return true;
        String text = bundle.has(key) ? bundle.get(key).toLowerCase() : key.toLowerCase();
        return text.contains(searchText) || key.toLowerCase().contains(searchText);
    }

    private void category(String titleKey, arc.func.Cons<Table> builder) {
        Table inner = new Table();
        inner.left().defaults().left().pad(2);
        builder.get(inner);

        if (inner.getChildren().size == 0) return;

        String title = bundle.has(titleKey) ? bundle.get(titleKey) : titleKey;
        main.add(title).color(Pal.accent).pad(6).padLeft(10).fillX().left().row();
        main.image().color(Pal.accent).height(3f).pad(6).padLeft(10).padRight(10).fillX().row();
        main.add(inner).fillX().padLeft(10).row();
    }

    private FieldCell check(Table t, String key, boolean value, arc.func.Boolc onChange) {
        if (!matchesSearch(key)) return FieldCell.EMPTY;

        String label = bundle.has(key) ? bundle.get(key) : key;
        Cell<CheckBox> cell = t.check(label, value, val -> {
            onChange.get(val);
            rebuild();
        }).pad(6).left();
        t.row();
        return new FieldCell(cell);
    }

    private FieldCell number(Table t, String key, float value, arc.func.Floatc onChange) {
        if (!matchesSearch(key)) return FieldCell.EMPTY;

        String label = bundle.has(key) ? bundle.get(key) : key;
        Cell<Table> cell = t.table(row -> {
            row.left();
            row.add(label).left().padRight(5);
            TextField field = row.field(Strings.autoFixed(value, 2), s -> {}).width(120f).valid(Strings::canParseFloat).left().get();
            field.addListener(new FocusListener() {
                @Override
                public void keyboardFocusChanged(FocusListener.FocusEvent event, arc.scene.Element element, boolean focused) {
                    if (!focused && field.isValid()) {
                        float f = Strings.parseFloat(field.getText(), value);
                        onChange.get(f);
                        rebuild();
                    }
                }
            });
        }).pad(6).left().fillX();
        t.row();
        return new FieldCell(cell);
    }

    private FieldCell integer(Table t, String key, int value, arc.func.Intc onChange) {
        if (!matchesSearch(key)) return FieldCell.EMPTY;

        String label = bundle.has(key) ? bundle.get(key) : key;
        Cell<Table> cell = t.table(row -> {
            row.left();
            row.add(label).left().padRight(5);
            TextField field = row.field(String.valueOf(value), s -> {}).width(120f).valid(Strings::canParseInt).left().get();
            field.addListener(new FocusListener() {
                @Override
                public void keyboardFocusChanged(FocusListener.FocusEvent event, arc.scene.Element element, boolean focused) {
                    if (!focused && field.isValid()) {
                        int i = Strings.parseInt(field.getText(), value);
                        onChange.get(i);
                        rebuild();
                    }
                }
            });
        }).pad(6).left().fillX();
        t.row();
        return new FieldCell(cell);
    }

    @SuppressWarnings("unchecked")
    private Seq<? extends UnlockableContent> contentForType(Class<?> type) {
        for (mindustry.ctype.ContentType ct : mindustry.ctype.ContentType.all) {
            Seq<mindustry.ctype.Content> seq = content.getBy(ct);
            if (seq.size > 0 && type.isInstance(seq.first())) {
                return (Seq<? extends UnlockableContent>) (Seq<?>) seq;
            }
        }
        return null;
    }

    private <T extends UnlockableContent> void bannedButton(Table t, String key, Seq<T> allContent, ObjectSet<T> banned) throws IllegalAccessException {
        if (!matchesSearch(key)) return;

        String label = bundle.has(key) ? bundle.get(key) : key;
        int columns = mobile ? 5 : 10;
        float iconSize = 48f;

        t.button(label + " [accent](" + banned.size + ")[]", () -> {
            BaseDialog dialog = new BaseDialog(label);
            dialog.addCloseButton();
            dialog.cont.pane(pane -> {
                pane.margin(0f, 24f, 0f, 24f);
                int[] count = {0};
                allContent.each(item -> {
                    pane.button(new TextureRegionDrawable(item.uiIcon), Styles.clearTogglei, iconSize, () -> {
                        if (banned.contains(item)) banned.remove(item);
                        else banned.add(item);
                        syncRules(key, banned);
                    }).size(iconSize + 8f).tooltip(item.localizedName).update(b -> b.setChecked(banned.contains(item)));
                    if (++count[0] % columns == 0) pane.row();
                });
            }).grow();
            dialog.show();
        }).left().width(300f).pad(6);
        t.row();
    }

    private void syncRules(String fieldName, ObjectSet<?> banned) {
        try {
            admins.manageRuleObjectSet(fieldName, banned);
            rebuild();
        } catch (Exception ignored) {}
    }

    private void apply(String field, boolean value) {
        try {
            Rules.class.getField(field).setBoolean(Vars.state.rules, value);
        } catch (Exception ignored) {}
        admins.manageRuleBool(value, field);
        rebuild();
    }

    private void applyFloat(String field, float value) {
        try {
            Rules.class.getField(field).setFloat(Vars.state.rules, value);
        } catch (Exception ignored) {}
        admins.manageRuleStr(String.valueOf(value), field);
    }

    private void applyInt(String field, int value) {
        try {
            Rules.class.getField(field).setInt(Vars.state.rules, value);
        } catch (Exception ignored) {}
        admins.manageRuleStr(String.valueOf(value), field);
    }

    private void applyTeam(int teamId, String field, boolean value) {
        try {
            Rules.TeamRule tr = Vars.state.rules.teams.get(Team.all[teamId]);
            Rules.TeamRule.class.getField(field).setBoolean(tr, value);
        } catch (Exception ignored) {}
        admins.manageTeamRuleBool(teamId, value, field);
        rebuild();
    }

    private void applyTeamFloat(int teamId, String field, float value) {
        try {
            Rules.TeamRule tr = Vars.state.rules.teams.get(Team.all[teamId]);
            Rules.TeamRule.class.getField(field).setFloat(tr, value);
        } catch (Exception ignored) {}
        admins.manageTeamRuleStr(teamId, String.valueOf(value), field);
    }

    private void applyTeamInt(int teamId, String field, int value) {
        try {
            Rules.TeamRule tr = Vars.state.rules.teams.get(Team.all[teamId]);
            Rules.TeamRule.class.getField(field).setInt(tr, value);
        } catch (Exception ignored) {}
        admins.manageTeamRuleStr(teamId, String.valueOf(value), field);
    }

    private static class FieldCell {
        static final FieldCell EMPTY = new FieldCell(null);
        private final Cell<?> cell;
        FieldCell(Cell<?> cell) { this.cell = cell; }
        FieldCell disabled(boolean disabled) {
            if (cell == null) return this;
            arc.scene.Element elem = cell.get();
            if (elem == null) return this;
            elem.touchable = disabled ? arc.scene.event.Touchable.disabled : arc.scene.event.Touchable.enabled;
            elem.color.a(disabled ? 0.5f : 1f);
            return this;
        }
    }
}
