package com.mineclone.core.math;

public final class V3 {
    public final double x, y, z;

    public V3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public V3 add(V3 o) {
        return new V3(x + o.x, y + o.y, z + o.z);
    }

    public V3 sub(V3 o) {
        return new V3(x - o.x, y - o.y, z - o.z);
    }

    public V3 scl(double s) {
        return new V3(x * s, y * s, z * s);
    }

    public double len() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public V3 nrm() {
        double L = len();
        return L > 1e-9 ? scl(1.0 / L) : new V3(0, 0, 0);
    }

    public static V3 cross(V3 a, V3 b) {
        return new V3(
            a.y * b.z - a.z * b.y,
            a.z * b.x - a.x * b.z,
            a.x * b.y - a.y * b.x
        );
    }


    @Override
    public String toString() {
        return "V3{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}
