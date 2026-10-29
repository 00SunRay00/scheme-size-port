package scheme.ui;

import arc.func.Cons;
import arc.func.Func;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.event.FocusListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.style.BaseDrawable;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.TextField;
import arc.scene.ui.TextField.TextFieldStyle;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.scene.utils.Disableable;
import arc.util.Align;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog.StringProcessor;

import static arc.Core.*;

/** Label on the left and an editable value on the right, slider below in its own row. */
public class TextSlider extends Table implements Disableable {

    /**
     * Reads as plain text; the underline appears only while editing.
     * The unfocused background is an invisible drawable with the underline's insets, never null:
     * TextField.letterUnderCursor dereferences style.background while dragging (Arc TextField.java:117), and the text keeps its place.
     */
    public static final TextFieldStyle valueField = new TextFieldStyle(Styles.nodeField) {{
        font = Fonts.outline;
        focusedBackground = background;
        background = new BaseDrawable(focusedBackground);
        disabledBackground = background;
    }};

    public Label label;
    /** Null when built from a {@link StringProcessor}: its text already holds the value. */
    public TextField field;
    public Slider slider;

    /** Slider units per displayed unit; typed input is never set below floor, in slider units. */
    public float scale, floor = Float.NEGATIVE_INFINITY;
    public final float baseMin, baseMax;

    private final Func<Float, String> name;
    /** The field text as last written by this slider; typing anything else is applied. */
    private String shown = "";

    /** The label shows the whole text of the processor, there is no value field. */
    public TextSlider(int min, int max, int step, int def, StringProcessor processor) {
        this(min, max, step, def, 1f, false, value -> processor.get((int) (float) value));
    }

    /** @param name the label text for a slider value; may have side effects, like a StringProcessor */
    public TextSlider(float min, float max, float step, float def, float scale, Func<Float, String> name) {
        this(min, max, step, def, scale, true, name);
    }

    private TextSlider(float min, float max, float step, float def, float scale, boolean editable, Func<Float, String> name) {
        touchable = Touchable.childrenOnly;

        this.baseMin = min;
        this.baseMax = max;
        this.scale = scale;
        this.name = name;

        label = new Label("", Styles.outlineLabel);
        slider = new Slider(min, max, step, false);

        if (editable) {
            label.setEllipsis(true);
            add(label).left().growX().minWidth(0f);

            field = new TextField("", valueField);
            field.setAlignment(Align.right);
            field.setFocusTraversal(false);
            field.setFilter((f, c) -> Character.isDigit(c) || c == '.' || c == ',' || c == '-');
            field.addListener(new InputListener() {
                @Override
                public boolean keyDown(InputEvent event, KeyCode key) {
                    if (key != KeyCode.enter && key != KeyCode.escape) return false;
                    if (key == KeyCode.escape) field.setText(shown);

                    scene.setKeyboardFocus(null); // commits in the focus listener
                    event.stop(); // escape must not close the dialog
                    return true;
                }
            });
            field.addListener(new FocusListener() {
                @Override
                public void keyboardFocusChanged(FocusEvent event, Element element, boolean focused) {
                    if (focused) app.post(field::selectAll);
                    else commit();
                }
            });
            add(field).width(60f).right().padLeft(4f).row();
        } else {
            label.setWrap(true);
            add(label).left().growX().row();
        }

        add(slider).colspan(2).growX();

        slider.moved(value -> refresh());
        slider.setValue(def);
        slider.change();
    }

    public Cell<TextSlider> build(Table table) {
        return table.add(this);
    }

    public TextSlider update(Cons<TextSlider> cons) {
        update(() -> cons.get(this));
        return this;
    }

    public TextSlider fieldWidth(float width) {
        if (field != null) getCell(field).width(width);
        return this;
    }

    /** Rewrites the label and, unless it is being edited, the value field. */
    public void refresh() {
        float value = slider.getValue();
        label.setText(name.get(value));
        if (field != null && !field.hasKeyboard()) field.setText(shown = AmountInput.format(value, scale));
    }

    private void commit() {
        String text = field.getText();
        if (!text.equals(shown)) AmountInput.apply(slider, baseMin, baseMax, scale, floor, text); // NaN leaves the slider as is

        // the field still holds focus while the focus event runs, so refresh() skipped it
        field.setText(shown = AmountInput.format(slider.getValue(), scale));
    }

    @Override
    public boolean isDisabled() {
        return slider.isDisabled();
    }

    @Override
    public void setDisabled(boolean isDisabled) {
        slider.setDisabled(isDisabled);
        if (field != null) field.setDisabled(isDisabled);

        Color color = isDisabled ? Color.gray : Color.white;
        slider.setColor(color);
        label.setColor(color);
        if (field != null) field.setColor(color);
    }
}
