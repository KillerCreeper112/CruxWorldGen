package killercreepr.cruxworldgen.core.feature.ore

import killercreepr.cruxworldgen.api.block.BlockPicker
import killercreepr.cruxworldgen.api.block.CanReplaceBlock

data class OreConfig(
  val ore: BlockPicker,
  val minSize: Int,
  val maxSize: Int,
  val canReplace: CanReplaceBlock,
  val discardChanceOnAirExposure: Double = 0.0, // optional
  val sizeOrder: Int = 0, //negative = biased towards min, greater than 0 = biased towards max, 0 = uniform
)