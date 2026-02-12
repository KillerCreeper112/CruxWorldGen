package killercreepr.cruxworldgen.test6.context

import org.bukkit.Material

abstract class ChunkContext(
  val minHeight : Int,
  val maxHeight : Int,
  val seaLevel : Int
) {
  abstract fun setBlock(x : Int, y : Int, z : Int, material : Material)
}