package killercreepr.cruxworldgen.crux.block

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.block.BlockSection

class CruxBlockSection(val blockData: CruxBlockData) : BlockSection {
  override fun blockData(): BlockData = blockData
}