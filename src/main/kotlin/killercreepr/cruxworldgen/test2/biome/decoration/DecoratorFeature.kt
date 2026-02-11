package killercreepr.cruxworldgen.test2.biome.decoration

import killercreepr.cruxworldgen.test2.VolumetricBiome
import org.bukkit.Material
import java.util.*

interface DecoratorFeature {
  /**
   * originChunkX/Z: chunk where this feature's RNG and candidate selection originate.
   * targetChunkX/Z: chunk currently being generated; only place blocks that fall inside this chunk.
   */
  fun placeFromOrigin(
    originChunkX: Int,
    originChunkZ: Int,
    targetChunkX: Int,
    targetChunkZ: Int,
    context: DecorationContext
  )
}

data class DecorationContext(
  val worldSeed: Long,
  val originChunkX: Int,
  val originChunkZ: Int,
  val targetChunkX: Int,
  val targetChunkZ: Int,
  val heightmap: IntArray,
  val topBlock: Array<Material>,
  val rng: Random,
  val placeBlock: (wx:Int, wy:Int, wz:Int, mat:Material) -> Unit,
  val getBlock: (wx:Int, wy:Int, wz:Int) -> Material,
  val canReplace: (wx:Int, wy:Int, wz:Int) -> Boolean,
  val biomeAt: (wx:Int, wy:Int, wz:Int) -> VolumetricBiome,
  val originHeightAt: (wx:Int, wz:Int) -> Int
)
