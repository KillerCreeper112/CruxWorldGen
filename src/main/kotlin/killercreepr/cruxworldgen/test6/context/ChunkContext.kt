package killercreepr.cruxworldgen.test6.context

import org.bukkit.Material

abstract class ChunkContext(
  val minHeight : Int,
  val maxHeight : Int,
  val seaLevel : Int
) {
  abstract fun setBlock(x : Int, y : Int, z : Int, material : Material)
  abstract fun getBlock(x: Int, y: Int, z: Int): Material

  fun isAir(x: Int, y: Int, z: Int): Boolean = getBlock(x, y, z).isAir
  fun isSolid(x: Int, y: Int, z: Int): Boolean = !getBlock(x, y, z).isAir
}