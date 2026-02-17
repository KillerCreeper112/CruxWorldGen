package killercreepr.cruxworldgen.bukkit.region

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.block.BlockSection
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.context.terrain.TerrainSnapshot

class SimpleLimitedRegion(
  override val ctx: GenerateContext,
  override val bufferX: Int,
  override val bufferZ: Int,
  override val minY: Int,
  override val maxY: Int,
  override val terrainSnapshot : TerrainSnapshot
) : LimitedRegion {
  private val width get() = ctx.chunkContext.width
  private val depth get() = ctx.chunkContext.depth

  // --- Chunk world bounds ---
  private val chunkMinX = ctx.chunkX * width
  private val chunkMinZ = ctx.chunkZ * depth
  private val chunkMaxX = chunkMinX + width - 1
  private val chunkMaxZ = chunkMinZ + depth - 1

  private val chunkMinY = ctx.chunkContext.minHeight
  private val chunkMaxY = ctx.chunkContext.maxHeight - 1

  // --- Buffered region world bounds (guard only) ---
  private val regionMinX = chunkMinX - bufferX
  private val regionMinZ = chunkMinZ - bufferZ
  private val regionMaxX = chunkMaxX + bufferX
  private val regionMaxZ = chunkMaxZ + bufferZ

  private val regionMinY = minY
  private val regionMaxY = maxY

  // --- helpers ---
  private fun isInThisChunkXZ(worldX: Int, worldZ: Int): Boolean =
    worldX in chunkMinX..chunkMaxX && worldZ in chunkMinZ..chunkMaxZ

  private fun isInThisChunkXYZ(worldX: Int, worldY: Int, worldZ: Int): Boolean =
    isInThisChunkXZ(worldX, worldZ) && worldY in chunkMinY..chunkMaxY

  private fun localX(worldX: Int) = worldX - chunkMinX
  private fun localZ(worldZ: Int) = worldZ - chunkMinZ

  private fun requireInThisChunk(worldX: Int, worldY: Int, worldZ: Int) {
    require(isInThisChunkXYZ(worldX, worldY, worldZ)) {
      "($worldX,$worldY,$worldZ) is outside current chunk bounds " +
        "X[$chunkMinX..$chunkMaxX], Y[$chunkMinY..$chunkMaxY], Z[$chunkMinZ..$chunkMaxZ]. " +
        "SimpleLimitedRegion is backed only by ChunkData; it cannot read/write outside the chunk."
    }
  }

  // --- LimitedRegion ---
  override fun isInRegion(worldX: Int, worldY: Int, worldZ: Int): Boolean {
    return (worldX in regionMinX..regionMaxX) &&
      (worldZ in regionMinZ..regionMaxZ) &&
      (worldY in regionMinY..regionMaxY)
  }

  override fun isInRegion(worldX: Int, worldZ: Int): Boolean {
    return (worldX in regionMinX..regionMaxX) && (worldZ in regionMinZ..regionMaxZ)
  }

  override fun setBlock(x: Int, y: Int, z: Int, block: BlockData) {
    requireInThisChunk(x, y, z)
    ctx.chunkContext.setBlock(localX(x), y, localZ(z), block)
  }

  override fun getBlock(x: Int, y: Int, z: Int): BlockSection {
    requireInThisChunk(x, y, z)
    return ctx.chunkContext.getBlock(localX(x), y, localZ(z))
  }
}