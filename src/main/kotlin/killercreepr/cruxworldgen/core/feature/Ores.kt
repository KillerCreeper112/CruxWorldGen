package killercreepr.cruxworldgen.core.feature

import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.bukkit.block.BukkitDataBlockData
import org.bukkit.Material

val ORE_FEATURE = OreVeinFeature()

val ironHigh = PlacedFeature(
  feature = ORE_FEATURE,
  cfg = OreConfig(
    ore = BukkitBlockResolver.INSTANCE.resolve(Material.IRON_ORE),
    size = 9,
    canReplace = {
      val data = it.blockData()
      if(data !is BukkitDataBlockData) return@OreConfig false
      return@OreConfig data.data.material.isSolid// data.data.material == Material.STONE
    }
  ),
  modifiers = listOf(
    Repeat(20, XZHeight(TriangleHeight(
      baseHeight = UniformHeightSampler.relative(0.5, 0.9)
    )))
  )
)

val ironLow = PlacedFeature(
  feature = ORE_FEATURE,
  cfg = OreConfig(
    ore = BukkitBlockResolver.INSTANCE.resolve(Material.DEEPSLATE_IRON_ORE),
    size = 9,
    canReplace = {
      val data = it.blockData()
      if(data !is BukkitDataBlockData) return@OreConfig false
      return@OreConfig data.data.material.isSolid//data.data.material == Material.STONE
    }
  ),
  modifiers = listOf(
    Repeat(10, XZHeight(TrapezoidHeight(
      baseHeight = UniformHeightSampler.relative(0.25, 0.75),
      plateau = 24
    )))
  )
)
