package scheme.ui.dialogs;

import arc.func.*;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.event.FocusListener;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.*;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.struct.OrderedMap;
import arc.struct.Seq;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
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

/** Rules editor: categories on the left, cards of rule groups on the right; changes apply at once. */
public class RuleSetterDialog extends BaseDialog {

    private static final String[] categories = { "waves", "resourcesbuilding", "unit", "enemy", "environment", "teams", "bans", "other" };

    /** Field, category, group, label key; order and keys follow vanilla CustomRulesDialog (v159.3). Unlisted fields go to "other". */
    private static final String[][] ruleTable = {
            { "waves", "waves", "flow", "rules.waves" },
            { "waveSending", "waves", "flow", "rules.wavesending" },
            { "waveTimer", "waves", "flow", "rules.wavetimer" },
            { "waitEnemies", "waves", "flow", "rules.waitForWaveToEnd" },
            { "randomWaveAI", "waves", "flow", "rules.randomwaveai" },
            { "winWave", "waves", "timing", "rules.wavelimit" },
            { "waveSpacing", "waves", "timing", "rules.wavespacing" },
            { "initialWaveSpacing", "waves", "timing", "rules.initialwavespacing" },
            { "wavesSpawnAtCores", "waves", "spawn", "rules.wavespawnatcores" },
            { "airUseSpawns", "waves", "spawn", "rules.airUseSpawns" },
            { "dropZoneRadius", "waves", "spawn", "rules.dropzoneradius" },

            { "infiniteResources", "resourcesbuilding", "resources", "rules.infiniteresources" },
            { "onlyDepositCore", "resourcesbuilding", "resources", "rules.onlydepositcore" },
            { "allowCoreUnloaders", "resourcesbuilding", "resources", "rules.coreunloaders" },
            { "coreIncinerates", "resourcesbuilding", "resources", "rules.coreincinerates" },
            { "deconstructRefundMultiplier", "resourcesbuilding", "resources", "rules.deconstructrefundmultiplier" },
            { "buildCostMultiplier", "resourcesbuilding", "building", "rules.buildcostmultiplier" },
            { "buildSpeedMultiplier", "resourcesbuilding", "building", "rules.buildspeedmultiplier" },
            { "schematicsAllowed", "resourcesbuilding", "building", "rules.schematic" },
            { "derelictRepair", "resourcesbuilding", "building", "rules.derelictrepair" },
            { "blockHealthMultiplier", "resourcesbuilding", "blocks", "rules.blockhealthmultiplier" },
            { "blockDamageMultiplier", "resourcesbuilding", "blocks", "rules.blockdamagemultiplier" },
            { "reactorExplosions", "resourcesbuilding", "blocks", "rules.reactorexplosions" },
            { "cleanupDeadTeams", "resourcesbuilding", "blocks", "rules.cleanupdeadteams" },
            { "allowEditWorldProcessors", "resourcesbuilding", "logic", "rules.alloweditworldprocessors" },
            { "disableWorldProcessors", "resourcesbuilding", "logic", "rules.disableworldprocessors" },

            { "unitCapVariable", "unit", "limits", "rules.unitcapvariable" },
            { "unitCap", "unit", "limits", "rules.unitcap" },
            { "unitFactoryActivationDelay", "unit", "limits", "rules.unitfactoryactivation" },
            { "unitCostMultiplier", "unit", "limits", "rules.unitcostmultiplier" },
            { "unitDamageMultiplier", "unit", "combat", "rules.unitdamagemultiplier" },
            { "unitHealthMultiplier", "unit", "combat", "rules.unithealthmultiplier" },
            { "unitCrashDamageMultiplier", "unit", "combat", "rules.unitcrashdamagemultiplier" },
            { "unitPayloadsExplode", "unit", "combat", "rules.unitpayloadsexplode" },
            { "unitMineSpeedMultiplier", "unit", "work", "rules.unitminespeedmultiplier" },
            { "unitBuildSpeedMultiplier", "unit", "work", "rules.unitbuildspeedmultiplier" },
            { "logicUnitControl", "unit", "logic", "rules.logicunitcontrol" },
            { "logicUnitBuild", "unit", "logic", "rules.logicunitbuild" },
            { "logicUnitDeconstruct", "unit", "logic", "rules.logicunitdeconstruct" },

            { "attackMode", "enemy", "general", "rules.attack" },
            { "coreCapture", "enemy", "general", "rules.corecapture" },
            { "placeRangeCheck", "enemy", "general", "rules.placerangecheck" },
            { "polygonCoreProtection", "enemy", "general", "rules.polygoncoreprotection" },
            { "enemyCoreBuildRadius", "enemy", "general", "rules.enemycorebuildradius" },

            { "damageExplosions", "environment", "world", "rules.explosions" },
            { "fire", "environment", "world", "rules.fire" },
            { "solarMultiplier", "environment", "world", "rules.solarmultiplier" },
            { "pauseDisabled", "environment", "world", "rules.pauseDisabled" },
            { "fog", "environment", "view", "rules.fog" },
            { "lighting", "environment", "view", "rules.lighting" },
            { "limitMapArea", "environment", "area", "rules.limitarea" },
            { "limitX", "environment", "area", null },
            { "limitY", "environment", "area", null },
            { "limitWidth", "environment", "area", null },
            { "limitHeight", "environment", "area", null },
            // vanilla "music" category has no tab here
            { "alwaysPlayMusic", "environment", "music", "rules.alwaysplaymusic" },
            { "disableMusic", "environment", "music", "rules.nomusic" },

            { "allowEditRules", "teams", "general", "rules.allowedit" },

            // vanilla shows these next to the ban buttons
            { "bannedBlocks", "bans", "blocks", "bannedblocks" },
            { "hideBannedBlocks", "bans", "blocks", "rules.hidebannedblocks" },
            { "blockWhitelist", "bans", "blocks", "bannedblocks.whitelist" },
            { "bannedUnits", "bans", "units", "bannedunits" },
            { "unitWhitelist", "bans", "units", "bannedunits.whitelist" },
    };

