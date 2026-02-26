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
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import killercreepr.cruxworldgen.api.util.NoiseShaper
import killercreepr.cruxworldgen.bukkit.biome.BukkitBiome
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import org.bukkit.Material
import kotlin.math.abs
import kotlin.math.pow

class BasaltSpires(
  override val caves: CaveShape = CaveProfile(
    buildList { addAll(gCaves.caveTypes) }
  ),
  override val decorations: List<Decoration> = listOf(),
  override val materialProvider: MaterialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): BlockData {
      if (!context.isSolid) return BlockData.NONE

      val x = context.worldX
      val y = context.y
      val z = context.worldZ

      // reuse magma signal idea for continuity
      val crackMagma = context.signalView.getOrDefault(
        x, y + 1, z,
        Signal.CRACK_MAGMA, 0.0
      )

      // Hot streaks near surface
      if (crackMagma > 0.78 && context.depthBelowSurface < 10) {
        return BukkitBlockResolver.INSTANCE.resolve(Material.MAGMA_BLOCK)
      }

      // Spires: slightly tougher surface look
      if (context.depthBelowSurface <= 0) return BukkitBlockResolver.INSTANCE.resolve(Material.BASALT)
      if (context.depthBelowSurface <= 2) return BukkitBlockResolver.INSTANCE.resolve(Material.BLACKSTONE)

      return BukkitBlockResolver.INSTANCE.resolve(Material.BASALT)
    }
  },

  // ===== Base plane knobs =====
  private val baseHeight: Double = 52.0,   // lower than CharredWastes so it feels like a different zone
  private val rollAmp: Double = 10.0,      // gentler ground plane
  private val ridgeAmp: Double = 18.0,     // minor knuckles only

  // ===== Cracks (lighter than CharredWastes) =====
  private val crackThreshold01: Double = 0.78,
  private val crackDepth: Double = 6.0,
  private val crackWarpAmp: Double = 10.0,

  // ===== Spire field knobs =====
  private val spireFieldThreshold01: Double = 0.58,  // higher => fewer spire groves
  private val spireFieldWarpAmp: Double = 26.0,      // field meander
  private val spireHeight: Double = 92.0,            // how tall spires can reach above local surface
  private val spireStrength: Double = 180.0,         // additive density punch
  private val spireBodyThreshold: Double = 0.50,     // higher => thinner spires
  private val spireTaperPow: Double = 1.45,          // higher => sharper taper
  private val spireArchChance: Double = 0.22         // how often knuckles/arches appear
) : Biome.Noised, BukkitBiome {

  override fun toBukkitBiome(): org.bukkit.block.Biome = org.bukkit.block.Biome.BASALT_DELTAS

  object Signal {
    val CRACK_MAGMA = SignalKey.doubleSignalKey()
    val SPIRE_FIELD = SignalKey.doubleSignalKey()
  }

  object Noise : NoiseModule {
    object Base2D : NoiseKey { override val id = "biome.basalt_spires.base2D" }
    object Ridge2D : NoiseKey { override val id = "biome.basalt_spires.ridge2D" }

    object CrackWarp2D : NoiseKey { override val id = "biome.basalt_spires.crack.warp2D" }
    object CrackMask2D : NoiseKey { override val id = "biome.basalt_spires.crack.mask2D" }

    object SpireFieldWarp2D : NoiseKey { override val id = "biome.basalt_spires.spire.fieldWarp2D" }
    object SpireFieldMask2D : NoiseKey { override val id = "biome.basalt_spires.spire.fieldMask2D" }

    object SpireBody3D : NoiseKey { override val id = "biome.basalt_spires.spire.body3D" }
    object SpireKnuckle3D : NoiseKey { override val id = "biome.basalt_spires.spire.knuckle3D" }

    object Gullies2D : NoiseKey { override val id = "biome.basalt_spires.gullies2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Base2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.0012)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
            .fractalGain(0.5)
            .fractalLacunarity(2.0)
        }
      }
      bank.register(Ridge2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.0022)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.Ridged)
            .fractalOctaves(2)
            .fractalGain(0.5)
            .fractalLacunarity(2.0)
        }
      }

      bank.register(CrackWarp2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.0026)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(CrackMask2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.017)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }

      // Spire groves: a broad mask + warp
      bank.register(SpireFieldWarp2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.0016)
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(SpireFieldMask2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.0042)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }

      // Spire body: 3D noise where "ridges" become columns when gated by field + taper
      bank.register(SpireBody3D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.035)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }

      // Knuckles/arches: separate higher-freq 3D noise to create occasional bridges
      bank.register(SpireKnuckle3D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.06)
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }

      // Tiny gullies so spire fields aren’t just flat + poles
      bank.register(Gullies2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.009)
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.Ridged)
            .fractalOctaves(1)
        }
      }
    }
  }
  override val noiseModule = Noise

  val shaper = NoiseShaper(
    listOf(
      NoiseShaper.Point(-1.0, NoiseShaper.ShapingFunction.VALLEY),
      NoiseShaper.Point(-0.3, NoiseShaper.ShapingFunction.FLAT),
      NoiseShaper.Point(0.0, NoiseShaper.ShapingFunction.FLAT),
      NoiseShaper.Point(0.7, NoiseShaper.ShapingFunction.HILLS),
      NoiseShaper.Point(1.0, NoiseShaper.ShapingFunction.MOUNTAIN)
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
        val sea = ctx.chunkContext.seaLevel

        // ----- 1) Base plane -----
        val roll = shaper.smoothShape(ctx.noise.get(Noise.Base2D).noise2D(worldX, worldZ))
        val rollY = roll * rollAmp

        val ridgeN = shaper.smoothShape(ctx.noise.get(Noise.Ridge2D).noise2D(worldX, worldZ))
        val ridge01 = (1.0 - abs(ridgeN)).let { it * it } // softer than pow(3)
        val ridgeY = ridge01 * ridgeAmp

        var surfaceY = (sea + baseHeight + rollY + ridgeY)

        // ----- 2) Light cracks for continuity -----
        val x = worldX.toDouble()
        val z = worldZ.toDouble()
        val crackWarpX = ctx.noise.get(Noise.CrackWarp2D).noise2D(x, z) * crackWarpAmp
        val crackWarpZ = ctx.noise.get(Noise.CrackWarp2D).noise2D(x + 777.0, z + 777.0) * crackWarpAmp
        val xw = x + crackWarpX
        val zw = z + crackWarpZ

        val crackRidge01 = 1.0 - abs(ctx.noise.get(Noise.CrackMask2D).noise2D(xw, zw))
        val ct = ((crackRidge01 - crackThreshold01) / (1.0 - crackThreshold01)).coerceIn(0.0, 1.0)
        val crackLine = smoothstep01(ct)
        surfaceY -= crackLine.pow(1.2) * crackDepth

        // Base density (surface field)
        val baseDensity = surfaceY - y.toDouble()

        // ----- 3) Spire fields (2D gate) -----
        val fwX = ctx.noise.get(Noise.SpireFieldWarp2D).noise2D(x, z) * spireFieldWarpAmp
        val fwZ = ctx.noise.get(Noise.SpireFieldWarp2D).noise2D(x + 1000.0, z + 1000.0) * spireFieldWarpAmp
        val fx = x + fwX
        val fz = z + fwZ

        // map [-1..1] -> [0..1]
        val fieldRaw01 = (ctx.noise.get(Noise.SpireFieldMask2D).noise2D(fx, fz) * 0.5 + 0.5)
        val fieldT = ((fieldRaw01 - spireFieldThreshold01) / (1.0 - spireFieldThreshold01)).coerceIn(0.0, 1.0)
        val spireField01 = smoothstep01(fieldT)

        // ----- 4) Spire body (3D column-ish) -----
        // "ridge" trick: 1 - abs(noise) makes vein-like solids; threshold => columns
        val body = ctx.noise.get(Noise.SpireBody3D).noise3D(worldX, y, worldZ)
        val bodyRidge01 = 1.0 - abs(body) // [0..1] ish
        val bodyT = ((bodyRidge01 - spireBodyThreshold) / (1.0 - spireBodyThreshold)).coerceIn(0.0, 1.0)
        val body01 = smoothstep01(bodyT)

        // Taper with height above local surface
        val above = (y.toDouble() - surfaceY).coerceAtLeast(0.0)
        val taper01 = (1.0 - (above / spireHeight).coerceIn(0.0, 1.0)).pow(spireTaperPow)

        // Additive spire density
        val spireAdd = spireField01 * body01 * taper01 * spireStrength

        // ----- 5) Occasional arches/knuckles -----
        val kn = ctx.noise.get(Noise.SpireKnuckle3D).noise3D(worldX, y, worldZ) * 0.5 + 0.5
        val kn01 = smoothstep01(((kn - (1.0 - spireArchChance)) / spireArchChance).coerceIn(0.0, 1.0))
        // only mid-height knuckles so they read as bridges, not blobs
        val mid = Curve.smoothstep(8.0, 28.0, above) * (1.0 - Curve.smoothstep(36.0, 70.0, above))
        val archAdd = spireField01 * kn01 * mid * (spireStrength * 0.35)

        // ----- 6) Tiny gullies carve (keeps navigation interesting) -----
        val gul = 1.0 - abs(ctx.noise.get(Noise.Gullies2D).noise2D(worldX, worldZ))
        val g01 = smoothstep01(((gul - 0.78) / (1.0 - 0.78)).coerceIn(0.0, 1.0))
        val gCarve = g01 * 6.5 // small

        // Signals
        signalWriter.max(worldX, y, worldZ, Signal.CRACK_MAGMA, crackLine)
        signalWriter.max(worldX, y, worldZ, Signal.SPIRE_FIELD, spireField01)

        return DensityStack.densityStack(
          base = baseDensity,
          add = spireAdd + archAdd,
          carve = gCarve
        )
      }
    },
    listOf()
  )
}