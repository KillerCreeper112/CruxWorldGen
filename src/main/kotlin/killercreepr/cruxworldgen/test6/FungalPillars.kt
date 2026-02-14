/*todo
package killercreepr.cruxworldgen.test6

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.biome.BiomeShape
import killercreepr.cruxworldgen.api.cave.CaveShape
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.MaterialContext
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.material.MaterialProvider
import killercreepr.cruxworldgen.test.biome.gCaves
import org.bukkit.Material
import kotlin.math.abs
import kotlin.math.pow

class FungalPillars(
  override val caves: CaveShape = gCaves,
  override val decorations: List<Decoration> = listOf(),
  override val materialProvider: MaterialProvider = FungalPillarsMaterials(),

  // --- Base ground ---
  private val baseYAboveSea: Double = 28.0,
  private val baseUndulationAmp: Double = 10.0,

  // --- Pillar distribution ---
  private val warpAmpBlocks: Double = 40.0,
  private val ribbonThreshold01: Double = 0.86, // higher = fewer pillar ribbons
  private val pillarStrength: Double = 1.20,    // must be >1-ish so they open properly

  // --- Pillar geometry ---
  private val minPillarHeight: Double = 35.0,
  private val maxPillarHeight: Double = 120.0,
  private val minRadius: Double = 1.1,
  private val maxRadius: Double = 2.6,
  private val radiusPower: Double = 2.4,        // higher => thinner pillar cores

  // --- Cap geometry ---
  private val capChanceThreshold01: Double = 0.58, // higher => fewer caps
  private val capMinRadius: Double = 4.5,
  private val capMaxRadius: Double = 9.0,
  private val capThickness: Double = 4.0,          // vertical hat thickness
  private val capUndersideHollow: Double = 0.75    // 0..1: more = more hollow underside
) : Biome {

  override val shape = object : BiomeShape {
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      edge: BiomeEdgeContext
    ): DensityStack {

      val sea = ctx.chunkContext.seaLevel.toDouble()
      val baseSurfaceY = sea + baseYAboveSea +
        ctx.noise.plainsHeight2D(worldX, worldZ) * baseUndulationAmp // reuse any 2D you already have

      // biome interior fade so spikes don’t pop at borders
      val edgeBlend = edge.edgeBlendFactor()          // 1 at edge, 0 inside
      val inside01 = (1.0 - edgeBlend).coerceIn(0.0, 1.0)
      val insideEase = inside01 * inside01 * (3.0 - 2.0 * inside01)

      // Pillar/cap fields
      val f = sampleFields(ctx, worldX, worldZ, insideEase)

      // Base ground density
      val base = baseSurfaceY - y.toDouble()

      // Pillar core: vertical cylinder-ish, thinned by noise
      val pillarAdd = pillarAddDensity(ctx, worldX, y, worldZ, baseSurfaceY, f)

      // Cap: overhang near top
      val capAdd = capAddDensity(ctx, worldX, y, worldZ, baseSurfaceY, f)

      return DensityStack(
        base = base,
        add = pillarAdd + capAdd,
        carve = 0.0
      )
    }
  }

  // ---------- Field sampling (2D, continuous) ----------

  private data class Fields(
    val ribbon01: Double,      // how “on the pillar ribbon” we are
    val height: Double,        // pillar height at this column
    val radius: Double,        // pillar radius at this column
    val capRadius: Double,     // hat radius at this column
    val capEnabled: Boolean    // whether hats are allowed in this patch
  )

  private fun sampleFields(ctx: GenerateContext, x: Int, z: Int, insideEase: Double): Fields {
    val wx = x.toDouble()
    val wz = z.toDouble()

    // domain warp
    val warpX = ctx.noise.fungalWarp2D.noise(wx, wz) * warpAmpBlocks
    val warpZ = ctx.noise.fungalWarp2D.noise(wx + 1000.0, wz + 1000.0) * warpAmpBlocks
    val xw = wx + warpX
    val zw = wz + warpZ

    // ribbon field: ridge01 high near centerline
    val n = ctx.noise.fungalPillarRibbon2D.noise(xw, zw) // [-1..1]
    val ridge01 = 1.0 - abs(n)                           // [0..1]

    // threshold into “ribbons”
    val t = ((ridge01 - ribbonThreshold01) / (1.0 - ribbonThreshold01)).coerceIn(0.0, 1.0)
    val ribbon01 = smoothstep01(t) * insideEase

    // height/radius variation along ribbon
    val var01 = (ctx.noise.fungalPillarVar2D.noise(xw, zw) + 1.0) * 0.5
    val height = lerp(minPillarHeight, maxPillarHeight, var01) * (0.35 + 0.65 * ribbon01)

    // thinner core when ribbon01 is small; thicker when strong
    val radius = lerp(minRadius, maxRadius, ribbon01.pow(0.7)).coerceAtLeast(0.75)

    // cap patches (some columns get hats)
    val capPatch01 = (ctx.noise.fungalCapPatch2D.noise(xw, zw) + 1.0) * 0.5
    val capEnabled = capPatch01 > capChanceThreshold01

    val capRadius = lerp(capMinRadius, capMaxRadius, var01)

    return Fields(ribbon01 = ribbon01, height = height, radius = radius, capRadius = capRadius, capEnabled = capEnabled)
  }

  // ---------- Pillar additive density ----------

  private fun pillarAddDensity(
    ctx: GenerateContext,
    x: Int,
    y: Int,
    z: Int,
    baseSurfaceY: Double,
    f: Fields
  ): Double {
    if (f.ribbon01 <= 0.0001) return 0.0

    val pillarTopY = baseSurfaceY + f.height
    val yy = y.toDouble()
    if (yy > pillarTopY) return 0.0

    // This is the “how far below top” vertical component
    val vertical = (pillarTopY - yy).coerceAtLeast(0.0)

    // Edge noise “thins” the pillar so it isn’t a perfect column.
    // You can think of this as a pseudo-radius field without needing distance-to-center.
    val e01 = (ctx.noise.fungalPillarEdge3D.noise(x.toDouble(), yy, z.toDouble()) + 1.0) * 0.5

    // Core mask: higher ribbon => more solid; edge noise reduces solidity
    val core = (f.ribbon01.pow(radiusPower) - (e01 * 0.55)).coerceIn(0.0, 1.0)

    // Convert mask into actual density add.
    // Multiply by vertical so it remains solid up the column.
    return core * vertical * pillarStrength
  }

  // ---------- Cap additive density ----------

  private fun capAddDensity(
    ctx: GenerateContext,
    x: Int,
    y: Int,
    z: Int,
    baseSurfaceY: Double,
    f: Fields
  ): Double {
    if (!f.capEnabled) return 0.0
    if (f.ribbon01 <= 0.20) return 0.0 // don’t cap weak pillars

    val pillarTopY = baseSurfaceY + f.height
    val yy = y.toDouble()

    // only near the top
    val dy = abs(yy - pillarTopY)
    if (dy > capThickness) return 0.0

    // In cap band: 1 at top plane, 0 at bottom plane
    val band01 = (1.0 - (dy / capThickness)).coerceIn(0.0, 1.0)

    // Cap edge irregularity (2D)
    val edge01 = (ctx.noise.fungalCapEdge2D.noise(x.toDouble(), z.toDouble()) + 1.0) * 0.5

    // Make caps appear as chunky plates, not sheets:
    // - stronger near very top
    // - broken edges
    val plate = smoothstep01(band01).pow(1.2) * (0.55 + 0.45 * edge01)

    // Hollow underside: reduce density in lower part of band
    val hollow = (1.0 - capUndersideHollow) + capUndersideHollow * band01

    val mask = (plate * hollow).coerceIn(0.0, 1.0)

    // Convert mask to density add
    return mask * 10.0
  }

  // ---------- Math helpers ----------

  private fun smoothstep01(t: Double): Double = t * t * (3.0 - 2.0 * t)
  private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
}

class FungalPillarsMaterials : MaterialProvider {
  override fun chooseMaterial(context: MaterialContext): Material {
    if (!context.isSolid) return Material.AIR

    // Quick heuristic using surfaceY + depthBelowSurface:
    // cap voxels are generally close to surface+pillarHeight,
    // but you don't have pillarHeight here.
    //
    // For now: keep it simple:
    // - top few layers of any solid (near surface) -> warped blocks occasionally
    // - otherwise stone
    //
    // You can upgrade this later by passing a "biomeShape tag" into MaterialContext
    // or by recomputing cap mask in material provider (requires ctx/noise access).

    val nearSurface = context.depthBelowSurface <= 4
    if (nearSurface) {
      // some warped variation
      return when ((context.worldX * 7349 + context.worldZ * 9151 + context.y * 131).toInt() and 3) {
        0 -> Material.WARPED_WART_BLOCK
        1 -> Material.WARPED_STEM
        2 -> Material.WARPED_HYPHAE
        else -> Material.SHROOMLIGHT
      }
    }

    return Material.BASALT
  }
}
*/
