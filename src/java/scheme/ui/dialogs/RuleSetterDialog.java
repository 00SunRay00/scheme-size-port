package scheme.ui.dialogs;

import arc.scene.ui.*;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.game.Rules;
import mindustry.gen.Call;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import scheme.SchemeVars;

import java.lang.reflect.Field;

import static arc.Core.settings;
import static scheme.SchemeVars.admins;
import static scheme.tools.MessageQueue.send;
import static scheme.ui.dialogs.AdminsConfigDialog.getTools;

public class RuleSetterDialog extends BaseDialog {

    public static Table createRulesTable() {
        Table table = new Table();
        table.top().defaults().pad(4);

        Rules rules = Vars.state.rules;

        table.add(new Label("Rule", Styles.outlineLabel)).left().padRight(20);
        table.add(new Label("Boolean", Styles.outlineLabel)).left().row();

        for (Field field : Rules.class.getDeclaredFields()) {
            if (field.getType() != boolean.class) continue;
            field.setAccessible(true);

            String name = field.getName();
            boolean value;
            try {
                value = field.getBoolean(rules);
            } catch (IllegalAccessException e) {
                continue;
            }
            table.add(new Label(name, Styles.outlineLabel)).left().padRight(20);
            CheckBox cb = new CheckBox("");
            cb.setChecked(value);
            cb.changed(() -> {
                boolean val = cb.isChecked();

                admins.manageRuleBool(val, name);
            });


            table.add(cb).left().row();
        }

        return table;
    }

    public static Table createRulesTableInt() {
        Table table = new Table();
        table.top().defaults().pad(4);

        Rules rules = Vars.state.rules;

        table.add(new Label("Rule", Styles.outlineLabel)).left().padRight(20);
        table.add(new Label("Int/Float", Styles.outlineLabel)).left().row();

        for (Field field : Rules.class.getDeclaredFields()) {
            if (field.getType() != float.class && field.getType() != int.class) continue;
            field.setAccessible(true);

            String name = field.getName();
            float value;
            try {
                if(field.getType() == float.class) {
                    value = field.getFloat(rules);
                } else {
                    // int -> float
                    value = (float) field.getInt(rules);
                }
            } catch(IllegalAccessException e) {
                continue;
            }
            table.add(new Label(name, Styles.outlineLabel)).left().padRight(20);
            TextField cb = new TextField();
            cb.setText(String.valueOf(value));
            cb.changed(() -> {
                String val = cb.getText();

                admins.manageRuleStr(val, name);
            });

            table.add(cb).left().row();
        }

        return table;
    }

    public RuleSetterDialog(){
        super("Rule Setter");

        addCloseButton();
    }

    @Override
    public Dialog show() {
        super.show();
        cont.clear();
        Table inner = new Table();
        inner.add(createRulesTable()).growX().row();
        ScrollPane pane = new ScrollPane(inner, Styles.defaultPane);
        cont.add(pane).left().grow();

        Table inner2 = new Table();
        inner2.add(createRulesTableInt()).growX().row();
        ScrollPane pane2 = new ScrollPane(inner2, Styles.defaultPane);
        cont.add(pane2).right().grow();
        return null;
    }

}
