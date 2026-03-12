package killercreepr.cruxworldgen.core.feature.ore

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.LimitedRegion
import java.util.*

data class OreConfig(
  val ore: BlockGetter,
  val minSize: Int,
  val maxSize: Int,
  val canReplace: CanReplace,
  val discardChanceOnAirExposure: Double = 0.0, // optional
  val sizeOrder: Int = 0, //negative = biased towards min, greater than 0 = biased towards max, 0 = uniform
){
  fun interface CanReplace{
    fun canReplace(region: LimitedRegion, rng: Random, x: Int, y: Int, z: Int): Boolean
  }

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
}