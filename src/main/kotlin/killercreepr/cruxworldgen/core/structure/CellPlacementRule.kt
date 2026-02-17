package killercreepr.cruxworldgen.core.structure

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.structure.StructureInstance
import killercreepr.cruxworldgen.api.structure.StructurePlacementRule
import killercreepr.cruxworldgen.api.util.HashUtil
import killercreepr.cruxworldgen.api.util.HashUtil.chooseInt

class CellPlacementRule(
  private val featureId: String,
  private val cellSizeChunks: Int = 4,
  private val chancePerCell: Double = 0.5,
  private val yOffset: Int = 1,
  private val borderPadding: Int = 3
) : StructurePlacementRule {

  override fun pickInstancesForChunk(region: LimitedRegion, chunkX: Int, chunkZ: Int): List<StructureInstance> {
    val out = ArrayList<StructureInstance>()

    val cellSize = cellSizeChunks
    val cellX = Math.floorDiv(chunkX, cellSize)
    val cellZ = Math.floorDiv(chunkZ, cellSize)

    // You only need to check *this* cell in single-anchor-per-cell mode
    // (later for overlap you can check neighbors)
    val ctx = region.ctx
    val baseSeed = ctx.worldContext.seed
    val cellSeed = HashUtil.hash2D(baseSeed, cellX, cellZ) xor featureId.hashCode().toLong()

    val r01 = HashUtil.hash01(cellSeed xor 0x51D11A7L)
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
      SimpleStructureInstance(
        worldX = worldX,
        worldY = worldY,
        worldZ = worldZ,
        rot = rot,
        seed = cellSeed
      )
    )

    return out
  }
}