package scheme.ui;

import arc.scene.ui.Slider;
import arc.util.Strings;

/** Typed slider input: parses a value in display units and writes it back without step rounding. */
public class AmountInput {

    /**
     * @param scale slider units per displayed unit, e.g. 60 for ticks shown as seconds
     * @param floor the result is never below this, in slider units
     * @return the value in slider units, or NaN if the text is not a finite number
     */
    public static float parse(String text, float scale, float floor) {
        if (text == null) return Float.NaN;

        float input = Strings.parseFloat(text.trim().replace(',', '.'), Float.NaN);
        if (Float.isNaN(input) || Float.isInfinite(input)) return Float.NaN;

        float val = Math.max(floor, input * scale);
        return Float.isInfinite(val) ? Float.NaN : val;
    }

    /** The slider range is always rebuilt from the base range, widened only to fit the value. */
    public static float rangeMin(float baseMin, float value) {
        return Math.min(baseMin, value);
    }

    public static float rangeMax(float baseMax, float value) {
        return Math.max(baseMax, value);
    }

    /** Text of a slider value in display units. */
    public static String format(float value, float scale) {
        float v = value / scale;
        // whole numbers as integers: autoFixed truncates large floats (1000001 -> "1000000.9")
        if (Math.abs(v - Math.round(v)) <= Math.ulp(v) * 4f) return Long.toString(Math.round((double) v));
        return Strings.autoFixed(v, 2);
    }

    /**
     * @param baseMin the slider range set in its constructor
     * @return the value set, in slider units, or NaN if the text was rejected and nothing changed
     */
    public static float apply(Slider slider, float baseMin, float baseMax, float scale, float floor, String text) {
        float val = parse(text, scale, floor);
        if (Float.isNaN(val)) return val;

        slider.setRange(rangeMin(baseMin, val), rangeMax(baseMax, val));

        // ProgressBar.setValue rounds to the step size; a step equal to |val| keeps val exact
        float step = slider.getStepSize();
        if (val != 0f) slider.setStepSize(Math.abs(val));
        slider.setValue(val);
        slider.setStepSize(step);

        slider.change();
        return val;
    }
}
