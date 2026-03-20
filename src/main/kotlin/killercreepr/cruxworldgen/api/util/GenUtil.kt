package killercreepr.cruxworldgen.api.util

import killercreepr.cruxworldgen.api.block.BlockPicker
import killercreepr.cruxworldgen.api.context.LimitedRegion
import java.util.Random

object GenUtil {
  fun placeTillGround(
    region: LimitedRegion,
    rng: Random,
    x: Int,
    y: Int,
    z: Int,
    block: BlockPicker,
    max: Int = 5
  ): Int{
    for(i in 0..max){
      fun previousY() = y - (i+1).coerceAtLeast(0)

      val yy = y - i
      if(!region.isInRegion(x, yy, z)) return previousY()

      if(!region.terrainQueries.isEmpty(x, yy, z)) return previousY()

      val block = block.pickBlock(region, rng, x, yy, z) ?: return previousY()
      region.setBlock(x, yy, z, block)
    }
    return y - max
  }
}