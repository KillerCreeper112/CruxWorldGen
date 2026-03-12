package killercreepr.cruxworldgen.core.feature

import killercreepr.cruxworldgen.api.feature.*
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.bukkit.block.BukkitDataBlockData
import killercreepr.cruxworldgen.core.feature.ore.OreConfig
import org.bukkit.Material

val ironHigh = PlacedFeature(
  feature = CoreFeatures.ORE_VEIN,
  cfg = OreConfig(
    ore = OreConfig.BlockGetter.constant(BukkitBlockResolver.INSTANCE.resolve(Material.IRON_ORE)),
    minSize = 5,
    maxSize = 9,
    canReplace = { region, rng, x, y, z ->
      val block = region.getBlock(x, y, z)
      val data = block.blockData()
      if(data !is BukkitDataBlockData) return@OreConfig false
      return@OreConfig data.data.material.isSolid
    }
  ),
  modifiers = listOf(
    Repeat(20, XZHeight(
      TriangleHeight(
        baseHeight = UniformHeightSampler.relative(0.5, 0.9)
      )
    )
    )
  )
)

val ironLow = PlacedFeature(
  feature = CoreFeatures.ORE_VEIN,
  cfg = OreConfig(
    ore = OreConfig.BlockGetter.constant(BukkitBlockResolver.INSTANCE.resolve(Material.DEEPSLATE_IRON_ORE)),
    minSize = 5,
    maxSize = 9,
    canReplace = {region, rng, x, y, z ->
      val block = region.getBlock(x, y, z)
      val data = block.blockData()
      if(data !is BukkitDataBlockData) return@OreConfig false
      return@OreConfig data.data.material.isSolid
    }
  ),
  modifiers = listOf(
    Repeat(10, XZHeight(
      TrapezoidHeight(
        baseHeight = UniformHeightSampler.relative(0.25, 0.75),
        plateau = 24
      )
    )
    )
  )
)


val diamondSkyIslands = PlacedFeature(
  feature = CoreFeatures.ORE_VEIN,
  cfg = OreConfig(
    ore = OreConfig.BlockGetter.constant(BukkitBlockResolver.INSTANCE.resolve(Material.DEEPSLATE_DIAMOND_ORE)),
    minSize = 5,
    maxSize = 9,
    canReplace = {region, rng, x, y, z ->
      val block = region.getBlock(x, y, z)
      val data = block.blockData()
      if(data !is BukkitDataBlockData) return@OreConfig false
      return@OreConfig data.data.material.isSolid
    }
  ),
  modifiers = listOf(
    Repeat(100, XZHeight(
      TriangleHeight(
        baseHeight = UniformHeightSampler.relative(0.5, 0.8)
      )
    )
    )
  )
)
