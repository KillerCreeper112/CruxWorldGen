package killercreepr.cruxworldgen.api.structure

import killercreepr.cruxworldgen.api.block.BlockState
import killercreepr.cruxworldgen.api.context.GenerateContext
import org.bukkit.Material

interface BlockProcessor {
  fun process(ctx: GenerateContext, wx: Int, y: Int, wz: Int, current: Material): BlockState
}