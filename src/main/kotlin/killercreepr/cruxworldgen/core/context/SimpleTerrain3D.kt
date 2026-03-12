package killercreepr.cruxworldgen.core.context

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.terrain.Terrain3D
import killercreepr.cruxworldgen.api.util.MathUtil.blockIndex
import java.util.*

class SimpleTerrain3D(
  val ctx : GenerateContext,
  val caveAirByBlock: BitSet
) : Terrain3D {
  override fun localIsCaveAir(localX: Int, worldY: Int, localZ: Int) : Boolean{
    return caveAirByBlock[blockIndex(
      localX, localZ,
      worldY,
      ctx.chunkContext.minHeight, ctx.chunkContext.width, ctx.chunkContext.depth
    )]
  }

  override fun isCaveAir(worldX: Int, worldY: Int, worldZ: Int): Boolean {
    return caveAirByBlock[blockIndex(
      ctx.toLocalXIfInChunk(worldX)?: error("WorldX $worldX is not local in ${ctx.chunkX}, ${ctx.chunkZ}"),
      ctx.toLocalXIfInChunk(worldX)?: error("WorldZ $worldZ is not local in ${ctx.chunkX}, ${ctx.chunkZ}"),
      worldY,
      ctx.chunkContext.minHeight, ctx.chunkContext.width, ctx.chunkContext.depth
    )]
  }
}