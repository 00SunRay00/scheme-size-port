package scheme.tools;

import arc.math.geom.Position;

public class PositionBuild {
    public static Position GetPosition(Float x, Float y) {
        return new Position() {
            @Override
            public float getX() {
                return x;
            }

            @Override
            public float getY() {
                return y;
            }
        };
    }
}