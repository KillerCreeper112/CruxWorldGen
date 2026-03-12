package killercreepr.cruxworldgen.bukkit.block.picker

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.LimitedRegion
import org.bukkit.Axis

fun interface AxisBlockPicker {
  fun pickBlock(region: LimitedRegion, x: Int, y: Int, z: Int, axis: Axis): BlockData?
}