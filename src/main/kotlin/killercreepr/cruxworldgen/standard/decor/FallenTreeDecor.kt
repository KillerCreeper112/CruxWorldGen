package killercreepr.cruxworldgen.standard.decor

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.decor.PropPoint
import killercreepr.cruxworldgen.api.decor.VolumetricDecoration
import killercreepr.cruxworldgen.api.decor.VolumetricPropPoint
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.util.HashUtil
import killercreepr.cruxworldgen.bukkit.block.picker.AxisBlockPicker
import org.bukkit.Axis
import kotlin.math.abs

open class FallenTreeDecor(
  override val pass: DecorationPass = DecorationPass.SURFACE,

  val chancePerPoint: Double = 0.10,
  val minAirAbove: Int = 4,
  val maxSlope01: Double = 0.55,     // tighten compared to 100.0; fallen logs look best on mild slopes

  val minLength: Int = 4,
  val maxLength: Int = 10,

  val allowSnake: Boolean = true,
  val snakeChance: Double = 0.22,    // chance per segment to “turn” a bit

  val maxStepUpDown: Int = 1,        // allow trunk to follow terrain +/- 1 block
  val requireSupport: Boolean = true,
  val maxUnsupportedSegments: Int = 1, // allow tiny gaps (roots/rocks), but not floating logs
  val logPicker : AxisBlockPicker,
  val chanceSalt: Long
) : VolumetricDecoration.LazyImpl {

  override fun shouldTry(
    region: LimitedRegion,
    point: VolumetricPropPoint,
    biomeBlend: BiomeBlendSample,
    biome: Biome
  ): Boolean {
    val s = HashUtil.mixSeed(
      seed = region.ctx.worldContext.seed,
      x = point.worldX, y = point.worldY, z = point.worldZ,
      salt = chanceSalt
    )
    return HashUtil.chance(s, chancePerPoint)
  }

  override fun shouldTry(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean {
    val s = HashUtil.mixSeed(
      seed = region.ctx.worldContext.seed,
      x = point.worldX, z = point.worldZ,
      salt = chanceSalt
    )
    return HashUtil.chance(s, chancePerPoint)
  }

  fun findPlacement(
    region: LimitedRegion,
    worldX: Int,
    surfaceY: Int,
    worldZ: Int,
    seed: Long,
    biomeBlend: BiomeBlendSample
  ): Placement?{
    val terrain2D = region.terrainSnapshot.terrain2D
    val queries = region.terrainQueries

    val baseY = surfaceY + 1
    if (!region.isInRegion(worldX, baseY, worldZ)) return null
    if (!queries.isSolid(worldX, surfaceY, worldZ)) return null
    if (terrain2D.isOceanColumn(worldX, worldZ)) return null

    if (queries.slope01(worldX, worldZ) > maxSlope01) return null

    val airAbove = queries.airBlocksAbove(worldX, surfaceY, worldZ, maxCount = minAirAbove)
    if (airAbove < minAirAbove) return null

    val len = HashUtil.chooseInt(seed xor HashUtil.HASH_SALT, minLength, maxLength)

    // Pick a direction (N/E/S/W) deterministically
    val dirIdx = ((seed ushr 32) and 3L).toInt()
    val (dx0, dz0) = when (dirIdx) {
      0 -> 1 to 0
      1 -> -1 to 0
      2 -> 0 to 1
      else -> 0 to -1
    }

    // Validate path along terrain
    var x = worldX
    var z = worldZ
    var y = baseY

    var dx = dx0
    var dz = dz0

    var unsupported = 0

    // We also collect per-segment y so trunk can follow terrain slightly
    val segYs = IntArray(len)

    for (i in 0 until len) {
      if (!region.isInRegion(x, y, z)) return null

      // follow surface, allow small up/down steps
      val segSurfaceY = terrain2D.surfaceY(x, z)
      val segBaseY = segSurfaceY + 1

      val dy = segBaseY - y
      if (abs(dy) > maxStepUpDown) return null
      y = segBaseY

      // space for the log
      if (!queries.isReplaceable(x, y, z)) return null

      // optional: require it sits on something
      if (requireSupport) {
        val supportY = y - 1
        val hasSupport = queries.isSolid(x, supportY, z)
        if (!hasSupport) unsupported++ else unsupported = 0
        if (unsupported > maxUnsupportedSegments) return null
      }

      // enough air above each segment (keeps it from shoving into cliffs)
      val air = queries.airBlocksAbove(x, y - 1, z, maxCount = minAirAbove)
      if (air < minAirAbove) return null

      segYs[i] = y

      // Step forward, maybe snake a little
      if (allowSnake && i > 1) {
        val r = (HashUtil.mixSeed(region.ctx.worldContext.seed, x, 0, z, salt = (seed xor i.toLong())))
        if ((r and 0xFFFF).toInt() < (snakeChance * 65535.0).toInt()) {
          // rotate 90° left or right
          val left = ((r ushr 16) and 1L) == 0L
          val ndx = if (left) -dz else dz
          val ndz = if (left) dx else -dx
          dx = ndx
          dz = ndz
        }
      }

      x += dx
      z += dz
    }

    return Placed(
      startX = worldX,
      startZ = worldZ,
      startY = baseY,
      dx = dx0,
      dz = dz0,
      length = len,
      segYs = segYs,
      seed = seed
    )
  }

  /** Pattern scan: find an anchor/placement candidate */
  override fun findPlacement(
    region: LimitedRegion,
    point: VolumetricPropPoint,
    biomeBlend: BiomeBlendSample,
    biome: Biome
  ): Placement? {
    return findPlacement(region, point.worldX, point.worldY, point.worldZ, point.seed, biomeBlend)
  }

  override fun findPlacement(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Placement? {
    val worldX = point.worldX
    val worldZ = point.worldZ

    val terrain2D = region.terrainSnapshot.terrain2D
    val queries = region.terrainQueries

    val surfaceY = terrain2D.surfaceY(worldX, worldZ)
    return findPlacement(region, worldX, surfaceY, worldZ, point.seed, biomeBlend)
  }

  override fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as Placed
    val bounds = region.regionBounds
    val queries = region.terrainQueries

    var x = p.startX
    var z = p.startZ
    var dx = p.dx
    var dz = p.dz

    val trunkAxis = if (abs(dx) >= abs(dz)) Axis.X else Axis.Z
    // stump/root at start (vertical 1-2 logs)
    run {
      val stumpH = HashUtil.chooseInt(p.seed xor 0xBEEFL, 1, 2)
      for (i in 0 until stumpH) {
        val y = p.startY + i
        if (y < bounds.minY || y > bounds.maxY) break
        if (queries.isReplaceable(x, y, z)) {
          val logTrunk = logPicker.pickBlock(region, x,y,z, trunkAxis) ?: continue
          region.setBlock(x, y, z, logTrunk)
        }
      }
    }

    // trunk
    for (i in 0 until p.length) {
      val y = p.segYs.getOrElse(i) { p.startY }
      if (y < bounds.minY || y > bounds.maxY) break

      if (queries.isReplaceable(x, y, z)) {
        val logTrunk = logPicker.pickBlock(region, x,y,z, trunkAxis) ?: continue
        region.setBlock(x, y, z, logTrunk)
      }

      // occasional “chunk” to make it feel more organic (1 extra log beside)
      val chunk = HashUtil.chooseInt((p.seed xor (i.toLong() * 0x9E37)), 0, 9) == 0
      if (chunk) {
        val sideLeft = ((p.seed ushr i) and 1L) == 0L

        val trunkAxis = if (abs(dx) >= abs(dz)) Axis.X else Axis.Z

        val sx = if (sideLeft) x - dz else x + dz
        val sz = if (sideLeft) z + dx else z - dx
        if (region.isInRegion(sx, y, sz) && queries.isReplaceable(sx, y, sz)) {
          val logSide = logPicker.pickBlock(region, sx, y, sz, trunkAxis) ?: continue
          region.setBlock(sx, y, sz, logSide)
        }
      }

      // step forward; if snaking is enabled, re-run the same deterministic turn logic
      if (allowSnake && i > 1) {
        val r = HashUtil.mixSeed(region.ctx.worldContext.seed, x, 0, z, salt = (p.seed xor i.toLong()))
        val turn = ((r and 0xFFFF).toInt() < (snakeChance * 65535.0).toInt())
        if (turn) {
          val left = ((r ushr 16) and 1L) == 0L
          val ndx = if (left) -dz else dz
          val ndz = if (left) dx else -dx
          dx = ndx
          dz = ndz
        }
      }

      x += dx
      z += dz
    }
  }

  data class Placed(
    val startX: Int,
    val startZ: Int,
    val startY: Int,
    val dx: Int,
    val dz: Int,
    val length: Int,
    val segYs: IntArray,
    val seed: Long
  ) : Placement
}