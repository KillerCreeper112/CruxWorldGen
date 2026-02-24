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
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import killercreepr.cruxworldgen.api.util.NoiseShaper
import killercreepr.cruxworldgen.bukkit.biome.BukkitBiome
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.core.feature.PlacedFeature
import killercreepr.cruxworldgen.core.feature.ironHigh
import killercreepr.cruxworldgen.core.feature.ironLow
import killercreepr.cruxworldgen.test.decor.AbyssTreeDecor
import org.bukkit.Material
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/**
 * Amplified-like terrain: extreme peaks + deep valleys, still a column-fill heightfield.
 * Add your cave/ravine carvers separately for true AMPLIFIED mega-arches.
 */
class AbyssStart(
  override val caves: CaveShape = CaveProfile(
    listOf(

    )
  ),
  override val decorations: List<Decoration> = listOf(
    AbyssTreeDecor()
  ),
  override val features: List<PlacedFeature<*>> = listOf(
    ironLow, ironHigh
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

  // --- Baseline ---
  private val baseYAboveSea: Double = 40.0,

  // --- Large-scale shape ---
  private val continentAmp: Double = 1.0,   // big landmass up/down
  private val hillsAmp: Double = 38.0,        // mid bumps
  private val detailAmp: Double = 8.0,        // small roughness

  // --- Amplified peaks ---
  private val peakAmp: Double = 100.0,        // main “AMPLIFIED” height
  private val peakStart01: Double = 0.55,     // only the top part of ridges become huge
  private val peakEnd01: Double = 0.92,
  private val peakPower: Double = 2.8,        // higher => fewer, sharper mega peaks

  // --- Valleys / erosion feel ---
  private val valleyAmp: Double = 90.0,       // how deep valleys cut
  private val valleyPower: Double = 1.6,      // higher => flatter valley floors

  // --- Domain warp (makes ranges meander) ---
  private val warpAmpBlocks: Double = 120.0,

  // Optional: adds a subtle “shelf” feel. Set to 0.0 to disable.
  private val terraceStep: Double = 0.0,      // e.g. 6.0 for stylized terracing
  private val terraceBlend: Double = 0.35     // 0..1 (higher = smoother terraces)
) : Biome.Noised, BukkitBiome {
  override fun toBukkitBiome(): org.bukkit.block.Biome = org.bukkit.block.Biome.END_HIGHLANDS

  object Noise : NoiseModule {
    object Warp2D : NoiseKey {
      override val id = "biome.amplified.warp2D"
    }

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

    object Overhang3D : NoiseKey {
      override val id = "biome.amplified.overhang3D"
    }

    object OverhangX3D : NoiseKey {
      override val id = "biome.amplified.overhangX3D"
    }

    object OverhangY3D : NoiseKey {
      override val id = "biome.amplified.overhangY3D"
    }

    object OverhangZ3D : NoiseKey {
      override val id = "biome.amplified.overhangZ3D"
    }

    object OverhangCarve3D : NoiseKey {
      override val id = "biome.amplified.overhang_carve3D"
    }

    object OverhangWarp2D : NoiseKey {
      override val id = "biome.amplified.overhang_warp2D"
    }

    object CliffOverhang3D : NoiseKey {
      override val id = "biome.amplified.cliffOverhang3D"
    }

    object Undercut3D : NoiseKey {
      override val id = "biome.amplified.undercut3D"
    }
    object Spike2D : NoiseKey {
      override val id = "biome.amplified.spike2D"
    }

    override fun install(bank: NoiseBank) {
      bank.register(Warp2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0011)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(4)
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
      bank.register(CliffOverhang3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.012)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
        }
      }
      bank.register(Undercut3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.006)   // big undercuts; try 0.004..0.010
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
        }
      }

      bank.register(Overhang3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.008) // ~125 block wavelength
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
        }
      }

      bank.register(OverhangWarp2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.004) // ~250 blocks, slow warping
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

