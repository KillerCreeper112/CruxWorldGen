package killercreepr.cruxworldgen.bukkit.block.picker

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.block.BlockPicker
import killercreepr.cruxworldgen.api.context.LimitedRegion
import org.bukkit.Axis
import java.util.Random

fun interface AxisBlockPicker: BlockPicker {
  fun pickBlock(region: LimitedRegion, x: Int, y: Int, z: Int, axis: Axis): BlockData? = pickBlock(region, region.ctx.random, x, y, z, axis)
  fun pickBlock(region: LimitedRegion, rng: Random, x: Int, y: Int, z: Int, axis: Axis): BlockData?
  override fun pickBlock(region: LimitedRegion, rng: Random, x: Int, y: Int, z: Int): BlockData? = pickBlock(region, rng,x, y, z, Axis.Y)
}