    /** TeamRule field, category (always teams), group, label key; from the team section of vanilla CustomRulesDialog. */
    private static final String[][] teamTable = {
            { "infiniteResources", "teams", "resources", "rules.infiniteresources" },
            { "fillItems", "teams", "resources", "rules.fillitems" },
            { "buildSpeedMultiplier", "teams", "resources", "rules.buildspeedmultiplier" },
            { "blockHealthMultiplier", "teams", "blocks", "rules.blockhealthmultiplier" },
            { "blockDamageMultiplier", "teams", "blocks", "rules.blockdamagemultiplier" },
            { "protectCores", "teams", "blocks", "rules.protectcores" },
            { "extraCoreBuildRadius", "teams", "blocks", "rules.extracorebuildradius" },
            { "checkPlacement", "teams", "blocks", "rules.checkplacement" },
            { "unitFactoryActivationDelay", "teams", "units", "rules.unitfactoryactivation" },
            { "unitDamageMultiplier", "teams", "units", "rules.unitdamagemultiplier" },
            { "unitCrashDamageMultiplier", "teams", "units", "rules.unitcrashdamagemultiplier" },
            { "unitMineSpeedMultiplier", "teams", "units", "rules.unitminespeedmultiplier" },
            { "unitBuildSpeedMultiplier", "teams", "units", "rules.unitbuildspeedmultiplier" },
            { "unitCostMultiplier", "teams", "units", "rules.unitcostmultiplier" },
            { "unitHealthMultiplier", "teams", "units", "rules.unithealthmultiplier" },
            { "rtsAi", "teams", "ai", "rules.rtsai" },
            { "rtsMinSquad", "teams", "ai", "rules.rtsminsquadsize" },
            { "rtsMaxSquad", "teams", "ai", "rules.rtsmaxsquadsize" },
            { "rtsMinWeight", "teams", "ai", "rules.rtsminattackweight" },
            { "buildAi", "teams", "ai", "rules.buildai" },
            { "buildAiTier", "teams", "ai", "rules.buildaitier" },
    };

