package killercreepr.cruxworldgen.api.context

import killercreepr.cruxworldgen.api.context.terrain.RegionBounds
import killercreepr.cruxworldgen.api.context.terrain.TerrainSnapshot

interface LimitedRegion : RegionAccessor {
  val ctx : GenerateContext
  val bufferX : Int
  val bufferZ : Int
  val centerBounds : RegionBounds
  val regionBounds : RegionBounds

  /*val centerChunkX : Int
    get() = ctx.chunkX
  val centerChunkZ : Int
    get() = ctx.chunkZ*/
  val terrainSnapshot : TerrainSnapshot
  val terrainQueries : TerrainQueries

  fun canRead(worldX : Int, worldY : Int, worldZ : Int) : Boolean
  fun canWrite(worldX : Int, worldY : Int, worldZ : Int) : Boolean

  fun isInRegion(worldX : Int, worldY : Int, worldZ : Int) : Boolean
  fun isInRegion(worldX : Int, worldZ : Int) : Boolean

  fun isLocalInRegion(localX : Int, localZ : Int) : Boolean = isInRegion(ctx.toWorldX(localX), ctx.toWorldZ(localZ))
}