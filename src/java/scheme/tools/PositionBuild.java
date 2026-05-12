package scheme.tools;

import arc.math.geom.Point2;
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
    public static Position PointToPosition(Point2 point) {return GetPosition((float) point.x,(float) point.y);}
    public static Point2 PositionToPoint(Position position) {return new Point2((int) position.getX(),(int) position.getY());}
}