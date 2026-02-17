package killercreepr.cruxworldgen.test.biome

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
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.core.feature.PlacedFeature
import killercreepr.cruxworldgen.core.feature.ironHigh
import killercreepr.cruxworldgen.core.feature.ironLow
import killercreepr.cruxworldgen.test.cave.CavernRooms
import killercreepr.cruxworldgen.test.cave.CheeseCaves
import killercreepr.cruxworldgen.test.cave.LavaTubes
import killercreepr.cruxworldgen.test.cave.MountainCheeseOverhangs
import killercreepr.cruxworldgen.test.cave.RavineCarver
import killercreepr.cruxworldgen.test.cave.SpaghettiCaves
import org.bukkit.Material
import kotlin.math.abs
import kotlin.math.pow

/**
 * Amplified-like terrain: extreme peaks + deep valleys, still a column-fill heightfield.
 * Add your cave/ravine carvers separately for true AMPLIFIED mega-arches.
 */
const val scale = 0.6
class AmplifiedHighlands(
  override val caves: CaveShape = CaveProfile(listOf(
    //HorizontalMountainTunnel(),
    MountainCheeseOverhangs(
      centerDepthBlocks = 10.0,
      surfaceFadeRamp = 0,
      surfaceFadeStart = 0,
      threshold01 = 0.4,
      strength = 1.6,
      openMarginBlocks = 30.0,
      halfWidthBlocks = 50.0
    ),
    LavaTubes(
      surfaceFadeStart = 0,
      surfaceFadeRamp = 0,
      baseDepthBelowSurface = 0.0
    ),
    LavaTubes(
      noodleRadius = 3.5,
      verticalRadiusBlocks = 10.0,
      depthVariationBlocks = 15.0
    ),
    SpaghettiCaves(
      noodleRadius = 3.5,
      surfaceFadeStart = 0,
      surfaceFadeRamp = 8,
      openMarginBlocks = 30.0,
    ),
    CavernRooms(),
    CheeseCaves()
    /*CheeseCaves(
      centerDepthBlocks = 20.0,
      surfaceFadeRamp = 0,
      surfaceFadeStart = 0,
      threshold01 = 0.4,
      strength = 1.6,
      openMarginBlocks = 30.0,
      halfWidthBlocks = 50.0
    )*/,
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
  override val features: List<PlacedFeature<*>> = listOf(
    ironLow, ironHigh
  ),
  override val materialProvider: MaterialProvider = object : MaterialProvider {
    override fun chooseMaterial(ctx: MaterialContext): BlockData {
      if(ctx.depthFromSeaFloor >= 0){
        if(ctx.depthFromSeaFloor < 3){
          return BukkitBlockResolver.INSTANCE.resolve(Material.SAND)
        }
      }
      //if(ctx.isUnderwater) return BukkitBlockResolver.INSTANCE.resolve(Material.WATER)
      if(!ctx.isSolid) return BlockData.NONE
      val x = ctx.worldX
      val y = ctx.y
      val z = ctx.worldZ

      if(ctx.signalView.getOrDefault(x,y+1,z,RavineCarver.Signal.RavineFloor, 0.0) > 0.0){
        return BukkitBlockResolver.INSTANCE.resolve(Material.MAGMA_BLOCK)
      }

      val depth = ctx.depthBelowSurface
      if(depth == 0){
        return BukkitBlockResolver.INSTANCE.resolve(Material.GRASS_BLOCK)
      }
      if(depth < 4){
        return BukkitBlockResolver.INSTANCE.resolve(Material.DIRT)
      }
      return BukkitBlockResolver.INSTANCE.resolve(Material.STONE)
    }
  },

  // --- Baseline ---
  private val baseYAboveSea: Double = 26.0,

  // --- Large-scale shape ---
  private val continentAmp: Double = 1.0 * scale,   // big landmass up/down
  private val hillsAmp: Double = 38.0 * scale,        // mid bumps
  private val detailAmp: Double = 8.0 * scale,        // small roughness

  // --- Amplified peaks ---
  private val peakAmp: Double = 100.0 * scale,        // main “AMPLIFIED” height
  private val peakStart01: Double = 0.55,     // only the top part of ridges become huge
  private val peakEnd01: Double = 0.92,
  private val peakPower: Double = 2.8,        // higher => fewer, sharper mega peaks

  // --- Valleys / erosion feel ---
  private val valleyAmp: Double = 90.0 * scale,       // how deep valleys cut
  private val valleyPower: Double = 1.6,      // higher => flatter valley floors

  // --- Domain warp (makes ranges meander) ---
  private val warpAmpBlocks: Double = 120.0 * scale,

  // Optional: adds a subtle “shelf” feel. Set to 0.0 to disable.
  private val terraceStep: Double = 0.0,      // e.g. 6.0 for stylized terracing
  private val terraceBlend: Double = 0.35     // 0..1 (higher = smoother terraces)
) : Biome.Noised {

  object Noise : NoiseModule {
    object Warp2D : NoiseKey { override val id = "biome.amplified.warp2D" }
    object Continent2D : NoiseKey { override val id = "biome.amplified.continent2D" }
    object Hills2D : NoiseKey { override val id = "biome.amplified.hills2D" }
    object Ridges2D : NoiseKey { override val id = "biome.amplified.ridges2D" }
    object Valleys2D : NoiseKey { override val id = "biome.amplified.valleys2D" }
    object Detail2D : NoiseKey { override val id = "biome.amplified.detail2D" }
    object Overhang3D : NoiseKey { override val id = "biome.amplified.overhang3D" }
    object CliffOverhang3D : NoiseKey { override val id = "biome.amplified.cliffOverhang3D" }
    object Undercut3D : NoiseKey { override val id = "biome.amplified.undercut3D" }

    override fun install(bank: NoiseBank) {
      bank.register(Warp2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0011 / scale)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(Continent2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0025 / scale)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
        }
      }

      bank.register(Hills2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0002 / scale)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(4)//3
        }
      }

      bank.register(Ridges2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0008 / scale)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(5)
        }
      }

      bank.register(Valleys2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.00009 / scale)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
        }
      }

      bank.register(Detail2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.015 / scale)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(Overhang3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.015 / scale)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(CliffOverhang3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.012 / scale)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
      bank.register(Undercut3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.006 / scale)   // big undercuts; try 0.004..0.010
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

    }
  }

  override val noiseModule = Noise

  override val shape = object : BiomeShape {
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      edge: BiomeEdgeContext
    ): DensityStack {

      val sea = ctx.chunkContext.seaLevel.toDouble()
      val baseSurface = sea + baseYAboveSea

      val wx = worldX.toDouble()
      val wz = worldZ.toDouble()

      // --- 1) Domain warp ---
      val warpN = ctx.noise.get(Noise.Warp2D)
      val warpX = warpN.noise2D(wx, wz) * warpAmpBlocks
      val warpZ = warpN.noise2D(wx + 1000.0, wz + 1000.0) * warpAmpBlocks
      val xw = wx + warpX
      val zw = wz + warpZ

      // --- 2) Core noises ---
      val contN = ctx.noise.get(Noise.Continent2D).noise2D(xw, zw) // [-1..1]
      val hillsN = ctx.noise.get(Noise.Hills2D).noise2D(xw, zw)
      val ridgesN = ctx.noise.get(Noise.Ridges2D).noise2D(xw, zw)
      val valleysN = ctx.noise.get(Noise.Valleys2D).noise2D(xw, zw)
      val detailN = ctx.noise.get(Noise.Detail2D).noise2D(wx, wz)

      // remap to 0..1
      val cont01 = (contN + 1.0) * 0.5
      val hills01 = (hillsN + 1.0) * 0.5
      val valleys01 = (valleysN + 1.0) * 0.5

      // --- 3) Ridged peaks mask (the AMPLIFIED "secret sauce") ---
      // Turn simplex into ridges: high where abs(noise) is small.
      val ridge01 = (1.0 - abs(ridgesN)).coerceIn(0.0, 1.0)

      // Only the top slice becomes *massive* peaks.
      val t = ((ridge01 - peakStart01) / (peakEnd01 - peakStart01)).coerceIn(0.0, 1.0)
      val peakMask = smoothstep01(t).pow(peakPower)

      // --- 4) Valleys: prefer cutting where peaks aren't dominant ---
      val valleyMask = (1.0 - peakMask).coerceIn(0.0, 1.0)
      val valleyDepth = smoothstep01(valleys01).pow(valleyPower) * valleyMask

      // --- 5) Compose height offset (blocks) ---
      var offset = 0.0

      // continents (broad up/down)
      offset += (cont01 - 0.5) * 2.0 * continentAmp

      // hills (adds rough terrain even outside peaks)
      offset += (hills01 - 0.5) * 2.0 * hillsAmp

      // giant amplified peaks
      offset += peakMask * peakAmp

      // carve deep valleys (subtract)
      offset -= valleyDepth * valleyAmp

      // small detail
      offset += detailN * detailAmp

      // --- 6) Optional terracing/shelves (stylized) ---
      if (terraceStep > 0.0) {
        val q = kotlin.math.floor(offset / terraceStep) * terraceStep
        // blend between quantized and raw so it doesn't look too artificial
        offset = q * (1.0 - terraceBlend) + offset * terraceBlend
      }

      //val offset = amplifiedOffset(ctx, worldX, worldZ)
      val surfaceY = baseSurface + offset

      val base = surfaceY - y.toDouble()

// band-limited: only within ~18 blocks of the surface
      val dist = y.toDouble() - surfaceY

      // dist < 0 => inside rock. We want to carve mostly a few blocks inside the surface.
// Peak at -6, fades out by about -18, and does nothing above surface.
      val inside = (-dist).coerceAtLeast(0.0) // 0 above surface
      val center = 6.0
      val halfWidth = 12.0
      val bandInside = 1.0 - smoothstep01((kotlin.math.abs(inside - center) / halfWidth).coerceIn(0.0, 1.0))


      val band = 1.0 - smoothstep01((kotlin.math.abs(dist) / 18.0).coerceIn(0.0, 1.0))

// stronger on steep areas: approximate with mountainMask-ish via ridge01/pv (cheap)
      val overhang = ctx.noise.get(Noise.Overhang3D).noise3D(worldX, y, worldZ) * 14.0 * band

      var density = base - overhang

      /*if(peakMask in 0.3..0.9){
        val cliffOverhang = ctx.noise.get(Noise.CliffOverhang3D).noise3D(worldX, y, worldZ) * 40.0 * bandInside
        density -= cliffOverhang
      }*/

      val n = ctx.noise.get(Noise.CliffOverhang3D).noise3D(worldX, y, worldZ) // [-1..1]
      val n01 = n//((n + 1.0) * 0.5).coerceIn(0.0, 1.0)

      // Keep only the top slice -> sparse scoops.
      // Raise threshold to make rarer, deeper openings.
      val threshold = 0.68
      val scoop01 = ((n01 - threshold) / (1.0 - threshold)).coerceIn(0.0, 1.0)
      val scoop = scoop01.pow(2.6)

      val carveStrength = 28.0 // 18..45 typical
      val sideCarve = scoop * carveStrength// * mountainGate * bandInside

      density -= sideCarve


      return DensityStack.densityStack(base = density, add = 0.0, carve = 0.0)

      //val baseDensity = surfaceY - y.toDouble()
      //return DensityStack.densityStack(base = baseDensity, add = 0.0, carve = 0.0)
    }
  }
}
