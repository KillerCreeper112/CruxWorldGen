package killercreepr.cruxworldgen.api.block

import killercreepr.cruxworldgen.api.context.LimitedRegion
import java.util.Random

fun interface CanReplaceBlock{
  companion object{
    val SOLID: CanReplaceBlock = Solid()
    val EMPTY: CanReplaceBlock = Empty()
  }
  fun canReplace(region: LimitedRegion, rng: Random, x: Int, y: Int, z: Int): Boolean

  class Solid : CanReplaceBlock{
    override fun canReplace(
      region: LimitedRegion,
      rng: Random,
      x: Int,
      y: Int,
      z: Int
    ): Boolean = region.terrainQueries.isSolid(x, y, z)
  }
  class Empty : CanReplaceBlock{
    override fun canReplace(
      region: LimitedRegion,
      rng: Random,
      x: Int,
      y: Int,
      z: Int
    ): Boolean = region.terrainQueries.isEmpty(x, y, z)
  }
}