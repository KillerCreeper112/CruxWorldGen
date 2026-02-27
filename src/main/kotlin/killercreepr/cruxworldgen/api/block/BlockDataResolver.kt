package killercreepr.cruxworldgen.api.block

import killercreepr.crux.api.codec.node.DataNode
import killercreepr.crux.core.codec.node.StringDataNode

interface BlockDataResolver{
  fun resolve(node : DataNode) : BlockData
  fun resolve(id : String) = resolve(StringDataNode(id))
}

interface MultiBlockDataResolver : BlockDataResolver{
  fun registerResolve(id : String, resolver : BlockDataResolver)
}