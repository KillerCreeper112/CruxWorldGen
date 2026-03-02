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
import killercreepr.cruxworldgen.api.signal.SignalKey
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.Curve.smoothstep
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import killercreepr.cruxworldgen.api.util.NoiseShaper
import killercreepr.cruxworldgen.api.util.NoiseShaper.Point
import killercreepr.cruxworldgen.api.util.NoiseShaper.ShapingFunction
import killercreepr.cruxworldgen.bukkit.biome.BukkitBiome
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.standard.cave.CheeseCaves
import killercreepr.cruxworldgen.test.decor.FallenTreeDecor
import org.bukkit.Material
import kotlin.math.abs
import kotlin.math.pow

class CharredWastes(
  override val caves: CaveShape = CaveProfile(
    listOf(
      CheeseCaves()
    )
  ),
  override val decorations: List<Decoration> = listOf(
  ),
  override val materialProvider: MaterialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): BlockData {
      if (!context.isSolid) return BlockData.NONE

      val x = context.worldX
      val y = context.y
      val z = context.worldZ

      val crackMagma = context.signalView.getOrDefault(
        x,y+1,z,
        Signal.CRACK_MAGMA, 0.0
      )
      if (crackMagma > 0.8 && context.depthBelowSurface < 9) {
        return BukkitBlockResolver.Companion.INSTANCE.resolve(Material.MAGMA_BLOCK)
      }

      if (context.depthBelowSurface <= 0) return BukkitBlockResolver.INSTANCE.resolve(Material.BLACKSTONE)
      if (context.depthBelowSurface <= 2) return BukkitBlockResolver.Companion.INSTANCE.resolve(Material.BASALT)

      return BukkitBlockResolver.Companion.INSTANCE.resolve(Material.BASALT)
    }
  },

  // ===== Plateau knobs =====
  private val baseHeight: Double = 70.0,     // how high above sea level the wastes sit
  private val rollAmp: Double = 18.0,        // broad rolling
  private val ridgeAmp: Double = 55.0,       // raised ridges/knuckles

  // ===== Crack knobs (height depressions) =====
  private val crackThreshold01: Double = 0.70, // higher => fewer cracks
  private val crackDepth: Double = 12.0,       // how deep the cracks depress the surface
  private val crackWarpAmp: Double = 14.0,     // meander cracks

  // ===== Fissure carve knobs =====
  private val fissureThreshold01: Double = 0.83, // higher => rarer/thinner fissures
  private val fissureDepth: Double = 40.0,       // how far down the slit carves
  private val fissureStrength: Double = 26.0,    // how strongly it punches open
  private val fissureWallSoftness: Double = 1.6  // higher => sharper walls
) : Biome.Noised, BukkitBiome {

  override fun toBukkitBiome(): org.bukkit.block.Biome = org.bukkit.block.Biome.JUNGLE

  object Signal {
    val CRACK_MAGMA = SignalKey.doubleSignalKey()
  }

  object Noise : NoiseModule {
    object FissureWarp2D : NoiseKey { override val id = "biome.charred_wastes.fissure.warp2D" }
    object FissureMask2D : NoiseKey { override val id = "biome.charred_wastes.fissure.mask2D" }
    object Base2D : NoiseKey { override val id = "biome.charred_wastes.base2D" }
    object Ridge2D : NoiseKey { override val id = "biome.charred_wastes.ridge2D" }
    object CrackWarp2D : NoiseKey { override val id = "biome.charred_wastes.crack.warp2D" }
    object CrackMask2D : NoiseKey { override val id = "biome.charred_wastes.crack.mask2D" }
    object Overhang3D : NoiseKey { override val id = "biome.charred_wastes.overhang3D" }
    object OverhangHeight2D : NoiseKey { override val id = "biome.charred_wastes.overhang.height2D" }
    object OverhangMask2D : NoiseKey { override val id = "biome.charred_wastes.overhang.mask2D" }
    object OverhangWarp2D : NoiseKey { override val id = "biome.charred_wastes.overhang.warp2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Base2D){ seed ->
        NoiseField.Companion.noiseField(seed){
          frequency(0.0014) // big rolling, ~700 block wavelength
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
            .fractalGain(0.5)
            .fractalLacunarity(2.0)
        }
      }
      bank.register(Ridge2D){ seed ->
        NoiseField.Companion.noiseField(seed){
          frequency(0.0024)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.Ridged)
            .fractalOctaves(2)
            .fractalGain(0.5)
            .fractalLacunarity(2.0)
        }
      }
      bank.register(CrackWarp2D){ seed ->
        NoiseField.Companion.noiseField(seed){
          frequency(0.0028)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(CrackMask2D){ seed ->
        NoiseField.Companion.noiseField(seed){
          frequency(0.018)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(FissureWarp2D){ seed ->
        NoiseField.Companion.noiseField(seed){
          frequency(0.0018)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(FissureMask2D){ seed ->
        NoiseField.Companion.noiseField(seed){
          frequency(0.0065) // lower = longer, fewer fissures
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(Overhang3D){ seed ->
        NoiseField.Companion.noiseField(seed){
          frequency(0.05)
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(OverhangMask2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.02)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }

      bank.register(OverhangHeight2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.006)
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }

      bank.register(OverhangWarp2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.005)
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3)
        }
      }


    }
  }
  override val noiseModule = Noise

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

  override val shape = BiomeShapeProfile(
    object : BiomeShape {
      override fun density(
        ctx: GenerateContext,
        worldX: Int,
        y: Int,
        worldZ: Int,
        edge: BiomeEdgeContext,
        signalWriter : SignalWriter
      ): DensityStack {
        val sea = ctx.chunkContext.seaLevel

        // ----- 1) High-elevation plateau base -----
        val roll = shaper.smoothShape(ctx.noise.get(Noise.Base2D).noise2D(worldX, worldZ)) // [-1..1]
        val rollY = roll * rollAmp

        val ridgeN = shaper.smoothShape(ctx.noise.get(Noise.Ridge2D).noise2D(worldX, worldZ)) // [-1..1]
        val ridge01 = (1.0 - abs(ridgeN)).pow(3.0)            // [0..1]
        val ridgeY = ridge01 * ridgeAmp

        var surfaceY = (sea + baseHeight + rollY + ridgeY)

        // ----- 2) Crack depressions (heightfield carving, NOT caves) -----
        val x = worldX.toDouble()
        val z = worldZ.toDouble()

        val crackWarpX = ctx.noise.get(Noise.CrackWarp2D).noise2D(x, z) * crackWarpAmp
        val crackWarpZ = ctx.noise.get(Noise.CrackWarp2D).noise2D(x + 777.0, z + 777.0) * crackWarpAmp
        val xw = x + crackWarpX
        val zw = z + crackWarpZ

        val crackRidge01 = 1.0 - abs(ctx.noise.get(Noise.CrackMask2D).noise2D(xw, zw))
        val ct = ((crackRidge01 - crackThreshold01) / (1.0 - crackThreshold01)).coerceIn(0.0, 1.0)
        val crackLine = smoothstep01(ct) // 0..1 near crack center

        // widen a bit + make cracks feel “broken”
        val crackShape = crackLine.pow(1.25)

        // depress surface where crack exists
        surfaceY -= crackShape * crackDepth

        // ----- 3) Magma fissure SLITS (macro carve) -----
        // Similar to cracks but used as a density carve so it opens to sky.
        val fissure01 = fissureMask01(ctx, worldX, y, worldZ, surfaceY)
        val fissureCarve = fissure01.pow(fissureWallSoftness) * fissureStrength

        // Base density is simple surface field
        val baseDensity = surfaceY - y.toDouble()

        signalWriter.max(
          worldX, y, worldZ,
          Signal.CRACK_MAGMA,
          crackLine
        )

        val yNormalized = ctx.normalizedY(y)
        val overhangGate = smoothstep(0.45, 0.85, yNormalized)

        val overhang3D = shaper.shape(ctx.noise.get(Noise.Overhang3D).noise3D(worldX, y, worldZ))
        val ridge = 1.0 - abs(overhang3D)  // keep linear (no pow)

        val baseThreshold = 0.2
        val strength = 500.0

        val aboveSurface = (-baseDensity).coerceAtLeast(0.0)

// Past this height above the local surface, start suppressing floaters
        val start = 18.0   // try 12..30
        val end   = 90.0   // try 60..140 (bigger keeps “squish” taller)
        val extra = 0.35   // how much stricter it gets at high air (0.2..0.6)

        val t = smoothstep(start, end, aboveSurface)
        val threshold = baseThreshold + extra * t

        val overhang = overhangGate *
          ((ridge - threshold).coerceAtLeast(0.0)) *
          strength

        return DensityStack.densityStack(
          base = baseDensity,
          add = overhang,
          carve = fissureCarve
        )
      }
    },
    listOf(
    )
  )

  fun fissureMask01(ctx: GenerateContext, wx: Int, y: Int, wz: Int, surfaceY: Double): Double {
    val x = wx.toDouble()
    val z = wz.toDouble()

    // domain warp so fissures meander
    val warpAmp = 18.0
    val warpX = ctx.noise.get(Noise.FissureWarp2D).noise2D(x, z) * warpAmp
    val warpZ = ctx.noise.get(Noise.FissureWarp2D).noise2D(x + 1000.0, z + 1000.0) * warpAmp
    val xw = x + warpX
    val zw = z + warpZ

    // ribbon lines: ridge = 1 - abs(noise)
    val ridge01 = 1.0 - abs(ctx.noise.get(Noise.FissureMask2D).noise2D(xw, zw))

    val t = ((ridge01 - fissureThreshold01) / (1.0 - fissureThreshold01)).coerceIn(0.0, 1.0)
    val line = smoothstep01(t) // 0..1 around slit

    // vertical presence: strong near surface, fades out after fissureDepth
    val d = (surfaceY - y.toDouble())
    val vertical = smoothstep01(((fissureDepth - d) / fissureDepth).coerceIn(0.0, 1.0))

    return (line * vertical).coerceIn(0.0, 1.0)
  }
}