package killercreepr.cruxworldgen.test.decor

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.decor.PropPoint
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.util.HashUtil.chance
import killercreepr.cruxworldgen.api.util.HashUtil.chooseInt
import killercreepr.cruxworldgen.api.util.HashUtil.mixSeed
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.bukkit.block.BukkitDataBlockData
import org.bukkit.Axis
import org.bukkit.Material
import org.bukkit.block.BlockType

class AbyssTreeDecor(
  override val pass: DecorationPass = DecorationPass.SURFACE,

  val chancePerPoint: Double = 0.18,
  val minAirAbove: Int = 7,
  val maxSlope01: Double = 100.0,

  val minHeight: Int = 4,
  val maxHeight: Int = 9,
  val minBranchLength: Int = 1,
  val maxBranchLength: Int = 4,
  val wartMinHeight : Int = 1,
  val wartMaxHeight : Int = 4,
  val maxBranches : Int = 4,
  val maxBranchesOnBranches : Int = 3,
) : Decoration {

  override fun shouldTry(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean {
    val s = mixSeed(
      seed = region.ctx.worldContext.seed,
      x = point.worldX, y = 0, z = point.worldZ,
      salt = 1L
    )
    return chance(s, chancePerPoint)
  }

  override fun findPlacement(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Placement? {
    val worldX = point.worldX
    val worldZ = point.worldZ

    val terrain2D = region.terrainSnapshot.terrain2D

    val queries = region.terrainQueries
    val surfaceY = terrain2D.surfaceY(worldX, worldZ)
    val baseY = surfaceY + 1
    if(!region.isInRegion(worldX, baseY, worldZ)) return null
    if(!queries.isSolid(worldX, surfaceY, worldZ)) return null

    if(terrain2D.isOceanColumn(worldX, worldZ)) return null

    if (queries.slope01(worldX, worldZ) > maxSlope01) return null

    val airAbove = queries.airBlocksAbove(worldX, surfaceY, worldZ, maxCount = minAirAbove)
    if (airAbove < minAirAbove) return null

    val height = chooseInt(point.seed xor 0x12345678L, minHeight, maxHeight)
    return AbyssTreePlacement(
      worldX = worldX,
      worldZ = worldZ,
      baseY = baseY,
      height = height,
      seed = point.seed,
      this
    )
  }

  override fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample) {
    AbyssTreePlacer(this, region, placement as AbyssTreePlacement, biomeBlend).place()
  }
  fun randomBranchLength(seed : Long) = chooseInt(seed, minBranchLength, maxBranchLength)
  fun randomWartHeight(seed : Long) = chooseInt(seed, wartMinHeight, wartMaxHeight)
}

fun fromDirection(xDir: Int, zDir: Int): BlockData {
  if (xDir == 1 || xDir == -1) {
    return BukkitDataBlockData(BlockType.OAK_LOG.createBlockData{ c -> c.axis = Axis.X })
  }
  if (zDir == 1 || zDir == -1) {
    return BukkitDataBlockData(BlockType.OAK_LOG.createBlockData{ c -> c.axis = Axis.Z })
  }
  return BukkitDataBlockData(BlockType.OAK_LOG.createBlockData{ c -> c.axis = Axis.Y })
}

data class AbyssTreePlacement(
  val worldX: Int,
  val worldZ: Int,
  val baseY: Int,
  val height: Int,
  val seed: Long,
  val parent : AbyssTreeDecor
) : Placement

class AbyssTreePlacer(
  val cfg : AbyssTreeDecor,
  val region: LimitedRegion,
  val p: AbyssTreePlacement,
  val biomeBlend: BiomeBlendSample
) {
  val branchDirections = listOf(
    1 to 0,
    -1 to 0,
    0 to 1,
    0 to -1
  )

  fun place() {
    val queries = region.terrainQueries
    val bounds = region.regionBounds

    val seed = region.ctx.worldContext.seed
    var placedBranches = 0
    for (dy in 0 until p.height) {
      val y = p.baseY + dy
      if (y < bounds.minY || y > bounds.maxY) break
      if (queries.isReplaceable(p.worldX, y, p.worldZ)) {
        region.setBlock(p.worldX, y, p.worldZ, BukkitBlockResolver.INSTANCE.resolve(Material.OAK_LOG))

        if (dy == p.height - 1) {
          val wartHeight = cfg.randomWartHeight(seed xor 0x32382)

          generateWart(
            region,
            p.worldX, y + 1, p.worldZ,
            wartHeight
          )
          break
        }

        if (dy > 2 && placedBranches < cfg.maxBranches) {

          for (dir in branchDirections) {
            if (region.ctx.random.nextBoolean()) continue

            BranchPlacer(
              cfg, region, p, biomeBlend,
              p.worldX + dir.first, y, p.worldZ + dir.second,
              dir.first, 0, dir.second, 0
            ).place()

            placedBranches++
          }
        }
      } else break
    }
  }

  fun generateWart(region: LimitedRegion, x: Int, y: Int, z: Int, height: Int) {
    val queries = region.terrainQueries
    for (i in 0..height) {
      val xx = x
      val yy = y + i
      val zz = z

      if (!region.isInRegion(xx, yy, zz)) break
      if (!queries.isReplaceable(xx, yy, zz)) break

      region.setBlock(xx, yy, zz, BukkitBlockResolver.INSTANCE.resolve("nether_wart_block"))
    }
  }

  class BranchPlacer(
    val cfg: AbyssTreeDecor,
    val region: LimitedRegion,
    val p: AbyssTreePlacement,
    val biomeBlend: BiomeBlendSample,
    val x: Int,
    val y: Int,
    val z: Int,
    val dirX: Int,
    val dirY: Int,
    val dirZ: Int,
    val subBranchID: Short
  ) {
    private val branchDirections = listOf(
      Triple(1, 0, 0),
      Triple(-1, 0, 0),
      Triple(0, 0, 1),
      Triple(0, 0, -1),
      Triple(0, 1, 0)
    )

    fun place() {
      val queries = region.terrainQueries

      // Branch seed tied to the *tree* and this branch’s start/direction
      val branchSeed = mixSeed(
        seed = p.seed,
        x = x, y = y, z = z,
        dx = dirX, dy = dirY, dz = dirZ,
        salt = 1L
      )

      val length = cfg.randomBranchLength(branchSeed xor 0x1111)
      val block = fromDirection(dirX, dirZ)

      var spawnedSubBranches = 0
      val maxSub = cfg.maxBranchesOnBranches

      // Optional: small chance this branch is shorter (adds irregularity)
      val effectiveLength = if (chance(branchSeed xor 0x2222, 0.18)) {
        (length - 1).coerceAtLeast(1)
      } else length

      for (i in 0..effectiveLength) {
        val xx = x + dirX * i
        val yy = y + dirY * i
        val zz = z + dirZ * i

        if (!region.isInRegion(xx, yy, zz)) break
        if (!queries.isReplaceable(xx, yy, zz)) break

        region.setBlock(xx, yy, zz, block)

        if (i == effectiveLength) {
          val wartHeight = cfg.randomWartHeight(branchSeed xor 0x3333)

          generateWart(
            region,
            xx + dirX,
            yy + dirY,
            zz + dirZ,
            wartHeight
          )
          break
        }

        if (subBranchID > 2) break
        if (i > 0 && spawnedSubBranches < maxSub) {
          for ((sdx, sdy, sdz) in branchDirections) {
            if (spawnedSubBranches >= maxSub) break

            // Bias vertical sub-branches lower than horizontals
            val baseP = if (sdy != 0) 0.18 else 0.35
            val dirHash = (sdx * 31 + sdy * 17 + sdz * 13).toLong()

            if (!chance(branchSeed xor (i.toLong() * 0x9E37L) xor dirHash, baseP)) continue

            BranchPlacer(
              cfg, region, p, biomeBlend,
              x = xx + sdx,
              y = yy + sdy,
              z = zz + sdz,
              dirX = sdx,
              dirY = sdy,
              dirZ = sdz,
              subBranchID = (subBranchID + 1).toShort()
            ).place()

            spawnedSubBranches++
          }
        }
      }
    }


    fun generateWart(region: LimitedRegion, x: Int, y: Int, z: Int, height: Int) {
      val queries = region.terrainQueries
      for (i in 0..height) {
        val xx = x
        val yy = y + i
        val zz = z

        if (!region.isInRegion(xx, yy, zz)) break
        if (!queries.isReplaceable(xx, yy, zz)) break

        region.setBlock(xx, yy, zz, BukkitBlockResolver.INSTANCE.resolve("nether_wart_block"))
      }
    }
  }
}