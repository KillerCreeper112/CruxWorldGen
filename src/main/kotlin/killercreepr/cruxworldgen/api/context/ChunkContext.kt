package killercreepr.cruxworldgen.api.context

import killercreepr.cruxworldgen.api.block.BlockState

interface ChunkContext{
  val minHeight : Int
  val maxHeight : Int
  val width : Int
  val depth : Int
  val seaLevel : Int

  fun setBlock(x : Int, y : Int, z : Int, state : BlockState)
  fun getBlock(x: Int, y: Int, z: Int): BlockState

  fun isAir(x: Int, y: Int, z: Int): Boolean
  fun isSolid(x: Int, y: Int, z: Int): Boolean
  fun isLiquid(x : Int, y : Int, z : Int): Boolean
}