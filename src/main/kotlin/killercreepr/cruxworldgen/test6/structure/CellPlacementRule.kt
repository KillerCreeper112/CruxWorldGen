package killercreepr.cruxworldgen.test6.structure

import killercreepr.cruxworldgen.test6.context.GenerateContext
import org.bukkit.Bukkit
import kotlin.math.floor

class CellPlacementRule(
  private val featureId: String,
  private val cellSizeChunks: Int = 4,
  private val chancePerCell: Double = 0.5,
  private val yOffset: Int = 1,
  private val borderPadding: Int = 3
) : StructurePlacementRule {

  override fun pickInstancesForChunk(ctx: GenerateContext, chunkX: Int, chunkZ: Int): List<StructureInstance> {
    val out = ArrayList<StructureInstance>()

    val cellSize = cellSizeChunks
    val cellX = floorDiv(chunkX, cellSize)
    val cellZ = floorDiv(chunkZ, cellSize)

    // You only need to check *this* cell in single-anchor-per-cell mode
    // (later for overlap you can check neighbors)
    val baseSeed = ctx.worldContext.seed
    val cellSeed = hash2D(baseSeed, cellX, cellZ) xor featureId.hashCode().toLong()

    val r01 = hash01(cellSeed xor 0x51D11A7L)
    if (r01 > chancePerCell) return out

    val rot = listOf(0, 90, 180, 270)[chooseInt(cellSeed xor 0xABCD, 0, 3)]

    // Pick ONE anchor chunk inside the cell (deterministic)
    val anchorChunkX = cellX * cellSize + chooseInt(cellSeed xor 0x1111, 0, cellSize - 1)
    val anchorChunkZ = cellZ * cellSize + chooseInt(cellSeed xor 0x2222, 0, cellSize - 1)

    // Only place in the chunk that is the chosen anchor chunk
    if (chunkX != anchorChunkX || chunkZ != anchorChunkZ) return out

    // Pick anchor inside that chunk with padding (single-chunk-safe)
    val localX = borderPadding + chooseInt(cellSeed xor 0x3333, 0, 15 - borderPadding * 2)
    val localZ = borderPadding + chooseInt(cellSeed xor 0x4444, 0, 15 - borderPadding * 2)

    val worldX = chunkX * 16 + localX
    val worldZ = chunkZ * 16 + localZ

    val surfaceY = ctx.queries.surfaceY(localX, localZ)
    val worldY = surfaceY + yOffset

    out.add(
      StructureInstance(
        id = featureId,
        worldX = worldX,
        worldY = worldY,
        worldZ = worldZ,
        rot = rot,
        seed = cellSeed
      )
    )

    return out
  }

  private fun floorDiv(a: Int, b: Int): Int = kotlin.math.floor(a.toDouble() / b.toDouble()).toInt()

  private fun hash2D(seed: Long, x: Int, z: Int): Long {
    var v = seed
    v = v xor (x.toLong() * 7145483588892929177L)
    v = v xor (z.toLong() * -7046029254386353131L)
    v = (v xor (v ushr 30)) * -4658895280553007687L
    v = (v xor (v ushr 27)) * -7723592293110705685L
    return v xor (v ushr 31)
  }

  private fun hash01(seed: Long): Double {
    var v = seed
    v = (v xor (v ushr 30)) * -4658895280553007687L
    v = (v xor (v ushr 27)) * -7723592293110705685L
    v = v xor (v ushr 31)
    val positive = v and Long.MAX_VALUE
    return positive.toDouble() / Long.MAX_VALUE.toDouble()
  }

  private fun chooseInt(seed: Long, min: Int, max: Int): Int {
    if (max <= min) return min
    val r = hash01(seed)
    return (min + (r * (max - min + 1)).toInt()).coerceIn(min, max)
  }
}