    private static Seq<Entry> ruleEntries, teamEntries;

    /** Rules as they were when the dialog opened: the base for "changed" marks and section reset. */
    private Rules snapshot;
    private final ObjectSet<String> changedCategories = new ObjectSet<>();

    private String searchText = "";
    private String category = categories[0];
    private int teamId = -1;
    private boolean narrow;
    private int columns;
    private float cardWidth;
    private TextField searchField;
    private Table main;
    private ScrollPane pane;

    public RuleSetterDialog() {
        super("Rule Setter");
        addCloseListener(); // the buttons themselves are sized in build() from the window width

        hidden(() -> {
            if (scene.getKeyboardFocus() != null) scene.setKeyboardFocus(null);
        });
        resized(() -> {
            if (isShown()) build();
        });
    }

    @Override
    public Dialog show() {
        if (ruleEntries == null) {
            ruleEntries = entries(Rules.class, ruleTable);
            teamEntries = entries(Rules.TeamRule.class, teamTable);
        }
        if (teamId < 0) teamId = Vars.state.rules.defaultTeam.id;
        snapshot = Vars.state.rules.copy();
        updateChanged();

        super.show();
        build();
        return this;
    }

    private void build() {
        // all sizes in UI units (before Scl scaling)
        float width = graphics.getWidth() / Scl.scl(1f);
        narrow = mobile || width < 800f;
        columns = !narrow && width >= 1200f ? 2 : 1;
        // cards fill the space right of the section list (minus pane scrollbar and cell padding)
        float available = width - (narrow ? 40f : 300f);
        cardWidth = columns == 2 ? (available - 40f) / 2f : available - 28f;

        float buttonWidth = Math.min(260f, (width - 48f) / 2f);
        buttons.clearChildren();
        buttons.defaults().size(buttonWidth, 64f);
        buttons.button("@back", Icon.left, this::hide);
        buttons.button("@scheme.rules.reset", Icon.refresh, this::resetCategory)
                .disabled(b -> !searchText.isEmpty() || !changedCategories.contains(category));

        cont.clear();
        cont.top();

        Table search = new Table();
        searchField = search.field(searchText, text -> {
            searchText = text.trim().toLowerCase();
            rebuildContent(true);
        }).growX().get();
        searchField.setMessageText("@search");
        searchField.setCursorPosition(searchText.length());
        search.button(Icon.cancel, Styles.emptyi, () -> {
            searchText = "";
            searchField.setText("");
            rebuildContent(true);
        }).size(40f).padLeft(4f);

        Table tabs = new Table();
        for (String name : categories) {
            Cell<TextButton> cell = tabs.button(categoryTitle(name), Styles.flatTogglet, () -> selectCategory(name))
                    .checked(b -> searchText.isEmpty() && category.equals(name));
            // accent mark: the section has rules that differ from the opening state
            cell.get().image(Tex.whiteui).size(8f).color(Pal.accent).padLeft(8f).visible(() -> changedCategories.contains(name));
            if (narrow) cell.height(46f).minWidth(150f).pad(2f);
            else cell.size(240f, 50f).pad(2f).row();
        }

        main = new Table();
        if (narrow) {
            cont.add(search).growX().pad(4f).row();
            cont.pane(tabs).growX().get().setScrollingDisabled(false, true);
            cont.row();
            pane = cont.pane(main).grow().padTop(6f).get();
        } else {
            cont.table(body -> {
                body.table(left -> {
                    left.top();
                    left.add(search).width(244f).padBottom(8f).row();
                    left.add(tabs).row();
                    left.add("@scheme.rules.changedhint").color(Pal.gray).width(240f).wrap().padTop(12f).left();
                }).top().padRight(12f);
                pane = body.pane(main).grow().get();
            }).grow();
        }
        pane.setScrollingDisabled(true, false);

        rebuildContent(true);
    }

