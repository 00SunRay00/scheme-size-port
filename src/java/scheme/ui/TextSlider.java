package scheme.ui;

import arc.func.Cons;
import arc.graphics.Color;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.scene.utils.Disableable;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog.StringProcessor;

import static arc.Core.*;
import static mindustry.Vars.*;

public class TextSlider extends Table implements Disableable{

    public Label label;
    public Slider slider;

    public TextSlider(int min, int max, int step, int def, StringProcessor processor) {
        touchable = Touchable.childrenOnly;

        label = labelWrap("").style(Styles.outlineLabel).padLeft(12f).growX().left().get();
        label.touchable = Touchable.enabled;
        slider = new Slider(min, max, step, false);

        slider.moved(value -> label.setText(processor.get((int) value)));
        slider.setValue(def);
        slider.change();

        label.clicked(() -> {
            if (isDisabled()) return;
            ui.showTextInput(
                bundle.get("amount", "Значение"),
                bundle.get("enter.amount", "Введите значение:"),
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
                        label.setText(processor.get((int) val));
                        slider.change();
                    } catch (Exception ignored) {}
                }
            );
        });
    }

    public Cell<Stack> build(Table table) {
        return table.stack(slider, this);
    }

    public TextSlider update(Cons<TextSlider> cons) {
        update(() -> cons.get(this));
        return this;
    }

    @Override
    public boolean isDisabled() {
        return slider.isDisabled();
    }

    @Override
    public void setDisabled(boolean isDisabled) {
        slider.setDisabled(isDisabled);
        Color color = isDisabled ? Color.gray : Color.white;
        slider.setColor(color);
        label.setColor(color);
    }
}
