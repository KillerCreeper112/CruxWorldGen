package killercreepr.cruxworldgen.api.structure

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.GenerateContext

interface BlockProcessor {
  fun process(ctx: GenerateContext, wx: Int, y: Int, wz: Int, current: BlockData): BlockData
}