    private void selectCategory(String name) {
        category = name;
        if (!searchText.isEmpty()) {
            searchText = "";
            searchField.setText("");
        }
        rebuildContent(true);
    }

    // Only for switching tab, search or team; a changed value updates its own cell.
    private void rebuildContent(boolean resetScroll) {
        float scroll = pane.getScrollY();
        main.clear();
        main.top().left();

        if (searchText.isEmpty()) {
            buildCategory(main, category, false);
        } else {
            for (String name : categories) {
                Table section = new Table();
                buildCategory(section, name, true);
                if (!section.hasChildren()) continue;

                main.add(categoryTitle(name)).color(Pal.accent).left().padTop(10f).row();
                main.image().color(Pal.accent).height(3f).fillX().padBottom(6f).row();
                main.add(section).left().row();
            }
        }

        pane.layout();
        pane.setScrollYForce(resetScroll ? 0f : scroll);
    }

    private void buildCategory(Table t, String name, boolean searching) {
        t.top().left();

        OrderedMap<String, Table> cards = new OrderedMap<>();
        for (Entry e : ruleEntries)
            if (e.category.equals(name)) ruleCell(card(cards, e.group), e);
        addCards(t, cards);

        if (name.equals("teams")) buildTeams(t, searching);
    }

    private void buildTeams(Table t, boolean searching) {
        if (admins instanceof Mindurka) {
            if (!searching) t.add("@admins.notsupported").left().pad(8f).row();
            return;
        }

        Rules rules = Vars.state.rules;
        if (!searching) t.table(row -> {
            row.left();
            for (Team team : Team.baseTeams) {
                if (team.data().cores.size == 0 && team != rules.defaultTeam && team != rules.waveTeam) continue;
                row.button(Tex.whiteui, Styles.squareTogglei, 30f, () -> selectTeam(team.id))
                        .size(46f).pad(2f).checked(b -> teamId == team.id).tooltip(team.localized())
                        .with(b -> b.getStyle().imageUpColor = team.color);
            }

            row.add("@scheme.rules.teamid").padLeft(12f).padRight(6f);
            arc.scene.ui.Image swatch = row.image(Tex.whiteui).size(30f).padRight(6f).get();
            TextField field = row.field(String.valueOf(teamId), s -> {}).width(80f).valid(s -> ListDialog.parseTeam(s) != null).get();
            field.setFilter(TextField.TextFieldFilter.digitsOnly);
            field.setMaxLength(3);
            // preview of the typed team: its color here, its name next to the field
            Label typedName = row.add("").padLeft(6f).get();
            swatch.update(() -> {
                Team typed = ListDialog.parseTeam(field.getText());
                swatch.setColor(typed == null ? Color.darkGray : typed.color);
                typedName.setText(typed == null ? "" : typed.localized());
                typedName.setColor(typed == null ? Color.gray : typed.color);
            });
            field.keyDown(KeyCode.enter, () -> scene.setKeyboardFocus(null));
            field.addListener(new FocusListener() {
                @Override
                public void keyboardFocusChanged(FocusListener.FocusEvent event, Element element, boolean focused) {
                    if (focused) return;
                    int id = Strings.parseInt(field.getText(), -1);
                    if (id >= 0 && id <= 255 && id != teamId) app.post(() -> selectTeam(id));
                    else field.setText(String.valueOf(teamId));
                }
            });
        }).left().pad(4f).row();

        Team team = Team.all[teamId];
        OrderedMap<String, Table> cards = new OrderedMap<>();
        for (Entry e : teamEntries) teamCell(card(cards, e.group), e, team.id);
        if (!hasRows(cards)) return;

        t.add("[#" + team.color + "]#" + team.id + " " + team.localized() + "[]").left().pad(4f).padTop(10f).row();
        t.image().color(team.color).height(2f).fillX().padBottom(4f).row();
        addCards(t, cards);
    }

