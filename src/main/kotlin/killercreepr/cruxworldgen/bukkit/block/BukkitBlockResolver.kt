package killercreepr.cruxworldgen.bukkit.block

import killercreepr.crux.api.codec.node.DataNode
import killercreepr.crux.core.Crux
import killercreepr.crux.core.codec.node.StringDataNode
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.block.BlockDataResolver
import net.kyori.adventure.key.Key
import org.bukkit.Material
import org.bukkit.Registry

open class BukkitBlockResolver : BlockDataResolver {
  companion object{
    val INSTANCE = BukkitBlockResolver()
  }

  fun resolve(id : String) = resolve(StringDataNode(id))
  fun resolve(id : Material) = resolve(id.key().asString())

  override fun resolve(node: DataNode): BlockData {
    if(!node.isString) throw IllegalArgumentException("node $node is not a string")
    val id = node.asString()
    return if(id.contains("[")) BukkitDataBlockData(Crux.getServer().createBlockData(id))
    else BukkitMaterialBlockData(Registry.MATERIAL.get(Key.key(id))!!)
  }
}