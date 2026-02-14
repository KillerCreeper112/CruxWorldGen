package killercreepr.cruxworldgen.api.context

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.block.BlockSection

interface ChunkContext{
  val minHeight : Int
  val maxHeight : Int
  val width : Int
  val depth : Int
  val seaLevel : Int

  fun setBlock(x : Int, y : Int, z : Int, data : BlockData)
  fun getBlock(x: Int, y: Int, z: Int): BlockSection

  fun isEmpty(x: Int, y: Int, z: Int): Boolean
  fun isSolid(x: Int, y: Int, z: Int): Boolean
  fun isLiquid(x : Int, y : Int, z : Int): Boolean
}