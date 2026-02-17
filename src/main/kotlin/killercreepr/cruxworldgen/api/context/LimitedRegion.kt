package killercreepr.cruxworldgen.api.context

import killercreepr.cruxworldgen.api.context.terrain.TerrainSnapshot

interface LimitedRegion : RegionAccessor {
  val ctx : GenerateContext
  val bufferX : Int
  val bufferZ : Int
  val minY: Int
  val maxY: Int
  val centerChunkX : Int
    get() = ctx.chunkX
  val centerChunkZ : Int
    get() = ctx.chunkZ
  val terrain : TerrainSnapshot

  fun isInRegion(worldX : Int, worldY : Int, worldZ : Int) : Boolean
  fun isInRegion(worldX : Int, worldZ : Int) : Boolean
}