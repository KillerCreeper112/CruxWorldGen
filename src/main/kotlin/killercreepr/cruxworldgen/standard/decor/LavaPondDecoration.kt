package killercreepr.cruxworldgen.standard.decor

import killercreepr.crux.api.block.CruxBlockWrapper.material
import killercreepr.cruxworldgen.api.block.BlockSection
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.decor.PropPoint
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockAdapter
import org.bukkit.Material
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

class LavaPondDecoration(
  private val worldSalt: Long,
  private val chancePerPoint: Double = 0.10,
  private val minRadius: Int = 3,
  private val maxRadius: Int = 6,
  private val minDepth: Int = 1,
  private val maxDepth: Int = 3,
  private val flatnessTolerance: Int = 2,
  private val seaLevel: Int = 63,
  private val avoidBelowSeaLevel: Boolean = true
) : Decoration {

  override val pass: DecorationPass = DecorationPass.SURFACE

  data class LavaPondPlacement(
    val x: Int,
    val y: Int,
    val z: Int,
    val radiusX: Int,
    val radiusZ: Int,
    val depth: Int
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
    val centerY = region.terrainSnapshot.terrain2D.surfaceY(centerX, centerZ)
    if(!region.isInRegion(centerX, centerY, centerZ)) return null

    if (avoidBelowSeaLevel && centerY <= seaLevel) return null

    val centerGround = region.getBlock(centerX, centerY, centerZ)
    if (!isGoodGround(centerGround)) return null
    if (!region.getBlock(centerX, centerY + 1, centerZ).blockData().isEmpty()) return null

    // Check the intended footprint is reasonably flat and dry
    val checkRX = radiusX + 1
    val checkRZ = radiusZ + 1

    for (dx in -checkRX..checkRX) {
      for (dz in -checkRZ..checkRZ) {
        val nx = dx.toDouble() / checkRX.toDouble()
        val nz = dz.toDouble() / checkRZ.toDouble()
        if (nx * nx + nz * nz > 1.0) continue

        val sx = centerX + dx
        val sz = centerZ + dz
        val sy = findSurfaceY(region, sx, sz)
        if (sy == Int.MIN_VALUE) return null

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
      depth = depth
    )
  }

  override fun place(
    region: LimitedRegion,
    placement: Placement,
    biomeBlend: BiomeBlendSample
  ) {
    val pond = placement as? LavaPondPlacement ?: return

    val liquidLevel = pond.y
    val rimRadiusX = pond.radiusX + 1
    val rimRadiusZ = pond.radiusZ + 1

    for (dx in -rimRadiusX..rimRadiusX) {
      for (dz in -rimRadiusZ..rimRadiusZ) {
        val nx = dx.toDouble() / pond.radiusX.toDouble()
        val nz = dz.toDouble() / pond.radiusZ.toDouble()
        val distSq = nx * nx + nz * nz

        if (distSq > 1.35) continue

        val worldX = pond.x + dx
        val worldZ = pond.z + dz

        if (distSq <= 1.0) {
          // Main bowl
          val bowl = (1.0 - distSq).coerceIn(0.0, 1.0)
          val carveDepth = max(1, floor(bowl * pond.depth).toInt())
          val floorY = liquidLevel - carveDepth

          for (y in liquidLevel + 1 downTo floorY) {
            val material = if (y <= liquidLevel) Material.LAVA else Material.AIR
            region.setBlock(worldX, y, worldZ,
              BukkitBlockAdapter.resolver().resolve(material))
          }

          // Put solid support under the lava floor if needed
          val belowFloor = region.getBlock(worldX, floorY - 1, worldZ)
          if (belowFloor.blockData().isEmpty() || belowFloor.blockData().isLiquid()) {
            region.setBlock(worldX, floorY - 1, worldZ, BukkitBlockAdapter.resolver().resolve(Material.STONE))
          }
        } else {
          // Light rim cleanup
          val topY = findSurfaceY(region, worldX, worldZ)
          if (topY != Int.MIN_VALUE) {
            val top = region.getBlock(worldX, topY, worldZ)
            if (isSoftTop(top)) {
              region.setBlock(worldX, topY, worldZ, BukkitBlockAdapter.resolver().resolve(Material.STONE))
            }
          }
        }
      }
    }
  }

  private fun findSurfaceY(region: LimitedRegion, x: Int, z: Int): Int {
    for (y in region.regionBounds.minY downTo region.regionBounds.maxY) {
      val ground = region.getBlock(x, y, z)
      val above = region.getBlock(x, y + 1, z)

      if (isGoodGround(ground) && above.blockData().isEmpty()) {
        return y
      }
    }
    return Int.MIN_VALUE
  }

  private fun isGoodGround(block: BlockSection): Boolean {
    val data = block.blockData()
    if(!data.isSolid()) return false
    return true
    /*if (!material.isSolid) return false
    if (material == Material.BEDROCK) return false
    if (material == Material.WATER || material == Material.LAVA) return false

    return when (material) {
      Material.GRASS_BLOCK,
      Material.DIRT,
      Material.COARSE_DIRT,
      Material.PODZOL,
      Material.ROOTED_DIRT,
      Material.MYCELIUM,
      Material.STONE,
      Material.ANDESITE,
      Material.DIORITE,
      Material.GRANITE,
      Material.TUFF,
      Material.CALCITE,
      Material.GRAVEL,
      Material.SAND,
      Material.RED_SAND,
      Material.DEEPSLATE,
      Material.COBBLED_DEEPSLATE,
      Material.BLACKSTONE,
      Material.BASALT,
      Material.NETHERRACK -> true

      else -> false
    }*/
  }

  private fun isSoftTop(block: BlockSection): Boolean {
    val data = block.blockData()
    return data.isSolid()
    /*return when (material) {
      Material.GRASS_BLOCK,
      Material.DIRT,
      Material.COARSE_DIRT,
      Material.PODZOL,
      Material.ROOTED_DIRT,
      Material.MYCELIUM,
      Material.SAND,
      Material.RED_SAND,
      Material.GRAVEL -> true
      else -> false
    }*/
  }

  private fun mix2D(seed: Long, x: Int, z: Int): Long {
    var h = seed
    h = h xor (x.toLong() * -0x61c8864680b583ebL)
    h = h xor (z.toLong() * 0x9E3779B97F4A715L)
    h = (h xor (h ushr 30)) * -0x40a7b892e31b1a47L
    h = (h xor (h ushr 27)) * -0x6b2fb644ecceee15L
    return h xor (h ushr 31)
  }
}