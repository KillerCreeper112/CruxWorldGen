package killercreepr.cruxworldgen.core.block

import killercreepr.crux.api.codec.node.DataArray
import killercreepr.crux.api.codec.node.DataNode
import killercreepr.crux.core.codec.node.StringDataNode
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.block.BlockDataResolver

open class MultiBlockResolver(
  val fallbackResolver : String
) : BlockDataResolver {
  val resolvers = mutableMapOf<String, BlockDataResolver>()
  override fun resolve(node: DataNode) : BlockData {
    val type : String
    val data : DataNode
    if(node.isString){
      val split = node.asString().split(":", limit = 2)
      if(split.size == 1){
        type = fallbackResolver
        data = StringDataNode(node.asString())
      } else{
        type = split[0]
        data = StringDataNode(split[1])
      }
      val resolver = resolvers[type] ?: throw IllegalArgumentException("Unknown BlockData type $type")
      return resolver.resolve(data)
    }
    //todo
    throw IllegalArgumentException("TODO NOT DONE")
  }
}