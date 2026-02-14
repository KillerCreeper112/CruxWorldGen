package killercreepr.cruxworldgen.api.block

import killercreepr.crux.api.codec.node.DataNode

interface BlockDataResolver{
  fun resolve(node : DataNode) : BlockData
}