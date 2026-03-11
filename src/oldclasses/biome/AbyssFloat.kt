package killercreepr.cruxworldgen.test.biome

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.biome.BiomeShape
import killercreepr.cruxworldgen.api.biome.BiomeShapeProfile
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.cave.CaveProfile
import killercreepr.cruxworldgen.api.cave.CaveShape
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.MaterialContext
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.material.MaterialProvider
import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.noise.NoiseField
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.noise.NoiseModule
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.NoiseShaper
import killercreepr.cruxworldgen.bukkit.biome.BukkitBiome
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.core.feature.PlacedFeature
import org.bukkit.Material

/**
 * Amplified-like terrain: extreme peaks + deep valleys, still a column-fill heightfield.
 * Add your cave/ravine carvers separately for true AMPLIFIED mega-arches.
 */
class AbyssFloat(
  override val caves: CaveShape = CaveProfile(
    listOf(
    )
  ),
  override val decorations: List<Decoration> = listOf(
  ),
  override val features: List<PlacedFeature<*>> = listOf(
  ),
  override val materialProvider: MaterialProvider = object : MaterialProvider {
    override fun chooseMaterial(ctx: MaterialContext): BlockData {
      if (ctx.depthFromSeaFloor >= 0) {
        if (ctx.depthFromSeaFloor < 3) {
          return BukkitBlockResolver.INSTANCE.resolve(Material.RED_SAND)
        }
      }
      //if(ctx.isUnderwater) return BukkitBlockResolver.INSTANCE.resolve(Material.WATER)
      if (!ctx.isSolid) return BlockData.NONE
      val x = ctx.worldX
      val y = ctx.y
      val z = ctx.worldZ

      val depth = ctx.depthBelowSurface
      if (depth == 0) {
        return BukkitBlockResolver.INSTANCE.resolve(Material.MYCELIUM)
      }
      if (depth < 4) {
        return BukkitBlockResolver.INSTANCE.resolve(Material.COARSE_DIRT)
      }
      return BukkitBlockResolver.INSTANCE.resolve(Material.DEEPSLATE)
    }
  },

  private val baseYAboveSea: Double = 40.0,

  private val continentAmp: Double = 1.0,
  private val hillsAmp: Double = 18.0,
  private val ridgesAmp: Double = 3.0,
  private val valleysAmp: Double = 5.0,
  private val detailAmp: Double = 8.0,

  private val islandAmp: Double = 250.0,
  private val islandCarveAmp: Double = 80.0,
) : Biome.Noised, BukkitBiome {
  override fun toBukkitBiome(): org.bukkit.block.Biome = org.bukkit.block.Biome.END_HIGHLANDS

  val shaper = NoiseShaper.dummy() /*NoiseShaper(
    listOf(
      NoiseShaper.Point(-1.0, NoiseShaper.ShapingFunction.VALLEY),
      NoiseShaper.Point(-0.55, NoiseShaper.ShapingFunction.VALLEY),
      NoiseShaper.Point(-0.20, NoiseShaper.ShapingFunction.FLAT),
      NoiseShaper.Point( 0.35, NoiseShaper.ShapingFunction.FLAT),
      NoiseShaper.Point( 0.65, NoiseShaper.ShapingFunction.HILLS),
      NoiseShaper.Point( 0.82, NoiseShaper.ShapingFunction.HILLS),
      NoiseShaper.Point( 0.92, NoiseShaper.ShapingFunction.FLAT),
      NoiseShaper.Point( 1.0,  NoiseShaper.ShapingFunction.FLAT)
    )
  )*/

  override val shape = BiomeShapeProfile(
    object : BiomeShape {
      override fun density(
        ctx: GenerateContext,
        worldX: Int,
        y: Int,
        worldZ: Int,
        edge: BiomeEdgeContext,
        signalWriter: SignalWriter
      ): DensityStack {

        val sea = ctx.chunkContext.seaLevel.toDouble()
        val baseSurface = sea + baseYAboveSea

        val wx = worldX.toDouble()
        val wy = y.toDouble()
        val wz = worldZ.toDouble()

        val xw = wx
        val zw = wz

        val contN = shaper.shape(ctx.noise.get(Noise.Continent2D).noise2D(xw, zw)) * continentAmp
        val hillsN = shaper.shape(ctx.noise.get(Noise.Hills2D).noise2D(xw, zw)) * hillsAmp
        val ridgesN = shaper.shape(ctx.noise.get(Noise.Ridges2D).noise2D(xw, zw)) * ridgesAmp
        val valleysN = shaper.shape(ctx.noise.get(Noise.Valleys2D).noise2D(xw, zw)) * valleysAmp
        val detailN = ctx.noise.get(Noise.Detail2D).noise2D(wx, wz) * detailAmp

        val surfaceY = baseSurface + contN + hillsN + ridgesN + valleysN + detailN

        val baseDensity = surfaceY - y

        val islandN = ctx.noise.get(Noise.Island3D).noise3D(wx * 0.8, wy * 0.7, wz * 0.8)

        val island = if(baseDensity < 0){
          (islandN * islandAmp)
        }else 0.0

        return DensityStack.densityStack(
          base = baseDensity,
          add = island,
          carve = 0.0
        )
      }
    },
    listOf()
  )

  object Noise : NoiseModule {
    object Continent2D : NoiseKey {
      override val id = "biome.amplified.continent2D"
    }

    object Hills2D : NoiseKey {
      override val id = "biome.amplified.hills2D"
    }

    object Ridges2D : NoiseKey {
      override val id = "biome.amplified.ridges2D"
    }

    object Valleys2D : NoiseKey {
      override val id = "biome.amplified.valleys2D"
    }

    object Detail2D : NoiseKey {
      override val id = "biome.amplified.detail2D"
    }

    object Island3D : NoiseKey {
      override val id = "biome.amplified.island3D"
    }

    object IslandCarve3D : NoiseKey {
      override val id = "biome.amplified.island_carve3D"
    }

    override fun install(bank: NoiseBank) {
      bank.register(Island3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0027)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
        }
      }
      bank.register(IslandCarve3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.002)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(Continent2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0025)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(Hills2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.003)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(4)//3
        }
      }

      bank.register(Ridges2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.006)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(Valleys2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.002)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
        }
      }

      bank.register(Detail2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.02)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
        }
      }
    }
  }

  override val noiseModule = Noise
}
