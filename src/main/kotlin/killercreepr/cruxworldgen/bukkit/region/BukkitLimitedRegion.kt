package killercreepr.cruxworldgen.bukkit.region

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.block.BlockSection
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.context.terrain.RegionBounds
import killercreepr.cruxworldgen.api.context.terrain.TerrainSnapshot
import killercreepr.cruxworldgen.bukkit.BukkitAdaptor
import killercreepr.cruxworldgen.bukkit.biome.BukkitBiome
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockAdapter
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockData
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockSection
import killercreepr.cruxworldgen.bukkit.block.BukkitDataBlockData
import killercreepr.cruxworldgen.bukkit.context.BukkitTerrainQueries

class BukkitLimitedRegion(
  override val ctx: GenerateContext,
  val region : org.bukkit.generator.LimitedRegion,
  override val bufferX: Int,
  override val bufferZ: Int,
  minY: Int,
  maxY: Int,
  override val terrainSnapshot : TerrainSnapshot
) : LimitedRegion {
  override val terrainQueries = BukkitTerrainQueries(this)
  private val width get() = ctx.chunkContext.width
  private val depth get() = ctx.chunkContext.depth

  // --- Chunk world bounds ---
  private val chunkMinX = ctx.chunkX * width
  private val chunkMinZ = ctx.chunkZ * depth
  private val chunkMaxX = chunkMinX + width - 1
  private val chunkMaxZ = chunkMinZ + depth - 1

  override val regionBounds = RegionBounds(
    minX = chunkMinX - bufferX,
    maxX = chunkMaxX + bufferX,
    minY = minY,
    maxY = maxY,
    minZ = chunkMinZ - bufferZ,
    maxZ = chunkMaxZ + bufferZ
  )

  override val centerBounds = RegionBounds(
    chunkMinX,
    chunkMaxX,
    minY, maxY,
    chunkMinZ,
    chunkMaxZ
  )

  override fun canRead(worldX: Int, worldY: Int, worldZ: Int) = regionBounds.contains(worldX, worldY, worldZ)
  override fun canWrite(worldX: Int, worldY: Int, worldZ: Int) = regionBounds.contains(worldX, worldY, worldZ)
  override fun isInRegion(worldX: Int, worldY: Int, worldZ: Int): Boolean = regionBounds.contains(worldX, worldY, worldZ)

  override fun isInRegion(worldX: Int, worldZ: Int): Boolean = regionBounds.contains(worldX, worldZ)

  // --- helpers ---
  fun requireRead(x: Int, y: Int, z: Int) {
    require(canRead(x, y, z)) { "($x,$y,$z) outside READ bounds: $regionBounds" }
  }

  fun requireWrite(x: Int, y: Int, z: Int) {
    require(canWrite(x, y, z)) { "($x,$y,$z) outside WRITE bounds: $regionBounds" }
  }

  override fun setBlock(x: Int, y: Int, z: Int, block: BlockData) {
    requireWrite(x, y, z)
    if(block !is BukkitBlockData) throw IllegalArgumentException("State must be of type BukkitBlockData")
    block.setAt(region, x,y,z)
  }

  override fun getBlock(x: Int, y: Int, z: Int): BlockSection {
    requireRead(x, y, z)
    return BukkitBlockAdapter.reader().readBlock(this, x,y,z)
  }

  override fun getBiome(x: Int, y: Int, z: Int): Biome? {
    val biome = region.getBiome(x,y,z)
    return BukkitAdaptor.fromBukkit(biome)
  }

  override fun setBiome(x: Int, y: Int, z: Int, biome: Biome) {
    if(biome !is BukkitBiome) throw IllegalArgumentException("State must be of type BukkitBiome")
    region.setBiome(x,y,z, biome.toBukkitBiome())
  }
}