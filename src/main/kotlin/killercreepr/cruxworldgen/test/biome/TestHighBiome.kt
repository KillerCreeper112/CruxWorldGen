package killercreepr.cruxworldgen.test.biome

import io.papermc.paper.util.ItemComponentSanitizer.override
import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.biome.BiomeShape
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
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import killercreepr.cruxworldgen.api.util.NoiseShaper
import killercreepr.cruxworldgen.api.util.NoiseShaper.Point
import killercreepr.cruxworldgen.api.util.NoiseShaper.ShapingFunction
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.test.cave.CavernRooms
import killercreepr.cruxworldgen.test.cave.CheeseCaves
import killercreepr.cruxworldgen.test.cave.HorizontalMountainTunnel
import killercreepr.cruxworldgen.test.cave.LavaTubes
import killercreepr.cruxworldgen.test.cave.MountainCutCavesOld
import killercreepr.cruxworldgen.test.cave.RavineCarver
import killercreepr.cruxworldgen.test.cave.SpaghettiCaves
import killercreepr.cruxworldgen.test.cave.ThroughMountainCave
import org.bukkit.Material
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

class TestHighBiome(
  override val caves: CaveShape = CaveProfile(listOf(
    /*CheeseCaves(
      centerDepthBlocks = 20.0,
      surfaceFadeRamp = 0,
      surfaceFadeStart = 0,
      threshold01 = 0.4,
      strength = 1.6,
      openMarginBlocks = 30.0,
      halfWidthBlocks = 50.0
    )*/
    /*CheeseCaves(
      centerDepthBlocks = 20.0,
      surfaceFadeRamp = 0,
      surfaceFadeStart = 0
    )*/
    /*LavaTubes(
      noodleRadius = 4.0,
      verticalRadiusBlocks = 15.0,
      depthVariationBlocks = 15.0,
      strength = 1.5,
      openMarginBlocks = 15.0
    ),
    RavineCarver(),
    SpaghettiCaves(),
    CavernRooms(),
    CheeseCaves()*/
    /*SpaghettiCaves(
      noodleRadius = 8.0,
      //baseDepthBelowSurface = 0.0,
      verticalRadiusBlocks = 10.0,
      depthVariationBlocks = 20.0,
      surfaceFadeRamp = 0,
      surfaceFadeStart = 0
    )*/
    /*LavaTubes(
      surfaceFadeStart = 0,
      surfaceFadeRamp = 0,
      baseDepthBelowSurface = 0.0,
      noodleRadius = 2.0,
      openMarginBlocks = 20.0
    )*/
    /*CheeseCaves(
      centerDepthBlocks = 0.0,
      surfaceFadeRamp = 0,
      surfaceFadeStart = 0
    ),
    RavineCarver(
      surfaceFadeRamp = 0,
      surfaceFadeStart = 0
    ),*/

    /*LavaTubes(),
    RavineCarver(),
    SpaghettiCaves(),
    CavernRooms(),
    CheeseCaves()*/
  )),
  override val decorations: List<Decoration> = listOf(),
  override val materialProvider: MaterialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): BlockData {
      return if (context.isSolid) BukkitBlockResolver.INSTANCE.resolve(Material.STONE)
      else BlockData.NONE
    }
  },

  // --- Baseline ---
  private val baseYAboveSea: Double = 26.0,

  // --- Large-scale shape ---
  private val continentAmp: Double = 5.0,   // big landmass up/down
  private val hillsAmp: Double = 100.0,        // mid bumps
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
) : Biome.Noised {

  object Noise : NoiseModule {
    object Continent2D : NoiseKey { override val id = "biome.test1.continent" }
    object Erosion : NoiseKey { override val id = "biome.test1.erosion" }
    object PeaksNValley : NoiseKey { override val id = "biome.test1.hills2D" }
    object Weirdness : NoiseKey { override val id = "biome.test1.weirdness" }
    object SquashingFactor : NoiseKey { override val id = "biome.test1.squashing_factor" }

    object MacroRegions : NoiseKey { override val id = "biome.test1.macro_regions" }
    object LocalPeaks   : NoiseKey { override val id = "biome.test1.local_peaks" }
    object LocalValleys : NoiseKey { override val id = "biome.test1.local_valleys" }

    override fun install(bank: NoiseBank) {

      bank.register(Continent2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.005 / scale)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(Erosion) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0001 / scale)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(1)
        }
      }

      bank.register(PeaksNValley) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0009 / scale)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)//3
        }
      }

      bank.register(Weirdness) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.001 / scale)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)//3
        }
      }

      bank.register(SquashingFactor) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.002 / scale)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)//3
        }
      }

      bank.register(MacroRegions) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0009 / scale)     // slow = long plains/mountain ranges
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(LocalPeaks) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.006 / scale)      // faster detail, but will be gated
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(LocalValleys) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0045 / scale)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

    }
  }

  override val noiseModule = Noise

  override val shape = object : BiomeShape {
    private fun remap01(x: Double) = ((x + 1.0) * 0.5).coerceIn(0.0, 1.0)

    // 1 near center, fades to 0 outside center±halfWidth
    private fun band01(x01: Double, center: Double, halfWidth: Double): Double {
      val t = (kotlin.math.abs(x01 - center) / halfWidth).coerceIn(0.0, 1.0)
      return 1.0 - smoothstep01(t)
    }
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      edge: BiomeEdgeContext
    ): DensityStack {
      val noise = ctx.noise

      val shaper = NoiseShaper(
        listOf(
          Point(-1.0, ShapingFunction.VALLEY),
          Point(-0.3, ShapingFunction.FLAT),
          Point(0.0, ShapingFunction.FLAT),
          Point(0.7, ShapingFunction.FLAT),
          Point(0.8, ShapingFunction.HILLS),
          Point(1.0, ShapingFunction.MOUNTAIN)
        )
      )

      // --- Macro "region" selector (slow) ---
      val macro01 = remap01(noise.get(Noise.MacroRegions).noise2D(worldX, worldZ)) // 0..1

      // Plains should dominate: wide plains band around 0.5
      val plainsMask = band01(macro01, center = 0.50, halfWidth = 0.30)           // wider => more plains

      // Valleys only when macro is low
      val valleyMask = smoothstep01(((0.40 - macro01) / 0.18).coerceIn(0.0, 1.0)).pow(1.2)

      // Mountains only when macro is high
      val mountainMask = smoothstep01(((macro01 - 0.62) / 0.22).coerceIn(0.0, 1.0)).pow(1.8)

      // --- Base / plains drivers (2D, relatively gentle) ---
      val continent = shaper.smoothShape(noise.get(Noise.Continent2D).noise2D(worldX, worldZ))
      val erosion2D = shaper.smoothShape(noise.get(Noise.Erosion).noise2D(worldX, worldZ))
      val detail2D  = shaper.smoothShape(noise.get(Noise.Weirdness).noise2D(worldX, worldZ))

      // --- Local features (faster, but gated) ---
      val localPeaks   = shaper.smoothShape(noise.get(Noise.LocalPeaks).noise2D(worldX, worldZ))
      val localValleys = shaper.smoothShape(noise.get(Noise.LocalValleys).noise2D(worldX, worldZ))

      val sea = ctx.chunkContext.seaLevel.toDouble()
      val baseSurface = sea + baseYAboveSea

      // Plains are the default baseline (don’t let this swing too hard)
      val plainsOffset =
        continent * continentAmp +
          erosion2D * (hillsAmp * 0.25) +     // IMPORTANT: reduce influence here
          detail2D  * (detailAmp * 0.35)

      // Mountains/valleys only show up when macro says so
      val mountainsOffset = mountainMask * (localPeaks * peakAmp)
      val valleysOffset   = valleyMask   * (kotlin.math.abs(localValleys) * valleyAmp)

      val surfaceY = baseSurface + plainsOffset + mountainsOffset - valleysOffset

      val baseDensity = surfaceY - y.toDouble()
      return DensityStack.densityStack(base = baseDensity, add = 0.0, carve = 0.0)
    }

    /*override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      edge: BiomeEdgeContext
    ): DensityStack {
      val noise = ctx.noise

      // Sea level and relative height
      val seaLevel = ctx.chunkContext.seaLevel
      val relativeHeight = y - seaLevel

      val shaper = NoiseShaper(
        listOf(
          Point(-1.0, ShapingFunction.VALLEY),
          Point(-0.3, ShapingFunction.FLAT),
          Point(0.0, ShapingFunction.FLAT),
          Point(0.7, ShapingFunction.FLAT),
          Point(0.8, ShapingFunction.HILLS),
          Point(1.0, ShapingFunction.MOUNTAIN)
        )
      )

      val continent = shaper.smoothShape(noise.get(Noise.Continent2D).noise2D(worldX, worldZ))
      val peaks     = shaper.smoothShape(noise.get(Noise.PeaksNValley).noise2D(worldX, worldZ))
      val erosion2D = shaper.smoothShape(noise.get(Noise.Erosion).noise2D(worldX, worldZ))
      val weird2D   = shaper.smoothShape(noise.get(Noise.Weirdness).noise2D(worldX, worldZ))

      val sea = ctx.chunkContext.seaLevel.toDouble()
      val baseSurface = sea + baseYAboveSea

      val surfaceOffset =
        continent * continentAmp +
          peaks * peakAmp * 0.35 +          // scaled down peaks contribution
          erosion2D * hillsAmp * 0.6 +
          weird2D * detailAmp * 0.6

      val surfaceY = baseSurface + surfaceOffset

      val baseDensity = surfaceY - y.toDouble()  // >0 solid, <0 air (typical)
      return DensityStack.densityStack(base = baseDensity, add = 0.0, carve = 0.0)

    }*/
  }
}
