package killercreepr.cruxworldgen.api.material

import killercreepr.cruxworldgen.api.block.BlockState
import killercreepr.cruxworldgen.api.context.MaterialContext

interface MaterialProvider {
  fun chooseMaterial(context: MaterialContext): BlockState
}