    private void selectTeam(int id) {
        teamId = id;
        updateChanged();
        rebuildContent(false);
    }

    /** One card per rule group, created on first use; empty cards (filtered by search) are skipped later. */
    private Table card(OrderedMap<String, Table> cards, String group) {
        Table card = cards.get(group);
        if (card != null) return card;

        card = new Table(Tex.pane);
        card.top().left().margin(10f).defaults().left().width(cardWidth - 24f).padTop(4f);
        card.add(groupTitle(group)).color(Pal.accent).padBottom(4f).row();
        cards.put(group, card);
        return card;
    }

    private static boolean hasRows(OrderedMap<String, Table> cards) {
        for (Table card : cards.values()) if (card.getChildren().size > 1) return true;
        return false;
    }

    private void addCards(Table t, OrderedMap<String, Table> cards) {
        if (!hasRows(cards)) return;

        Table grid = new Table();
        grid.top().left().defaults().width(cardWidth).pad(6f).top().left();
        int count = 0;
        for (Table card : cards.values()) {
            if (card.getChildren().size <= 1) continue; // title only
            grid.add(card);
            if (++count % columns == 0) grid.row();
        }
        t.add(grid).left().row();
    }

    private boolean matches(String label, String name) {
        return searchText.isEmpty() || label.toLowerCase().contains(searchText) || name.toLowerCase().contains(searchText);
    }

    private void ruleCell(Table card, Entry e) {
        String name = e.field.getName();
        String label = label(e);
        if (!matches(label, name)) return;

        Prov<Object> target = () -> Vars.state.rules;
        Boolp changed = () -> differs(e.field, Vars.state.rules, snapshot);
        Class<?> type = e.field.getType();
        if (type == boolean.class) {
            boolCell(card, label, changed, () -> readBool(e.field, target.get()), v -> apply(name, v));
        } else if (type == float.class) {
            numberCell(card, label, changed, () -> readFloat(e.field, target.get()), Strings::canParseFloat, s -> applyFloat(name, Strings.parseFloat(s)));
        } else if (type == int.class) {
            numberCell(card, label, changed, () -> readInt(e.field, target.get()), Strings::canParseInt, s -> applyInt(name, Strings.parseInt(s)));
        } else {
            bannedButton(card, e.field, label, changed, e.content);
        }
    }

    private void teamCell(Table card, Entry e, int tid) {
        String name = e.field.getName();
        String label = label(e);
        if (!matches(label, name)) return;

        Prov<Object> target = () -> Vars.state.rules.teams.get(Team.all[tid]);
        Boolp changed = () -> differs(e.field, target.get(), snapshot.teams.get(Team.all[tid]));
        Class<?> type = e.field.getType();
        if (type == boolean.class) {
            boolCell(card, label, changed, () -> readBool(e.field, target.get()), v -> applyTeam(tid, name, v));
        } else if (type == float.class) {
            numberCell(card, label, changed, () -> readFloat(e.field, target.get()), Strings::canParseFloat, s -> applyTeamFloat(tid, name, Strings.parseFloat(s)));
        } else if (type == int.class) {
            numberCell(card, label, changed, () -> readInt(e.field, target.get()), Strings::canParseInt, s -> applyTeamInt(tid, name, Strings.parseInt(s)));
        }
    }

    /** Text width inside a card: the card margin is 10 on each side, plus a little air. */
    private float inner() {
        return cardWidth - 24f;
    }

    // A rule changed since the dialog opened has its name in the accent color; no extra mark takes width.
    private static void markChanged(Label label, Boolp changed) {
        label.update(() -> label.setColor(changed.get() ? Pal.accent : Color.white));
    }

