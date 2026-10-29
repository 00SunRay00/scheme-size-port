package scheme.ui;

import arc.scene.ui.Slider;
import arc.scene.ui.Slider.SliderStyle;
import org.junit.Test;

import static org.junit.Assert.*;

public class AmountInputTest {

    private static final float NO_FLOOR = Float.NEGATIVE_INFINITY;

    @Test
    public void parsesPlainNumbers() {
        assertEquals(1000f, AmountInput.parse("1000", 1f, NO_FLOOR), 0f);
        assertEquals(-500f, AmountInput.parse("-500", 1f, NO_FLOOR), 0f);
        assertEquals(12.5f, AmountInput.parse(" 12.5 ", 1f, NO_FLOOR), 0f);
    }

    @Test
    public void acceptsCommaAsDecimalSeparator() {
        assertEquals(0.25f, AmountInput.parse("0,25", 1f, NO_FLOOR), 0f);
    }

    @Test
    public void rejectsGarbageWithNaN() {
        assertTrue(Float.isNaN(AmountInput.parse("", 1f, NO_FLOOR)));
        assertTrue(Float.isNaN(AmountInput.parse("abc", 1f, NO_FLOOR)));
        assertTrue(Float.isNaN(AmountInput.parse("1.2.3", 1f, NO_FLOOR)));
        assertTrue(Float.isNaN(AmountInput.parse("-", 1f, NO_FLOOR)));
        assertTrue(Float.isNaN(AmountInput.parse(null, 1f, NO_FLOOR)));
        assertTrue(Float.isNaN(AmountInput.parse("NaN", 1f, NO_FLOOR)));
        assertTrue(Float.isNaN(AmountInput.parse("Infinity", 1f, NO_FLOOR)));
    }

    @Test
    public void rejectsOverflowAfterScaling() {
        assertTrue(Float.isNaN(AmountInput.parse("3e38", 60f, NO_FLOOR)));
    }

    @Test
    public void scalesSecondsToTicks() {
        assertEquals(15f, AmountInput.parse("0.25", 60f, 0f), 0f);
        assertEquals(600f, AmountInput.parse("10", 60f, 0f), 0f);
    }

    @Test
    public void clampsToFloorInSliderUnits() {
        assertEquals(0f, AmountInput.parse("-5", 60f, 0f), 0f);
        assertEquals(-5f, AmountInput.parse("-5", 1f, NO_FLOOR), 0f);
    }

    @Test
    public void rangeIsRebuiltFromBase() {
        // value above base widens max, a later smaller value returns to the base
        assertEquals(1000f, AmountInput.rangeMax(600f, 1000f), 0f);
        assertEquals(600f, AmountInput.rangeMax(600f, 100f), 0f);
        assertEquals(-50f, AmountInput.rangeMin(0f, -50f), 0f);
        assertEquals(0f, AmountInput.rangeMin(0f, 100f), 0f);
    }

    @Test
    public void formatsLargeWholeNumbersExactly() {
        assertEquals("1000001", AmountInput.format(1000001f, 1f));
        assertEquals("-1000000", AmountInput.format(-1000000f, 1f));
    }

    @Test
    public void formatsInDisplayUnits() {
        assertEquals("0.25", AmountInput.format(15f, 60f));
        assertEquals("80", AmountInput.format(80f, 1f));
    }

    @Test
    public void applyWidensRangeAndKeepsOffStepValue() {
        Slider slider = new Slider(0, 600, 20, false, new SliderStyle());

        assertEquals(1000f, AmountInput.apply(slider, 0, 600, 1f, NO_FLOOR, "1000"), 0f);
        assertEquals(1000f, slider.getMaxValue(), 0f);
        assertEquals(1000f, slider.getValue(), 0f);

        // 33 is not a multiple of the step 20
        assertEquals(33f, AmountInput.apply(slider, 0, 600, 1f, NO_FLOOR, "33"), 0f);
        assertEquals(600f, slider.getMaxValue(), 0f);
        assertEquals(33f, slider.getValue(), 0f);
        assertEquals(20f, slider.getStepSize(), 0f);
    }

    @Test
    public void applyIgnoresGarbage() {
        Slider slider = new Slider(0, 600, 20, false, new SliderStyle());
        slider.setValue(80f);

        assertTrue(Float.isNaN(AmountInput.apply(slider, 0, 600, 1f, NO_FLOOR, "abc")));
        assertEquals(80f, slider.getValue(), 0f);
        assertEquals(600f, slider.getMaxValue(), 0f);
    }

    @Test
    public void applyScalesSecondsToTicks() {
        Slider slider = new Slider(0, 500 * 3600, 60, false, new SliderStyle());

        assertEquals(15f, AmountInput.apply(slider, 0, 500 * 3600, 60f, 0f, "0.25"), 0f);
        assertEquals(15f, slider.getValue(), 0f);
    }
}
