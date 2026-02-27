package killercreepr.cruxworldgen.crux.block

import killercreepr.crux.api.codec.node.DataNode
import killercreepr.crux.core.Crux
import killercreepr.cruxblocks.core.registries.CruxBlocksRegistries
import killercreepr.cruxworldgen.api.block.BlockDataResolver
import killercreepr.cruxworldgen.bukkit.block.BukkitDataBlockData
import killercreepr.cruxworldgen.bukkit.block.BukkitMaterialBlockData
import org.bukkit.Material
import org.bukkit.block.data.BlockData

open class CruxBlockResolver : BlockDataResolver {
  companion object{
    val INSTANCE = CruxBlockResolver()
  }
  fun resolve(id : Material) = BukkitMaterialBlockData(id)
  fun resolve(id : BlockData) = BukkitDataBlockData(id)

  override fun resolve(node: DataNode): killercreepr.cruxworldgen.api.block.BlockData {
    if(!node.isString) throw IllegalArgumentException("node $node is not a string")
    val id = node.asString()
    val key = Crux.key(id)
    return CruxBlocksRegistries.BLOCK.get(key)?.let(::CruxBlockData)
      ?: CruxBlocksRegistries.BLOCK.getGroup(key)?.let(::CruxBlockGroupData)
      ?: throw IllegalArgumentException("Unknown crux block or crux block group id: '$id' (key=$key)")
  }
}