    // After apply the cell reads the rule back, so a refused change shows the old value.
    private void boolCell(Table card, String label, Boolp changed, Boolp get, Boolc set) {
        CheckBox box = new CheckBox(label);
        box.setChecked(get.get());
        box.changed(() -> {
            set.get(box.isChecked());
            box.setChecked(get.get());
            updateChanged();
        });
        box.left();
        box.getLabel().setWrap(true);
        box.getLabelCell().width(inner() - 44f).left();
        markChanged(box.getLabel(), changed);
        card.add(box).row();
    }

    private void numberCell(Table card, String label, Boolp changed, Prov<String> get, Boolf<String> valid, Cons<String> set) {
        Table row = new Table();
        row.left();
        Label name = row.add(label).wrap().width(inner() - 104f).left().padRight(8f).get();
        markChanged(name, changed);

        String[] shown = { get.get() };
        TextField field = new TextField(shown[0]);
        field.setValidator(valid::get);
        field.keyDown(KeyCode.enter, () -> scene.setKeyboardFocus(null));
        field.addListener(new FocusListener() {
            @Override
            public void keyboardFocusChanged(FocusListener.FocusEvent event, Element element, boolean focused) {
                if (focused) return;
                String text = field.getText().trim();
                if (!text.equals(shown[0]) && valid.get(text)) set.get(text);
                shown[0] = get.get();
                field.setText(shown[0]);
                updateChanged();
            }
        });
        row.add(field).width(96f);
        card.add(row).row();
    }

    @SuppressWarnings("unchecked")
    private <T extends UnlockableContent> void bannedButton(Table card, Field field, String label, Boolp changed, Seq<T> allContent) {
        String key = field.getName();
        int columns = mobile ? 5 : 10;
        float iconSize = 48f;

        TextButton button = new TextButton("");
        Runnable sync = () -> button.setText(label + " [accent](" + banned(field, Vars.state.rules).size + ")[]");
        sync.run();

        button.clicked(() -> {
            BaseDialog dialog = new BaseDialog(label);
            dialog.addCloseButton();
            dialog.cont.pane(pane -> {
                pane.margin(0f, 24f, 0f, 24f);
                int[] count = { 0 };
                allContent.each(item -> {
                    pane.button(new TextureRegionDrawable(item.uiIcon), Styles.clearTogglei, iconSize, () -> {
                        ObjectSet<T> banned = (ObjectSet<T>) banned(field, Vars.state.rules);
                        ObjectSet<T> next = new ObjectSet<>(banned);
                        if (!next.remove(item)) next.add(item);
                        setBanned(field, next);
                        sync.run();
                    }).size(iconSize + 8f).tooltip(item.localizedName).update(b -> b.setChecked(banned(field, Vars.state.rules).contains(item)));
                    if (++count[0] % columns == 0) pane.row();
                });
            }).grow();
            dialog.hidden(() -> {
                sync.run();
                updateChanged();
            });
            dialog.show();
        });

        button.getLabel().setWrap(true);
        markChanged(button.getLabel(), changed);
        card.add(button).height(52f).row();
    }

