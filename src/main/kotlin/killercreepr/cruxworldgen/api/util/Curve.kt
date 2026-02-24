package killercreepr.cruxworldgen.api.util

object Curve {
  fun smoothstep01(t: Double): Double {
    val x = t.coerceIn(0.0, 1.0)
    return x * x * (3.0 - 2.0 * x)
  }

  fun smoothstep(a: Double, b: Double, x: Double): Double {
    val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
  }

  fun smootherstep01(t: Double): Double {
    val x = t.coerceIn(0.0, 1.0)
    return x * x * x * (x * (x * 6 - 15) + 10)
  }
  fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
  fun invLerp(a: Double, b: Double, v: Double): Double =
    if (a == b) 0.0 else ((v - a) / (b - a)).coerceIn(0.0, 1.0)

  fun remap(inA: Double, inB: Double, outA: Double, outB: Double, v: Double): Double =
    lerp(outA, outB, invLerp(inA, inB, v))

  fun band(center01: Double, halfWidth01: Double, y01: Double): Double {
    // 1 at center, fades to 0 outside roughly +/- halfWidth
    val t = kotlin.math.abs(y01 - center01) / halfWidth01
    val clamped = t.coerceIn(0.0, 1.0)
    // smoothstep down: 1 -> 0
    val s = clamped * clamped * (3.0 - 2.0 * clamped)
    return 1.0 - s
  }

  fun trilerp(
    v000: Double, v100: Double, v010: Double, v110: Double,
    v001: Double, v101: Double, v011: Double, v111: Double,
    tx: Double, ty: Double, tz: Double
  ): Double {
    val x00 = lerp(v000, v100, tx)
    val x10 = lerp(v010, v110, tx)
    val x01 = lerp(v001, v101, tx)
    val x11 = lerp(v011, v111, tx)

    val y0 = lerp(x00, x10, tz) // z then x is also fine if consistent
    val y1 = lerp(x01, x11, tz)

    return lerp(y0, y1, ty)
  }
}