// If you're doing 3D vector warp, these should be LOW freq and LOW octaves.
// These are “flow fields”, not detail noise.
      bank.register(OverhangX3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.006) // ~166 blocks
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(OverhangY3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.006)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(OverhangZ3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.006)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(OverhangCarve3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.018) // ~55 blocks -> good arch/void scale
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(Spike2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0045) // 0.003..0.007: lower = broader spike fields
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2) // keep low to avoid noisy mess
        }
      }
    }
  }

  override val noiseModule = Noise

  val shaper = NoiseShaper(
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
  )

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
        val wz = worldZ.toDouble()

        val warpN = ctx.noise.get(Noise.Warp2D)
        val warpX = warpN.noise2D(wx, wz) * warpAmpBlocks
        val warpZ = warpN.noise2D(wx + 1000.0, wz + 1000.0) * warpAmpBlocks
        val xw = wx + warpX
        val zw = wz + warpZ

        val contN = shaper.smoothShape(ctx.noise.get(Noise.Continent2D).noise2D(xw, zw)) // [-1..1]
        val hillsN = shaper.smoothShape(ctx.noise.get(Noise.Hills2D).noise2D(xw, zw))
        val ridgesN = shaper.smoothShape(ctx.noise.get(Noise.Ridges2D).noise2D(xw, zw))
        val valleysN = shaper.smoothShape(ctx.noise.get(Noise.Valleys2D).noise2D(xw, zw))
        val detailN = ctx.noise.get(Noise.Detail2D).noise2D(wx, wz)

        val cont01 = (contN + 1.0) * 0.5
        val hills01 = (hillsN + 1.0) * 0.5

        val valleys01 = (valleysN + 1.0) * 0.5

        val ridge01 = (1.0 - abs(ridgesN)).coerceIn(0.0, 1.0)

        val t = ((ridge01 - peakStart01) / (peakEnd01 - peakStart01)).coerceIn(0.0, 1.0)
        val peakMask = smoothstep01(t).pow(peakPower)

        val highland01 = smoothstep01(((cont01 - 0.56) / (0.90 - 0.56)).coerceIn(0.0, 1.0))

        val range01 = smoothstep01(((hills01 - 0.55) / (0.85 - 0.55)).coerceIn(0.0, 1.0)) * highland01

        val tame = 1.0 - 0.85 * range01

        val spikeRaw = ctx.noise.get(Noise.Spike2D).noise2D(xw + 2000.0, zw - 2000.0) // [-1..1]
        val spike01 = ((spikeRaw + 1.0) * 0.5).coerceIn(0.0, 1.0)

        val spikeStart = 0.40
        val spikeEnd   = 0.985
        val tt = ((spike01 - spikeStart) / (spikeEnd - spikeStart)).coerceIn(0.0, 1.0)

        val spikeMask = smoothstep01(tt).pow(3.0) * highland01
        val valleyMask = (1.0 - spikeMask).coerceIn(0.0, 1.0)
        val valleyDepth = smoothstep01(valleys01).pow(valleyPower) * valleyMask

        var offset = 0.0

        offset += (cont01 - 0.5) * 2.0 * continentAmp * (0.85 + 0.15 * range01)

        offset += (hills01 - 0.5) * 2.0 * hillsAmp * tame

        val peakBase = peakMask * highland01 * range01
        offset += peakBase * (peakAmp * 0.25)

        offset += spikeMask * peakAmp
        offset -= valleyDepth * valleyAmp


        offset += detailN * detailAmp * (0.55 + 0.45 * range01)

        if (terraceStep > 0.0) {
          val q = floor(offset / terraceStep) * terraceStep
          offset = q * (1.0 - terraceBlend) + offset * terraceBlend
        }

        val surfaceY = baseSurface + offset

        val base = surfaceY - y.toDouble()
        var density = base

        return DensityStack.densityStack(base = density, add = 0.0, carve = 0.0)
      }
    },
    listOf(
      AbyssStartOverhang(
        NoiseShaper(
          listOf(
            NoiseShaper.Point(-1.0, NoiseShaper.ShapingFunction.VALLEY),
            NoiseShaper.Point(-0.3, NoiseShaper.ShapingFunction.FLAT),
            NoiseShaper.Point(0.0, NoiseShaper.ShapingFunction.FLAT),
            NoiseShaper.Point(0.7, NoiseShaper.ShapingFunction.FLAT),
            NoiseShaper.Point(0.8, NoiseShaper.ShapingFunction.HILLS),
            NoiseShaper.Point(1.0, NoiseShaper.ShapingFunction.MOUNTAIN)
          )
        ),
        Noise.Overhang3D,
        Noise.OverhangWarp2D,
        Noise.OverhangX3D,
        Noise.OverhangY3D,
        Noise.OverhangZ3D,
        Noise.OverhangCarve3D
      )
    )
  )
}