    /** Sends the whole set; the local rule changes only if the admin tool accepted it. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void setBanned(Field field, ObjectSet next) {
        if (!syncRules(field.getName(), next)) return;
        ObjectSet banned = banned(field, Vars.state.rules);
        banned.clear();
        banned.addAll(next);
    }

    // Section reset: every rule of the shown section (and of the shown team) goes back to its opening value.
    private void resetCategory() {
        for (Entry e : ruleEntries) {
            if (!e.category.equals(category) || !differs(e.field, Vars.state.rules, snapshot)) continue;
            String name = e.field.getName();
            Class<?> type = e.field.getType();
            if (type == boolean.class) apply(name, readBool(e.field, snapshot));
            else if (type == float.class) applyFloat(name, Strings.parseFloat(readFloat(e.field, snapshot)));
            else if (type == int.class) applyInt(name, Strings.parseInt(readInt(e.field, snapshot)));
            else setBanned(e.field, new ObjectSet<>(banned(e.field, snapshot)));
        }

        if (category.equals("teams") && !(admins instanceof Mindurka)) {
            Team team = Team.all[teamId];
            Object now = Vars.state.rules.teams.get(team), before = snapshot.teams.get(team);
            for (Entry e : teamEntries) {
                if (!differs(e.field, now, before)) continue;
                String name = e.field.getName();
                Class<?> type = e.field.getType();
                if (type == boolean.class) applyTeam(teamId, name, readBool(e.field, before));
                else if (type == float.class) applyTeamFloat(teamId, name, Strings.parseFloat(readFloat(e.field, before)));
                else if (type == int.class) applyTeamInt(teamId, name, Strings.parseInt(readInt(e.field, before)));
            }
        }

        updateChanged();
        rebuildContent(false);
    }

    private void updateChanged() {
        changedCategories.clear();
        if (snapshot == null) return;

        for (Entry e : ruleEntries)
            if (differs(e.field, Vars.state.rules, snapshot)) changedCategories.add(e.category);

        if (teamId >= 0) {
            Team team = Team.all[teamId];
            Object now = Vars.state.rules.teams.get(team), before = snapshot.teams.get(team);
            for (Entry e : teamEntries)
                if (differs(e.field, now, before)) changedCategories.add("teams");
        }
    }

    @SuppressWarnings("rawtypes")
    private static boolean differs(Field field, Object now, Object before) {
        try {
            Class<?> type = field.getType();
            if (type == boolean.class) return field.getBoolean(now) != field.getBoolean(before);
            if (type == float.class) return Float.compare(field.getFloat(now), field.getFloat(before)) != 0;
            if (type == int.class) return field.getInt(now) != field.getInt(before);

            ObjectSet a = (ObjectSet) field.get(now), b = (ObjectSet) field.get(before);
            if (a == null || b == null) return a != b;
            if (a.size != b.size) return true;
            for (Object o : a) if (!b.contains(o)) return true;
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    @SuppressWarnings("rawtypes")
    private static ObjectSet banned(Field field, Rules rules) {
        try {
            ObjectSet set = (ObjectSet) field.get(rules);
            return set == null ? new ObjectSet<>() : set;
        } catch (Exception ignored) {
            return new ObjectSet<>();
        }
    }

    private String categoryTitle(String name) {
        return bundle.get(name.equals("bans") || name.equals("other") ? "scheme.rules." + name : "rules.title." + name);
    }

    private static String groupTitle(String group) {
        return bundle.get("scheme.rules.group." + group);
    }

    private static String label(Entry e) {
        if (e.key != null && bundle.has(e.key)) return bundle.get(e.key);
        String name = e.field.getName();
        String lower = "rules." + name.toLowerCase();
        return bundle.has(lower) ? bundle.get(lower) : Strings.capitalize(Strings.insertSpaces(name));
    }

    private static boolean readBool(Field field, Object target) {
        try {
            return field.getBoolean(target);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String readFloat(Field field, Object target) {
        try {
            return Strings.autoFixed(field.getFloat(target), 3);
        } catch (Exception ignored) {
            return "0";
        }
    }

    private static String readInt(Field field, Object target) {
        try {
            return String.valueOf(field.getInt(target));
        } catch (Exception ignored) {
            return "0";
        }
    }

    /** Table rows first, in table order, then the remaining supported fields of the class in declaration order. */
    private static Seq<Entry> entries(Class<?> type, String[][] table) {
        Seq<Entry> result = new Seq<>();
        ObjectSet<String> listed = new ObjectSet<>();
        for (String[] row : table) {
            listed.add(row[0]);
            try {
                Entry e = entry(type.getField(row[0]), row[1], row[2], row[3]);
                if (e != null) result.add(e);
            } catch (NoSuchFieldException ignored) {}
        }
        for (Field field : type.getFields()) {
            if (listed.contains(field.getName())) continue;
            boolean set = field.getType() == ObjectSet.class;
            String category = type == Rules.class ? (set ? "bans" : "other") : "teams";
            Entry e = entry(field, category, set && type == Rules.class ? "lists" : "general", null);
            if (e != null) result.add(e);
        }
        return result;
    }

