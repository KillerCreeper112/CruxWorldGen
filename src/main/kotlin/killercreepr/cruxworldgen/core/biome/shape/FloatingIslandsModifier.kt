package killercreepr.cruxworldgen.core.biome.shape

import killercreepr.cruxworldgen.api.biome.BiomeShapeType
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.density.DensityBank
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class FloatingIslandsModifier(
  private val region2D: NoiseKey,     // large blobs where islands can exist
  private val height2D: NoiseKey,     // chooses island band height
  private val detail3D: NoiseKey,     // edge breakup / erosion

  private val regionThreshold: Double = 0.1, // higher => fewer islands regions
  private val minY: Double = 110.0,           // min island center height
  private val maxY: Double = 200.0,           // max island center height

  private val radiusXZ: Double = 70.0,        // island radius in XZ (BIG)
  private val radiusY: Double = 22.0,         // island thickness (vertical)
  private val radiusJitter: Double = 25.0,    // adds variety

  private val strength: Double = 120.0,       // MUST be large for dramatic shapes
  private val edgeSoftness: Double = 12.0,    // softer edge => more natural

  // optional: prevents islands from forming deep inside mountains
  private val avoidSolidDepth: Double = 12.0  // how "inside rock" we start suppressing
) : BiomeShapeType {

  override fun density(
    ctx: GenerateContext,
    worldX: Int,
    y: Int,
    worldZ: Int,
    edge: BiomeEdgeContext,
    signalWriter: SignalWriter,
    baseStack: DensityStack,
    out: DensityBank
  ) {
    // 1) Pick big XZ regions
    val rN = ctx.noise.get(region2D).noise2D(worldX, worldZ)   // -1..1
    val r01 = 1.0 - abs(rN)                                    // 0..1 ridge-ish
    val region = ((r01 - regionThreshold) / (1.0 - regionThreshold)).coerceIn(0.0, 1.0)
    val region2 = region * region
    //if (region2 <= 0.0) return

    // 2) Choose an island center height (per XZ)
    val hN = ctx.noise.get(height2D).noise2D(worldX + 1337, worldZ - 777) // -1..1
    val h01 = (hN * 0.5 + 0.5).coerceIn(0.0, 1.0)
    val centerY = (minY + (maxY - minY) * h01)

    // 3) Vary radius a bit per region
    val radN = ctx.noise.get(height2D).noise2D(worldX - 9000, worldZ + 4200) // -1..1
    val rad01 = (radN * 0.5 + 0.5).coerceIn(0.0, 1.0)
    val rx = radiusXZ + (rad01 - 0.5) * 2.0 * radiusJitter
    val rz = radiusXZ + (0.5 - rad01) * 2.0 * radiusJitter
    val ry = radiusY + (rad01 - 0.5) * 8.0

    // 4) Ellipsoid SDF around (worldX,centerY,worldZ)
    // We intentionally do NOT pick "centers" here (needs cellular). This makes “sheets of islands”.
    // It’s still a great first step: big connected floating landmasses.
    val dx = worldX.toDouble()
    val dy = y.toDouble() - centerY
    val dz = worldZ.toDouble()

    // Make the ellipsoid “exist” in XZ where region mask is strong, not everywhere:
    // we treat region2 as the horizontal "footprint weight".
    val nx = dx / max(1.0, rx)
    val ny = dy / max(1.0, ry)
    val nz = dz / max(1.0, rz)

    // This is not a perfect ellipsoid centered at a point; it's more like a thick banded sheet
    // controlled by region2D. That's *good* for your “connected floating islands” look.
    // If you want discrete islands later, you swap region2D for a cell-based distance field.
    val ellipsoid = 1.0 - (nx*nx + ny*ny + nz*nz)

    // Soft edge (turn ellipsoid into 0..1 density weight)
    val core = smoothstep01((ellipsoid * (max(1.0, rx) / edgeSoftness)).coerceIn(0.0, 1.0))
    //if (core <= 0.0) return

    // 5) Break up edges with 3D noise (erosion)
    val dN = ctx.noise.get(detail3D).noise3D(worldX, y, worldZ) // -1..1
    val d01 = (dN * 0.5 + 0.5).coerceIn(0.0, 1.0)
    val erosion = smoothstep01(((d01 - 0.35) / 0.65).coerceIn(0.0, 1.0)) // 0..1

    // 6) Avoid burying islands inside mountains (optional)
    // baseStack.base ~= surfaceY - y. Large positive => deep inside rock.
    val insideRock = max(0.0, baseStack.base)
    val rockFade = smoothstep01(((avoidSolidDepth - insideRock) / avoidSolidDepth).coerceIn(0.0, 1.0))

    // 7) Final add
    val add = region2 * core * erosion * rockFade * strength
    out.addAdditive(add)

    // 8) Undercut carve to make it read as “floating”
    // carve a little under the center plane
    val under = smoothstep01((((centerY - 10.0) - y.toDouble()) / 18.0).coerceIn(0.0, 1.0))
    out.addCarve(region2 * core * under * 30.0)
  }
}
