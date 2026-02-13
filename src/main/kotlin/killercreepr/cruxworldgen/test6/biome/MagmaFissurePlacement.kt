package killercreepr.cruxworldgen.test6.biome

import killercreepr.cruxworldgen.test6.biome.BiomeBlendSample
import killercreepr.cruxworldgen.test6.context.GenerateContext
import killercreepr.cruxworldgen.test6.decor.Decoration
import killercreepr.cruxworldgen.test6.decor.DecorationPass
import killercreepr.cruxworldgen.test6.decor.Placement
import killercreepr.cruxworldgen.test6.prop.PropPoint
import org.bukkit.Material
import kotlin.math.abs

data class MagmaFissurePlacement(
  val x: Int,
  val z: Int,
  val floorY: Int,
  val depth: Int
) : Placement

class MagmaFissureDecoration(
  override val pass: DecorationPass = DecorationPass.UNDERGROUND,

  private val fissureThreshold01: Double = 0.86, // higher = rarer
  private val warpAmp: Double = 18.0,

  private val minDepthBelowSurface: Int = 10,    // don’t do tiny surface cracks
  private val magmaDepthMin: Int = 2,
  private val magmaDepthMax: Int = 6
) : Decoration {

  override fun shouldTry(ctx: GenerateContext, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean {
    // Domain warp so fissures snake
    val wx = point.worldX.toDouble()
    val wz = point.worldZ.toDouble()
    val warpX = ctx.noise.charredFissureWarp2D(wx, wz) * warpAmp
    val warpZ = ctx.noise.charredFissureWarp2D(wx + 1000.0, wz + 1000.0) * warpAmp

    val xw = wx + warpX
    val zw = wz + warpZ

    // Ridge-like band mask
    val n = ctx.noise.charredFissureMask2D(xw, zw) // [-1..1]
    val ridge01 = 1.0 - abs(n)                     // [0..1]

    return ridge01 >= fissureThreshold01
  }

  override fun findPlacement(ctx: GenerateContext, point: PropPoint, biomeBlend: BiomeBlendSample): Placement? {
    val chunk = ctx.chunkContext
    val x = point.localX
    val z = point.localZ
    if (x !in 0..15 || z !in 0..15) return null

    val surfaceY = ctx.queries.surfaceY(x, z)

    // Only work below surface a bit
    if ((surfaceY - (chunk.minHeight + 1)) < minDepthBelowSurface) return null

    // Look for: air pocket with solid below (a “fissure floor”)
    // We scan downward from a bit below surface.
    val startY = (surfaceY - 3).coerceAtMost(chunk.maxHeight - 2)
    var y = startY

    while (y > chunk.minHeight + 2) {
      if (chunk.isAir(x, y, z) && chunk.isSolid(x, y - 1, z)) {
        val floorY = y - 1

        // If there’s too much “ceiling” right above, it’s not a fissure shaft—skip.
        // (Optional; comment out if your fissures are more cavern-like)
        val airUp = ctx.queries.airBlocksAbove(x, floorY, z, maxCount = 12)
        if (airUp < 2) return null

        val depth = magmaDepthMin + ((point.seed ushr 12).toInt() and 0x7FFFFFFF) % (magmaDepthMax - magmaDepthMin + 1)
        return MagmaFissurePlacement(x, z, floorY, depth)
      }
      y--
    }

    return null
  }

  override fun place(ctx: GenerateContext, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as MagmaFissurePlacement
    val chunk = ctx.chunkContext

    // Fill magma *above* the floor into air
    for (i in 1..p.depth) {
      val y = p.floorY + i
      if (y !in chunk.minHeight until chunk.maxHeight) break
      if (chunk.isAir(p.x, y, p.z)) {
        chunk.setBlock(p.x, y, p.z, Material.MAGMA_BLOCK)
      } else break
    }
  }
}
