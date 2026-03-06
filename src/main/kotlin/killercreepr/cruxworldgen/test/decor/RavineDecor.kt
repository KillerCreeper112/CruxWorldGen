package killercreepr.cruxworldgen.test.decor

import killercreepr.crux.api.data.Holder
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.context.TerrainQueries
import killercreepr.cruxworldgen.api.context.terrain.Terrain2D
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.decor.PropPoint
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.util.HashUtil.chance
import killercreepr.cruxworldgen.api.util.HashUtil.mixSeed
import killercreepr.cruxworldgen.api.util.SeededRng
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class RavineDecor(
  override val pass: DecorationPass = DecorationPass.UNDERGROUND,

  val chancePerPoint: Double = 0.035,
  val chanceSalt: Long = 9182451L,

  // start/depth rules
  val minDepthBelowSurface: Int = 12,
  val maxDepthBelowSurface: Int = 60,
  val minSurfaceClearance: Int = 6,

  // path
  val stepsMin: Int = 18,
  val stepsMax: Int = 74,
  val stepLengthMin: Double = 3.0,
  val stepLengthMax: Double = 12.0,
  val yawJitter: Double = 0.22,     // radians per step influence
  val pitchJitter: Double = 0.05,   // small vertical drift
  val pitchDampen: Double = 0.72,
  val yawDampen: Double = 0.65,

  // shape
  val widthMin: Double = 2.5,
  val widthMax: Double = 9.5,
  val heightRatioMin: Double = 1.8, // height = width * ratio
  val heightRatioMax: Double = 7.2,

  // roughness
  val wallNoiseAmp: Double = 0.18,
  val floorFlatten: Double = 0.15,  // 0..1, higher = flatter floor

  // blocks
  val carveTo: BlockData? = null,   // null = use AIR
  val bottomFill: Holder<BlockData>? = null // optional lava/water/etc
) : Decoration {

  override fun shouldTry(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean {
    val s = mixSeed(
      seed = region.ctx.worldContext.seed,
      x = point.worldX, y = 0, z = point.worldZ,
      salt = chanceSalt
    )
    return chance(s, chancePerPoint)
  }

  override fun findPlacement(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Placement? {
    val worldX = point.worldX
    val worldZ = point.worldZ
    val terrain2D = region.terrainSnapshot.terrain2D
    val queries = region.terrainQueries

    val surfaceY = terrain2D.surfaceY(worldX, worldZ)
    if (terrain2D.isOceanColumn(worldX, worldZ)) return null

    // pick a start Y below local surface
    val s = mixSeed(
      seed = region.ctx.worldContext.seed,
      x = worldX, y = 0, z = worldZ,
      salt = chanceSalt xor 0x51A7D3C2L
    )

    val depthRange = (maxDepthBelowSurface - minDepthBelowSurface).coerceAtLeast(1)
    val depth = minDepthBelowSurface + positiveMod((s ushr 12).toInt(), depthRange + 1)
    val startY = surfaceY - depth

    if (!region.isInRegion(worldX, startY, worldZ)) return null
    if (surfaceY - startY < minSurfaceClearance) return null

    // require solid where it starts
    if (!queries.isSolid(worldX, startY, worldZ)) return null

    return Placed(
      startX = worldX.toDouble(),
      startY = startY.toDouble(),
      startZ = worldZ.toDouble(),
      seed = point.seed
    )
  }

  override fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as Placed
    val rnd = SeededRng(
      mixSeed(
        region.ctx.worldContext.seed,
        p.startX.toInt(),
        p.startY.toInt(),
        p.startZ.toInt(),
        chanceSalt xor 0xCAFEBABEL
      )
    )

    var x = p.startX
    var y = p.startY
    var z = p.startZ

    var yaw = rnd.nextDouble() * PI * 2.0
    var pitch = (rnd.nextDouble() - 0.5) * 0.18

    var yawVel = 0.0
    var pitchVel = 0.0

    val steps = rnd.nextInt(stepsMin, stepsMax)
    val baseWidth = rnd.nextDouble(widthMin, widthMax)
    val heightRatio = rnd.nextDouble(heightRatioMin, heightRatioMax)

    val minY = region.ctx.chunkContext.minHeight + 2
    val maxY = region.ctx.chunkContext.maxHeight - 3
    val terrain2D = region.terrainSnapshot.terrain2D
    val queries = region.terrainQueries

    for (i in 0 until steps) {
      val t = if (steps <= 1) 0.0 else i.toDouble() / (steps - 1).toDouble()

      // widest near the middle, narrower at ends
      val widthMul = 0.75 + sin(t * PI) * 0.85
      val radiusXZ = baseWidth * widthMul
      val radiusY = radiusXZ * heightRatio

      // keep from erupting too often through the surface
      val localSurfaceY = terrain2D.surfaceY(x.toInt(), z.toInt())
      if (localSurfaceY - y < minSurfaceClearance) {
        pitch = min(pitch, -0.08)
      }

      carveEllipsoid(region, queries, terrain2D, x, y, z, radiusXZ, radiusY)

      val stepLen = rnd.nextDouble(stepLengthMin, stepLengthMax)
      val cosPitch = cos(pitch)
      x += cos(yaw) * cosPitch * stepLen
      y += sin(pitch) * stepLen
      z += sin(yaw) * cosPitch * stepLen

      yaw += yawVel * 0.12
      pitch += pitchVel * 0.07

      pitch *= pitchDampen
      yawVel *= yawDampen
      pitchVel *= 0.82

      yawVel += (rnd.nextDouble() - rnd.nextDouble()) * yawJitter
      pitchVel += (rnd.nextDouble() - rnd.nextDouble()) * pitchJitter

      if (y < minY || y > maxY) break
      if (!region.isInRegion(x.toInt(), y.toInt(), z.toInt())) continue
    }
  }

  private fun carveEllipsoid(
    region: LimitedRegion,
    queries: TerrainQueries,
    terrain2D: Terrain2D,
    cx: Double,
    cy: Double,
    cz: Double,
    radiusXZ: Double,
    radiusY: Double
  ) {
    val minX = kotlin.math.floor(cx - radiusXZ - 1.0).toInt()
    val maxX = kotlin.math.ceil(cx + radiusXZ + 1.0).toInt()
    val minY = kotlin.math.floor(cy - radiusY - 1.0).toInt()
    val maxY = kotlin.math.ceil(cy + radiusY + 1.0).toInt()
    val minZ = kotlin.math.floor(cz - radiusXZ - 1.0).toInt()
    val maxZ = kotlin.math.ceil(cz + radiusXZ + 1.0).toInt()

    for (x in minX..maxX) {
      for (z in minZ..maxZ) {
        val dx = (x + 0.5 - cx) / radiusXZ
        val dz = (z + 0.5 - cz) / radiusXZ
        val horizSq = dx * dx + dz * dz
        if (horizSq > 1.35) continue

        val surfaceY = terrain2D.surfaceY(x, z)

        for (y in minY..maxY) {
          if (!region.isInRegion(x, y, z)) continue
          if (y >= surfaceY) continue // don't cut open sky from decoration unless already underground

          val dyRaw = (y + 0.5 - cy) / radiusY

          // flatter bottom, steeper walls
          val dy = if (dyRaw < 0.0) dyRaw * (1.0 - floorFlatten) else dyRaw

          val rough = wallNoise(region.ctx.worldContext.seed, x, y, z) * wallNoiseAmp
          val q = horizSq + dy * dy + rough

          if (q >= 1.0) continue

          // only carve actual terrain
          if (!queries.isSolid(x, y, z)) continue

          val carveBlock = carveTo ?: airBlock()
          region.setBlock(x, y, z, carveBlock)

          // optional fill near the very bottom
          if (bottomFill != null && dyRaw < -0.72) {
            region.setBlock(x, y, z, bottomFill.value())
          }
        }
      }
    }
  }

  private fun wallNoise(seed: Long, x: Int, y: Int, z: Int): Double {
    val h = mixSeed(seed xor 0x9E3779B97F4A715L, x, y, z, 0x1234ABCDL)
    val v = ((h ushr 11) and 1023L).toDouble() / 1023.0
    return (v - 0.5) * 2.0
  }

  private fun airBlock(): BlockData {
    return BukkitBlockResolver.INSTANCE.resolve(org.bukkit.Material.CAVE_AIR)
  }

  private fun positiveMod(v: Int, mod: Int): Int {
    val m = v % mod
    return if (m < 0) m + mod else m
  }

  data class Placed(
    val startX: Double,
    val startY: Double,
    val startZ: Double,
    val seed: Long
  ) : Placement
}