    private static Entry entry(Field field, String category, String group, String key) {
        int mods = field.getModifiers();
        if (Modifier.isStatic(mods) || Modifier.isFinal(mods)) return null;

        Class<?> type = field.getType();
        if (type == boolean.class || type == float.class || type == int.class) return new Entry(field, category, group, key, null);
        if (type != ObjectSet.class) return null;

        Type generic = field.getGenericType();
        if (!(generic instanceof ParameterizedType)) return null;
        Type arg = ((ParameterizedType) generic).getActualTypeArguments()[0];
        if (!(arg instanceof Class)) return null;
        Seq<? extends UnlockableContent> content = contentForType((Class<?>) arg);
        return content == null ? null : new Entry(field, category, group, key, content);
    }

    @SuppressWarnings("unchecked")
    private static Seq<? extends UnlockableContent> contentForType(Class<?> type) {
        for (mindustry.ctype.ContentType ct : mindustry.ctype.ContentType.all) {
            Seq<mindustry.ctype.Content> seq = content.getBy(ct);
            if (seq.size > 0 && type.isInstance(seq.first())) {
                return (Seq<? extends UnlockableContent>) (Seq<?>) seq;
            }
        }
        return null;
    }

    private boolean syncRules(String fieldName, ObjectSet<?> banned) {
        try {
            return admins.manageRuleObjectSet(fieldName, banned);
        } catch (Exception ignored) {
            return false;
        }
    }

    // Local rules change only after the admin tool applied or sent the change; the cell reads the value back.
    private void apply(String field, boolean value) {
        if (!admins.manageRuleBool(value, field)) return;
        try {
            Rules.class.getField(field).setBoolean(Vars.state.rules, value);
        } catch (Exception ignored) {}
    }

    private void applyFloat(String field, float value) {
        if (!admins.manageRuleStr(String.valueOf(value), field)) return;
        try {
            Rules.class.getField(field).setFloat(Vars.state.rules, value);
        } catch (Exception ignored) {}
    }

    private void applyInt(String field, int value) {
        if (!admins.manageRuleStr(String.valueOf(value), field)) return;
        try {
            Rules.class.getField(field).setInt(Vars.state.rules, value);
        } catch (Exception ignored) {}
    }

    private void applyTeam(int teamId, String field, boolean value) {
        if (!admins.manageTeamRuleBool(teamId, value, field)) return;
        try {
            Rules.TeamRule tr = Vars.state.rules.teams.get(Team.all[teamId]);
            Rules.TeamRule.class.getField(field).setBoolean(tr, value);
        } catch (Exception ignored) {}
    }

    private void applyTeamFloat(int teamId, String field, float value) {
        if (!admins.manageTeamRuleStr(teamId, String.valueOf(value), field)) return;
        try {
            Rules.TeamRule tr = Vars.state.rules.teams.get(Team.all[teamId]);
            Rules.TeamRule.class.getField(field).setFloat(tr, value);
        } catch (Exception ignored) {}
    }

    private void applyTeamInt(int teamId, String field, int value) {
        if (!admins.manageTeamRuleStr(teamId, String.valueOf(value), field)) return;
        try {
            Rules.TeamRule tr = Vars.state.rules.teams.get(Team.all[teamId]);
            Rules.TeamRule.class.getField(field).setInt(tr, value);
        } catch (Exception ignored) {}
    }

    private static class Entry {
        final Field field;
        final String category, group, key;
        final Seq content;

        Entry(Field field, String category, String group, String key, Seq<? extends UnlockableContent> content) {
            this.field = field;
            this.category = category;
            this.group = group;
            this.key = key;
            this.content = content;
        }
    }
}
