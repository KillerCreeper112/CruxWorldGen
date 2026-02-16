package killercreepr.cruxworldgen.api.util

import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import kotlin.math.pow


open class NoiseShaper(
    // Custom points (thresholds) where terrain changes
    val points: List<Point> = listOf(
        Point(-1.0, ShapingFunction.VALLEY),
        Point(0.0, ShapingFunction.FLAT),
        Point(0.5, ShapingFunction.HILLS),
        Point(1.0, ShapingFunction.MOUNTAIN)
    )
) {

    fun interface ShapingFunction{
        companion object{
            fun flattenMiddle(x: Double, width: Double, strength: Double): Double {
                val v = x.coerceIn(-1.0, 1.0)
                val a = kotlin.math.abs(v)
                val t = (a / width).coerceIn(0.0, 1.0)
                val s = smoothstep01(t)
                val scale = (1.0 - strength) + strength * s
                return kotlin.math.sign(v) * a * scale
            }

            fun valleyBias(x: Double, amount: Double, width: Double): Double {
                val v = x.coerceIn(-1.0, 1.0)
                val a = kotlin.math.abs(v)
                val t = (a / width).coerceIn(0.0, 1.0)
                val w = 1.0 - smoothstep01(t)
                return (v - amount * w).coerceIn(-1.0, 1.0)
            }

            fun contrast(x: Double, power: Double): Double {
                val v = x.coerceIn(-1.0, 1.0)
                val a = kotlin.math.abs(v).pow(power)
                return kotlin.math.sign(v) * a
            }

            fun mountainize(x: Double, start: Double, power: Double): Double {
                val v = x.coerceIn(-1.0, 1.0)
                if (v <= start) return v
                val t = ((v - start) / (1.0 - start)).coerceIn(0.0, 1.0)
                return (start + t.pow(power) * (1.0 - start)).coerceIn(-1.0, 1.0)
            }
            fun ridges(x: Double, power: Double = 1.7): Double {
                val v = x.coerceIn(-1.0, 1.0)
                val r01 = (1.0 - kotlin.math.abs(v)).coerceIn(0.0, 1.0) // 1 at 0, 0 at +/-1
                val shaped = r01.pow(power)                              // sharpen ridges
                return shaped * 2.0 - 1.0                                // back to [-1..1]
            }
            fun plateau(x: Double, top: Double = 0.55, softness: Double = 0.25): Double {
                val v = x.coerceIn(-1.0, 1.0)
                // Only affect upper range; softness controls blending
                val t = ((v - (top - softness)) / (2.0 * softness)).coerceIn(0.0, 1.0)
                val s = smoothstep01(t)
                // blend toward 'top' as v approaches and exceeds it
                val blended = v * (1.0 - s) + top * s
                return blended.coerceIn(-1.0, 1.0)
            }
            fun terrace(x: Double, step: Double = 0.18, blend: Double = 0.35): Double {
                val v = x.coerceIn(-1.0, 1.0)
                val q = kotlin.math.floor(v / step) * step
                return (q * (1.0 - blend) + v * blend).coerceIn(-1.0, 1.0)
            }
            fun bias(x: Double, amount: Double = 0.15): Double {
                // Positive amount biases upward, negative biases downward.
                return (x.coerceIn(-1.0, 1.0) + amount).coerceIn(-1.0, 1.0)
            }
            fun canyon(x: Double, depth: Double = 0.40, width: Double = 0.75, wallPower: Double = 1.6): Double {
                val v = x.coerceIn(-1.0, 1.0)
                val biased = valleyBias(v, amount = depth, width = width)   // push downward near middle
                return contrast(biased, power = wallPower)                  // sharpen walls
            }
            fun bulgeMiddle(x: Double, strength: Double = 0.35): Double {
                val v = x.coerceIn(-1.0, 1.0)
                val a = kotlin.math.abs(v)
                val t = a // 0 at center, 1 at edges
                val w = 1.0 - smoothstep01(t) // 1 at center, 0 at edges
                val boosted = v + kotlin.math.sign(v) * strength * w * (1.0 - a)
                return boosted.coerceIn(-1.0, 1.0)
            }

            val DOME = ShapingFunction { n -> bulgeMiddle(n, strength = 0.35) }
            val CANYON = ShapingFunction { n -> canyon(n, depth = 0.40, width = 0.75, wallPower = 1.6) }
            val BIAS_UP = ShapingFunction { n -> bias(n, amount = 0.15) }
            val BIAS_DOWN = ShapingFunction { n -> bias(n, amount = -0.15) }
            val TERRACE = ShapingFunction { n -> terrace(n, step = 0.18, blend = 0.35) }
            val PLATEAU = ShapingFunction { n -> plateau(n, top = 0.55, softness = 0.25) }
            val RIDGES = ShapingFunction { n -> ridges(n, power = 1.7) }
            val FLAT = ShapingFunction { n -> flattenMiddle(n, width = 0.45, strength = 0.70) }
            val VALLEY = ShapingFunction { n -> valleyBias(n, amount = 0.35, width = 0.70) }
            val HILLS = ShapingFunction { n -> contrast(n, power = 0.85) }
            val MOUNTAIN = ShapingFunction { n -> mountainize(n, start = 0.35, power = 2.6) }
        }

        fun shape(noise : Double) : Double
    }
    data class Point(val threshold: Double, val shapingFunction: ShapingFunction)

    // Map the noise value to terrain type based on custom points
    fun mapNoiseToShapingFunction(noiseValue: Double): ShapingFunction {
        for (i in 0 until points.size - 1) {
            val point1 = points[i]
            val point2 = points[i + 1]

            // Check if the noise value is between two points
            if (noiseValue in point1.threshold..point2.threshold) {
                return point1.shapingFunction
            }
        }
        // Default to flat if no match found
        return ShapingFunction.FLAT
    }

    fun smoothShape(noiseValue: Double): Double {
        val x = noiseValue.coerceIn(-1.0, 1.0)

        // Find segment [i, i+1]
        val i = points.indexOfLast { it.threshold <= x }.coerceIn(0, points.size - 2)
        val a = points[i]
        val b = points[i + 1]

        val u = ((x - a.threshold) / (b.threshold - a.threshold)).coerceIn(0.0, 1.0)
        val t = smoothstep01(u)

        val ya = a.shapingFunction.shape(x)
        val yb = b.shapingFunction.shape(x)

        return Curve.lerp(ya, yb, t).coerceIn(-1.0, 1.0)
    }

    // Shape the terrain based on the noise value and custom points
    fun shape(noiseValue: Double): Double {
        val clampedNoise = noiseValue.coerceIn(-1.0, 1.0)  // Ensure the noise is in the valid range

        // Map noise value to terrain type
        val shaping = mapNoiseToShapingFunction(clampedNoise)

        // Based on terrain type, apply different behavior
        return shaping.shape(clampedNoise)
    }
}
