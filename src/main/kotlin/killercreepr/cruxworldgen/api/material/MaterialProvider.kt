package killercreepr.cruxworldgen.api.material

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.MaterialContext

interface MaterialProvider {
  fun chooseMaterial(context: MaterialContext): BlockData
}
