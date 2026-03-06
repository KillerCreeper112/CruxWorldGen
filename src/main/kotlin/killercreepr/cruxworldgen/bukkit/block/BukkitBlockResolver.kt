package killercreepr.cruxworldgen.bukkit.block

import killercreepr.crux.api.codec.node.DataNode
import killercreepr.crux.core.Crux
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.block.BlockDataResolver
import net.kyori.adventure.key.Key
import org.bukkit.Material
import org.bukkit.Registry

open class BukkitBlockResolver : BlockDataResolver {
  companion object{
    val INSTANCE = BukkitBlockResolver()
  }
  fun resolve(id : Material) = BukkitMaterialBlockData(id)
  fun resolve(id : org.bukkit.block.data.BlockData) = BukkitDataBlockData(id)

  override fun resolve(node: DataNode): BlockData {
    if(!node.isString) throw IllegalArgumentException("node $node is not a string")
    val id = node.asString()
    return if(id.contains("[")) BukkitDataBlockData(Crux.getServer().createBlockData(id))
    else BukkitMaterialBlockData(Registry.MATERIAL.get(Key.key(id))!!)
  }
}