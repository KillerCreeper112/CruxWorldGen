package killercreepr.cruxworldgen.core.block

import killercreepr.crux.api.codec.node.DataNode
import killercreepr.crux.core.codec.node.StringDataNode
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.block.BlockDataResolver
import killercreepr.cruxworldgen.api.block.MultiBlockDataResolver

open class MultiBlockResolver(
  val fallbackResolver : String
) : MultiBlockDataResolver {
  val resolvers = mutableMapOf<String, BlockDataResolver>()

  private val cache = object : LinkedHashMap<String, BlockData>(512, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BlockData>) =
      size > 4096
  }

  override fun resolve(id: String): BlockData {
    return cache[id] ?: run {
      val data = resolveUncached(id)
      cache[id] = data
      data
    }
  }

  private fun resolveUncached(node: String): BlockData {
    val colon = node.indexOf(':')
    val type: String
    val payload: String

    if (colon < 0) {
      type = fallbackResolver
      payload = node
    } else {
      type = node.substring(0, colon)
      payload = node.substring(colon + 1)
    }

    val resolver = resolvers[type] ?: throw IllegalArgumentException("Unknown BlockData type $type")
    return resolver.resolve(StringDataNode(payload))
  }

  override fun resolve(node: DataNode) : BlockData {
    if(node.isString) return resolve(node.asString())
    //todo
    throw IllegalArgumentException("TODO NOT DONE - NO SUPORT FOR NON-STRINGS YET")
  }

  override fun registerResolve(id: String, resolver: BlockDataResolver){
    resolvers[id] = resolver
  }
}