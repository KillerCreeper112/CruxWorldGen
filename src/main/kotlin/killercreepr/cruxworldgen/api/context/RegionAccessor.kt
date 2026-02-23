package killercreepr.cruxworldgen.api.context

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.block.BlockSection

interface RegionAccessor {
  fun setBlock(x : Int, y : Int, z : Int, block : BlockData)
  fun getBlock(x : Int, y : Int, z : Int) : BlockSection

  fun getBiome(x : Int, y : Int, z : Int) : Biome?
  fun setBiome(x : Int, y : Int, z : Int, biome : Biome)

  //fun getSurfaceY(worldX : Int, worldZ : Int) : Int
}