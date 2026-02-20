package killercreepr.cruxworldgen.test.decor

import killercreepr.cruxblocks.api.block.CruxBlock
import killercreepr.cruxblocks.core.block.component.CruxBlockComponents
import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.decor.PropPoint
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.util.HashUtil.chooseInt
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.bukkit.block.BukkitDataBlockData
import org.bukkit.Axis
import org.bukkit.Material
import org.bukkit.block.BlockType
import java.util.*

class AbyssTreeDecor(
  override val pass: DecorationPass = DecorationPass.SURFACE,

  private val chancePerPoint: Double = 0.18,
  private val minAirAbove: Int = 7,
  private val maxSlope01: Double = 100.0,

  private val minHeight: Int = 4,
  private val maxHeight: Int = 7,
  val minWidth: Int = 3,
  val maxWidth: Int = 5,
  val wartMinHeight : Int = 2,
  val wartMaxHeight : Int = 4
) : Decoration {

  override fun shouldTry(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean {
    // If you later add biome-specific toggles, put them here.
    // For now: deterministic chance gate.
    //val r01 = hash01(point.seed xor TREE_SALT)
    val ctx = region.ctx
    val r01 = CruxNoise.fast(ctx.worldContext.seed.toInt())
      .frequency(0.01)
      .noiseType(CruxNoise.NoiseType.OpenSimplex2)
      .fractalType(CruxNoise.FractalType.FBm)
      .fractalOctaves(1).noise(point.worldX.toDouble(), point.worldZ.toDouble())
    return r01 <= chancePerPoint
  }

  override fun findPlacement(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Placement? {
    //val chunk = ctx.chunkContext

    val worldX = point.worldX
    val worldZ = point.worldZ

    // Must be inside chunk bounds + padding (avoid cross-chunk canopy writes)
    //if (localX !in borderPadding..(15 - borderPadding)) return null
    //if (localZ !in borderPadding..(15 - borderPadding)) return null

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
    val width = chooseInt(point.seed xor 0x22325678L, minWidth, maxWidth)
    //val canopyRadius = 2 + chooseInt(point.seed xor 0x9ABCDEF0L, 0, 1) // 2..3
    return AbyssTreePlacement(
      worldX = worldX,
      worldZ = worldZ,
      baseY = baseY,
      height = height,
      width = width,
      seed = point.seed,
      this
    )
  }

  override fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as AbyssTreePlacement
    val queries = region.terrainQueries
    val bounds = region.regionBounds

    // Trunk
    var topY = 0
    for (dy in 0 until p.height) {
      val y = p.baseY + dy
      if (y < bounds.minY || y > bounds.maxY) break
      if (queries.isEmpty(p.worldX, y, p.worldZ)) {
        region.setBlock(p.worldX, y, p.worldZ, BukkitBlockResolver.INSTANCE.resolve(Material.OAK_LOG))
        topY = y
      }
    }

    val rng = region.ctx.random
    generateBranch(
      region,
      p.worldX + if(rng.nextBoolean()) -1 else 1,
      topY,
      p.worldZ,
      p.width,
      1, 0,
      p.parent
    )
  }
  fun generateBranch(region : LimitedRegion, x : Int, y : Int , z : Int, length : Int, dirX : Int, dirZ : Int,
                     parent : AbyssTreeDecor) {
    val block = fromDirection(dirX, dirZ)
    val queries = region.terrainQueries

    for(i in 0..length){
      val xx = addDirection(x, dirX, i)
      val yy = y
      val zz = addDirection(z, dirZ, i)

      if(!region.isInRegion(xx, yy, zz)) continue
      if(!queries.isEmpty(xx, yy, zz)) continue

      region.setBlock(xx, yy, zz, block)

      if(i == length){
        val height = chooseInt(
          region.ctx.worldContext.seed, parent.wartMinHeight, parent.wartMaxHeight
        )
        generateWart(region,
          addDirection(xx, dirX, 1),
          yy,
          addDirection(zz, dirZ, 1), height)
      }
    }
  }

  fun generateWart(region : LimitedRegion, x : Int, y : Int , z : Int, height : Int){
    val queries = region.terrainQueries
    for(i in 0..height){
      val xx = x
      val yy = y + i
      val zz = z

      if(!region.isInRegion(xx, yy, zz)) continue
      if(!queries.isEmpty(xx, yy, zz)) continue

      region.setBlock(xx, yy, zz, BukkitBlockResolver.INSTANCE.resolve("nether_wart_block"))
    }
  }
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

fun addDirection(value : Int, dir: Int, amount: Int): Int {
  if (dir == 1 || dir == -1) {
    return value + (dir * amount)
  }
  return value
}

data class AbyssTreePlacement(
  val worldX: Int,
  val worldZ: Int,
  val baseY: Int,
  val height: Int,
  val width: Int,
  val seed: Long,
  val parent : AbyssTreeDecor
) : Placement
