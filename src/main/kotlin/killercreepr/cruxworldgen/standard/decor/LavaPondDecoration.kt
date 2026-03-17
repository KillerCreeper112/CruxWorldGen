package killercreepr.cruxworldgen.standard.decor

import killercreepr.cruxworldgen.api.block.BlockSection
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.decor.PropPoint
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.util.HashUtil.mix2D
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockAdapter
import org.bukkit.Material
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.random.Random

open class LavaPondDecoration(
  private val worldSalt: Long,
  private val chancePerPoint: Double = 0.25,
  private val minRadius: Int = 3,
  private val maxRadius: Int = 6,
  private val minDepth: Int = 2,
  private val maxDepth: Int = 4,
  private val flatnessTolerance: Int = 2,
  private val avoidBelowSeaLevel: Boolean = true
) : Decoration {

  override val pass: DecorationPass = DecorationPass.SURFACE

  data class LavaPondPlacement(
    val x: Int,
    val y: Int,
    val z: Int,
    val radiusX: Int,
    val radiusZ: Int,
    val depth: Int,
    val noiseA: Double,
    val noiseB: Double
  ) : Placement

  override fun shouldTry(
    region: LimitedRegion,
    point: PropPoint,
    biomeBlend: BiomeBlendSample
  ): Boolean {
    val seed = mix2D(worldSalt xor 0x4A71C92DL, point.worldX, point.worldZ)
    return Random(seed).nextDouble() < chancePerPoint.coerceIn(0.0, 1.0)
  }

  override fun findPlacement(
    region: LimitedRegion,
    point: PropPoint,
    biomeBlend: BiomeBlendSample
  ): Placement? {
    val seed = mix2D(worldSalt xor 0x73BEE11L, point.worldX, point.worldZ)
    val random = Random(seed)

    val radiusX = random.nextInt(minRadius, maxRadius + 1)
    val radiusZ = random.nextInt(minRadius, maxRadius + 1)
    val depth = random.nextInt(minDepth, maxDepth + 1)

    val centerX = point.worldX
    val centerZ = point.worldZ
    val centerY = findSurfaceY(region, centerX, centerZ)
    if (centerY == Int.MIN_VALUE) return null
    if (!region.isInRegion(centerX, centerY, centerZ)) return null
    if (avoidBelowSeaLevel && centerY <= region.ctx.chunkContext.seaLevel) return null

    val centerGround = region.getBlock(centerX, centerY, centerZ)
    val centerAbove = region.getBlock(centerX, centerY + 1, centerZ)

    if (!isGoodGround(centerGround)) return null
    if (!centerAbove.blockData().isEmpty()) return null
    if (centerGround.blockData().isLiquid()) return null
    if (centerAbove.blockData().isLiquid()) return null

    val checkRX = radiusX + 2
    val checkRZ = radiusZ + 2

    for (dx in -checkRX..checkRX) {
      for (dz in -checkRZ..checkRZ) {
        val nx = dx.toDouble() / checkRX.toDouble()
        val nz = dz.toDouble() / checkRZ.toDouble()
        if (nx * nx + nz * nz > 1.0) continue

        val sx = centerX + dx
        val sz = centerZ + dz

        val sy = findSurfaceY(region, sx, sz)
        if (sy == Int.MIN_VALUE) return null
        if (!region.isInRegion(sx, sy, sz)) return null
        if (abs(sy - centerY) > flatnessTolerance) return null

        val ground = region.getBlock(sx, sy, sz)
        val above = region.getBlock(sx, sy + 1, sz)

        if (!isGoodGround(ground)) return null
        if (!above.blockData().isEmpty()) return null
        if (ground.blockData().isLiquid()) return null
        if (above.blockData().isLiquid()) return null
      }
    }

    return LavaPondPlacement(
      x = centerX,
      y = centerY,
      z = centerZ,
      radiusX = radiusX,
      radiusZ = radiusZ,
      depth = depth,
      noiseA = random.nextDouble(-0.18, 0.18),
      noiseB = random.nextDouble(-0.18, 0.18)
    )
  }

  override fun place(
    region: LimitedRegion,
    placement: Placement,
    biomeBlend: BiomeBlendSample
  ) {
    val pond = placement as? LavaPondPlacement ?: return
    val resolver = BukkitBlockAdapter.resolver()
    val liquidLevel = pond.y

    data class Cell(
      val x: Int,
      val z: Int,
      val distSq: Double,
      val floorY: Int,
      val topY: Int
    )

    val inside = HashMap<Pair<Int, Int>, Cell>()
    val rim = HashSet<Pair<Int, Int>>()

    val maxRX = pond.radiusX + 2
    val maxRZ = pond.radiusZ + 2

    // Phase 1: classify columns
    for (dx in -maxRX..maxRX) {
      for (dz in -maxRZ..maxRZ) {
        val worldX = pond.x + dx
        val worldZ = pond.z + dz

        val topY = findSurfaceY(region, worldX, worldZ)
        if (topY == Int.MIN_VALUE) continue
        if (!region.isInRegion(worldX, topY, worldZ)) continue

        val nx = dx.toDouble() / pond.radiusX.toDouble()
        val nz = dz.toDouble() / pond.radiusZ.toDouble()

        val wobble =
          pond.noiseA * kotlin.math.sin(dx * 0.9) +
            pond.noiseB * kotlin.math.cos(dz * 1.1) +
            0.10 * kotlin.math.sin((dx + dz) * 0.7)

        val distSq = nx * nx + nz * nz + wobble
        if (distSq > 1.30) continue

        val key = worldX to worldZ

        if (distSq <= 1.0) {
          val bowl = (1.0 - distSq).coerceIn(0.0, 1.0)
          val carveDepth = max(1, floor(bowl * pond.depth).toInt())
          val floorY = liquidLevel - carveDepth
          inside[key] = Cell(worldX, worldZ, distSq, floorY, topY)
        } else {
          rim.add(key)
        }
      }
    }

    // Phase 2: carve interior
    for ((_, cell) in inside) {
      for (y in cell.topY + 1 downTo cell.floorY) {
        if (!region.isInRegion(cell.x, y, cell.z)) continue

        val material = when {
          y > liquidLevel -> Material.AIR
          else -> Material.LAVA
        }

        region.setBlock(cell.x, y, cell.z, resolver.resolve(material))
      }

      // solid floor under pond
      val supportY = cell.floorY - 1
      if (region.isInRegion(cell.x, supportY, cell.z)) {
        val below = region.getBlock(cell.x, supportY, cell.z)
        if (below.blockData().isEmpty() || below.blockData().isLiquid()) {
          region.setBlock(cell.x, supportY, cell.z, resolver.resolve(Material.STONE))
        }
      }
    }

    // Phase 3: build outer walls only where neighbor is outside pond
    for ((_, cell) in inside) {
      for ((ox, oz) in arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
        val nx = cell.x + ox
        val nz = cell.z + oz
        val nKey = nx to nz

        if (inside.containsKey(nKey)) continue

        for (y in cell.floorY..liquidLevel) {
          if (!region.isInRegion(nx, y, nz)) continue
          val side = region.getBlock(nx, y, nz)
          if (side.blockData().isEmpty() || side.blockData().isLiquid()) {
            region.setBlock(nx, y, nz, resolver.resolve(Material.STONE))
          }
        }
      }
    }

    // Phase 4: rim cleanup
    for ((x, z) in rim) {
      val topY = findSurfaceY(region, x, z)
      if (topY == Int.MIN_VALUE) continue
      if (!region.isInRegion(x, topY, z)) continue

      val top = region.getBlock(x, topY, z)
      if (isSoftTop(top)) {
        region.setBlock(x, topY, z, resolver.resolve(Material.STONE))
      }
    }
  }

  private fun findSurfaceY(region: LimitedRegion, x: Int, z: Int): Int {
    for (y in region.regionBounds.maxY downTo region.regionBounds.minY) {
      if (!region.isInRegion(x, y, z)) continue

      val ground = region.getBlock(x, y, z)
      if (!ground.blockData().isSolid()) continue
      if (ground.blockData().isLiquid()) continue

      val aboveY = y + 1
      if (!region.isInRegion(x, aboveY, z)) continue

      val above = region.getBlock(x, aboveY, z)
      if (above.blockData().isEmpty()) {
        return y
      }
    }
    return Int.MIN_VALUE
  }

  private fun isGoodGround(block: BlockSection): Boolean {
    val data = block.blockData()
    if (!data.isSolid()) return false
    if (data.isLiquid()) return false
    return true
  }

  private fun isSoftTop(block: BlockSection): Boolean {
    val data = block.blockData()
    return data.isSolid() && !data.isLiquid()
  }
}