package killercreepr.cruxworldgen.test.biome

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.biome.BiomeShape
import killercreepr.cruxworldgen.api.block.BlockData
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
import killercreepr.cruxworldgen.bukkit.biome.BukkitBiome
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import org.bukkit.Material
import kotlin.math.abs
import kotlin.math.pow

class ToxicFogBasins(
  override val caves: CaveShape = gCaves,
  override val decorations: List<Decoration> = listOf(),
  override val materialProvider: MaterialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): BlockData {
      return if (context.isSolid) BukkitBlockResolver.INSTANCE.resolve(Material.DARK_PRISMARINE)
      else BlockData.NONE
    }
  },

  // --- Terrain knobs ---
  private val baseYAboveSea: Double = 34.0,      // baseline surface for this biome
  private val warpAmpBlocks: Double = 55.0,      // how “meandering” basins get

  private val basinThreshold01: Double = 0.48,   // lower => more basins; higher => fewer
  private val maxDepthBlocks: Double = 46.0,     // how deep basin centers can be
  private val depthPower: Double = 2.2,          // higher => steeper sides, flatter bottom

  // rim band near the edge of basins
  private val rimWidth01: Double = 0.07,         // thickness of rim band in noise-space
  private val rimHeightBlocks: Double = 7.0,
  private val rimPower: Double = 1.6,

  // floor imperfections
  private val floorAmpBlocks: Double = 2.5
) : Biome.Noised, BukkitBiome {

  override fun toBukkitBiome(): org.bukkit.block.Biome = org.bukkit.block.Biome.JAGGED_PEAKS

  object Noise : NoiseModule{
    object Warp2D : NoiseKey{ override val id = "biome.toxic_fog_basins.warp2D" }
    object Mask2D : NoiseKey{ override val id = "biome.toxic_fog_basins.mask2D" }
    object Floor2D : NoiseKey{ override val id = "biome.toxic_fog_basins.floor2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Warp2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.0018)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(Mask2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.0028) // basin patch size (lower => bigger basins)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3)
        }
      }
      bank.register(Floor2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.03) // floor imperfections
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
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
      edge: BiomeEdgeContext,
      signalWriter : SignalWriter
    ): DensityStack {

      val sea = ctx.chunkContext.seaLevel.toDouble()
      val baseSurfaceY = sea + baseYAboveSea

      val offset = basinOffset(ctx, worldX, worldZ)

      val surfaceY = baseSurfaceY + offset
      val baseDensity = surfaceY - y.toDouble()

      return DensityStack.densityStack(base = baseDensity, add = 0.0, carve = 0.0)
    }
  }

  private fun basinOffset(ctx: GenerateContext, x: Int, z: Int): Double {
    val wx = x.toDouble()
    val wz = z.toDouble()

    // --- 1) Domain warp (continuous) ---
    val warpX = ctx.noise.get(Noise.Warp2D).noise2D(wx, wz) * warpAmpBlocks
    val warpZ = ctx.noise.get(Noise.Warp2D).noise2D(wx + 1000.0, wz + 1000.0) * warpAmpBlocks
    val xw = wx + warpX
    val zw = wz + warpZ

    // --- 2) Basin patch field (0..1) ---
    val n01 = (ctx.noise.get(Noise.Mask2D).noise2D(xw, zw) + 1.0) * 0.5

    // We want basins where n01 is LOW.
    // strength01: 0 outside basins, 1 at deepest basin centers.
    val raw = ((basinThreshold01 - n01) / basinThreshold01).coerceIn(0.0, 1.0)
    if (raw <= 0.0) return 0.0

    val basinStrength = smoothstep01(raw).pow(depthPower)

    // --- 3) Bowl depth (negative) ---
    var offset = -maxDepthBlocks * basinStrength

    // --- 4) Rim lip: a band near n01 ~= basinThreshold01 ---
    // Compute how close we are to the edge (centered at threshold).
    val edgeDist = abs(n01 - basinThreshold01)
    val band = (1.0 - (edgeDist / rimWidth01)).coerceIn(0.0, 1.0)
    val rim = smoothstep01(band).pow(rimPower)

    // only add rim where we are in/near a basin (so it doesn’t appear everywhere)
    offset += rimHeightBlocks * rim * (basinStrength.coerceIn(0.0, 1.0))

    // --- 5) Floor imperfections (mostly in the interior) ---
    val floorN = ctx.noise.get(Noise.Floor2D).noise2D(wx, wz) // [-1..1]
    offset += floorN * floorAmpBlocks * basinStrength

    return offset
  }
}
