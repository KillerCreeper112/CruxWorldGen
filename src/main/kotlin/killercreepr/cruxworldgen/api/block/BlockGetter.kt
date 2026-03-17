package killercreepr.cruxworldgen.api.block

import killercreepr.cruxworldgen.api.context.LimitedRegion
import java.util.Random

fun interface BlockGetter {
    companion object{
      fun constant(data: BlockData): BlockGetter = Constant(data)
    }

    fun getBlock(region: LimitedRegion, rng: Random, x: Int, y: Int, z: Int): BlockData?

    data class Constant(val data: BlockData) : BlockGetter{
      override fun getBlock(
        region: LimitedRegion,
        rng: Random,
        x: Int,
        y: Int,
        z: Int
      ): BlockData? = data
    }
  }