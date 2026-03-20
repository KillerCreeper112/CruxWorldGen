package killercreepr.cruxworldgen.api.block

import killercreepr.cruxworldgen.api.context.LimitedRegion
import java.util.*

fun interface BlockPicker {
  companion object {
    fun constant(data: BlockData): BlockPicker = Constant(data)
  }

  fun pickBlock(region: LimitedRegion, x: Int, y: Int, z: Int) = pickBlock(region, region.ctx.random, x, y, z)
  fun pickBlock(region: LimitedRegion, rng: Random, x: Int, y: Int, z: Int): BlockData?

  data class Constant(val data: BlockData) : BlockPicker {
    override fun pickBlock(
      region: LimitedRegion,
      rng: Random,
      x: Int,
      y: Int,
      z: Int
    ): BlockData? = data
  }
}