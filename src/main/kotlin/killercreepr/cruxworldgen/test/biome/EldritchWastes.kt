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
import killercreepr.cruxworldgen.core.feature.ironHigh
import killercreepr.cruxworldgen.core.feature.ironLow
import killercreepr.cruxworldgen.test.cave.eldritch.CathedralChambers
import killercreepr.cruxworldgen.test.cave.eldritch.OffsetTunnels
import killercreepr.cruxworldgen.test.cave.eldritch.VerticalTears
import killercreepr.cruxworldgen.test.cave.eldritch.VoidPockets
import killercreepr.cruxworldgen.test.decor.AbyssTreeDecor
import org.bukkit.Material
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

class EldritchWastes(
  override val caves: CaveShape = CaveProfile(
    listOf(
      CathedralChambers(
        threshold01 = 0.82,
        strength = 1.18,
        openMarginBlocks = 22.0
      ),

      // "Missing chunks" around the same general vertical region
      VoidPockets(
        pocketThreshold01 = 0.75,
        pocketStrength = 1.10,
        openMarginBlocks = 8.0
      ),

      // Thin vertical rips that connect spaces and feel unnatural
      VerticalTears(
        slitRadius = 0.20,
        slitStrength = 1.16,
        openMarginBlocks = 14.0
      ),

      // Traversable worm-like tunnels, but warped/misaligned
      OffsetTunnels(
        noodleRadius = 0.42,
        verticalRadiusBlocks = 8.0,
        baseDepthBelowSurface = 44.0,
        depthVariationBlocks = 20.0,
        strength = 1.14,
        openMarginBlocks = 9.0
      )
    )
  ),

  override val decorations: List<Decoration> = listOf(
    // Replace/add your eldritch decorations later
    AbyssTreeDecor()
  ),

  override val features: List<PlacedFeature<*>> = listOf(
    ironLow, ironHigh
  ),

  override val materialProvider: MaterialProvider = object : MaterialProvider {
    override fun chooseMaterial(ctx: MaterialContext): BlockData {
      if (!ctx.isSolid) return BlockData.NONE

      val depth = ctx.depthBelowSurface

      // Sea-floor / basin crust
      if (ctx.depthFromSeaFloor >= 0 && ctx.depthFromSeaFloor < 3) {
        return BukkitBlockResolver.INSTANCE.resolve(Material.BLACKSTONE)
      }

      // Surface skin
      if (depth == 0) {
        // "Warped, dead, fungal/ash" vibe
        return BukkitBlockResolver.INSTANCE.resolve(Material.WARPED_NYLIUM)
      }

      // Subsurface crust
      if (depth < 3) {
        return BukkitBlockResolver.INSTANCE.resolve(Material.SOUL_SOIL)
      }

      if (depth < 6) {
        return BukkitBlockResolver.INSTANCE.resolve(Material.BASALT)
      }

      return BukkitBlockResolver.INSTANCE.resolve(Material.DEEPSLATE)
    }
  },

  // --- Baseline feel ---
  private val baseYAboveSea: Double = 100.0,

  // --- Broad terrain (keeps biome traversable) ---
  private val continentAmp: Double = 22.0,
  private val plainsAmp: Double = 14.0,
  private val roughAmp: Double = 7.5,

  // --- Distortion / reality folding ---
  private val macroWarpAmpBlocks: Double = 150.0,
  private val microWarpAmpBlocks: Double = 36.0,

  // --- Fractures / deleted land ---
  private val fractureCutAmp: Double = 90.0,
  private val fractureSharpness: Double = 3.2, // higher = thinner, nastier tears

  // --- Wrongness zones ("terrain laws shift here") ---
  private val lawZoneStrength: Double = 1.0,

  // --- Inverted shelf / floating mass contribution (in base density) ---
  private val shelfAddAmp: Double = 26.0,   // pseudo "lift" near certain heights
  private val shelfBandBlocks: Double = 18.0,

  // Optional subtle terracing (works well if you want "folds")
  private val terraceStep: Double = 0.0,
  private val terraceBlend: Double = 0.4
) : Biome.Noised, BukkitBiome {

  override fun toBukkitBiome(): org.bukkit.block.Biome = org.bukkit.block.Biome.END_HIGHLANDS

  object Noise : NoiseModule {

    // Domain warps
    object MacroWarp2D : NoiseKey { override val id = "biome.eldritch.macroWarp2D" }
    object MicroWarp2D : NoiseKey { override val id = "biome.eldritch.microWarp2D" }

    // Base shape
    object Continent2D : NoiseKey { override val id = "biome.eldritch.continent2D" }
    object Plains2D : NoiseKey { override val id = "biome.eldritch.plains2D" }
    object Ridges2D : NoiseKey { override val id = "biome.eldritch.ridges2D" }
    object Rough2D : NoiseKey { override val id = "biome.eldritch.rough2D" }

    // Fractures / rifts
    object Fracture2D : NoiseKey { override val id = "biome.eldritch.fracture2D" }
    object FractureWarp2D : NoiseKey { override val id = "biome.eldritch.fractureWarp2D" }

    // Local "law zones" (controls how terrain behaves in subregions)
    object LawZone2D : NoiseKey { override val id = "biome.eldritch.lawZone2D" }
    object Compress2D : NoiseKey { override val id = "biome.eldritch.compress2D" }
    object Stretch2D : NoiseKey { override val id = "biome.eldritch.stretch2D" }

    // 3D distortion fields (used by base density and optional overhang layer)
    object Fold3D : NoiseKey { override val id = "biome.eldritch.fold3D" }
    object Shear3D : NoiseKey { override val id = "biome.eldritch.shear3D" }
    object VoidPockets3D : NoiseKey { override val id = "biome.eldritch.voidPockets3D" }

    // Overhang support (reusing your subshape style)
    object Overhang3D : NoiseKey { override val id = "biome.eldritch.overhang3D" }
    object OverhangWarp2D : NoiseKey { override val id = "biome.eldritch.overhangWarp2D" }
    object OverhangX3D : NoiseKey { override val id = "biome.eldritch.overhangX3D" }
    object OverhangY3D : NoiseKey { override val id = "biome.eldritch.overhangY3D" }
    object OverhangZ3D : NoiseKey { override val id = "biome.eldritch.overhangZ3D" }
    object OverhangCarve3D : NoiseKey { override val id = "biome.eldritch.overhangCarve3D" }

    override fun install(bank: NoiseBank) {
      // ----- Domain warp -----
      bank.register(MacroWarp2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0012)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
        }
      }

      bank.register(MicroWarp2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0055)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      // ----- Base terrain -----
      bank.register(Continent2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0021)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(Plains2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0042)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
        }
      }

      bank.register(Ridges2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0065)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(Rough2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.018)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
        }
      }

      // ----- Fractures -----
      bank.register(Fracture2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0034)
          noiseType(CruxNoise.NoiseType.OpenSimplex2S) // if unavailable, swap to OpenSimplex2
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
        }
      }

      bank.register(FractureWarp2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0027)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      // ----- Law zones -----
      bank.register(LawZone2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0018) // broad "wrongness regions"
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(Compress2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0048)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(Stretch2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0038)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      // ----- 3D distortion -----
      bank.register(Fold3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.010)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
        }
      }

      bank.register(Shear3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.007)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(VoidPockets3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.014)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      // ----- Overhang support -----
      bank.register(Overhang3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0085)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
        }
      }

      bank.register(OverhangWarp2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0038)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(OverhangX3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0055)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(OverhangY3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0055)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(OverhangZ3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0055)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }

      bank.register(OverhangCarve3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.017)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
        }
      }
    }
  }

  override val noiseModule = Noise

  // Shaper is intentionally "odd":
  // low values are still somewhat traversable, highs don't become classic mountains
  private val shaper = NoiseShaper(
    listOf(
      NoiseShaper.Point(-1.0, NoiseShaper.ShapingFunction.VALLEY),
      NoiseShaper.Point(-0.60, NoiseShaper.ShapingFunction.VALLEY),
      NoiseShaper.Point(-0.15, NoiseShaper.ShapingFunction.FLAT),
      NoiseShaper.Point(0.25, NoiseShaper.ShapingFunction.FLAT),
      NoiseShaper.Point(0.58, NoiseShaper.ShapingFunction.HILLS),
      NoiseShaper.Point(0.82, NoiseShaper.ShapingFunction.RIDGES),
      NoiseShaper.Point(1.0, NoiseShaper.ShapingFunction.HILLS)
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
        val wy = y.toDouble()

        // -------------------------
        // 1) Multi-stage domain warp
        // -------------------------
        val macroWarp = ctx.noise.get(Noise.MacroWarp2D)
        val microWarp = ctx.noise.get(Noise.MicroWarp2D)

        val mwx = macroWarp.noise2D(wx, wz) * macroWarpAmpBlocks
        val mwz = macroWarp.noise2D(wx + 2000.0, wz - 2000.0) * macroWarpAmpBlocks

        val x1 = wx + mwx
        val z1 = wz + mwz

        val swx = microWarp.noise2D(x1 + 700.0, z1 - 700.0) * microWarpAmpBlocks
        val swz = microWarp.noise2D(x1 - 900.0, z1 + 900.0) * microWarpAmpBlocks

        val xw = x1 + swx
        val zw = z1 + swz

        // -------------------------
        // 2) Broad terrain scaffold
        // -------------------------
        val contN = shaper.smoothShape(ctx.noise.get(Noise.Continent2D).noise2D(xw, zw))
        val plainsN = shaper.smoothShape(ctx.noise.get(Noise.Plains2D).noise2D(xw, zw))
        val ridgesN = shaper.smoothShape(ctx.noise.get(Noise.Ridges2D).noise2D(xw, zw))
        val roughN = ctx.noise.get(Noise.Rough2D).noise2D(wx, wz)

        val cont01 = ((contN + 1.0) * 0.5).coerceIn(0.0, 1.0)
        val plains01 = ((plainsN + 1.0) * 0.5).coerceIn(0.0, 1.0)
        val ridge01 = (1.0 - abs(ridgesN)).coerceIn(0.0, 1.0) // "ridge cores"

        // -------------------------
        // 3) Law zones (local terrain rule shifts)
        // -------------------------
        val lawRaw = ctx.noise.get(Noise.LawZone2D).noise2D(xw - 1337.0, zw + 7331.0) // [-1,1]
        val law01 = ((lawRaw + 1.0) * 0.5).coerceIn(0.0, 1.0)

        // Three soft masks that overlap a bit
        val compressZone = smoothstep01(((0.45 - abs(law01 - 0.20)) / 0.45).coerceIn(0.0, 1.0)) * lawZoneStrength
        val stretchZone = smoothstep01(((0.45 - abs(law01 - 0.55)) / 0.45).coerceIn(0.0, 1.0)) * lawZoneStrength
        val foldZone = smoothstep01(((0.45 - abs(law01 - 0.85)) / 0.45).coerceIn(0.0, 1.0)) * lawZoneStrength

        val compressNoise = ((ctx.noise.get(Noise.Compress2D).noise2D(xw, zw) + 1.0) * 0.5).coerceIn(0.0, 1.0)
        val stretchNoise = ((ctx.noise.get(Noise.Stretch2D).noise2D(xw, zw) + 1.0) * 0.5).coerceIn(0.0, 1.0)

        // -------------------------
        // 4) Fracture cuts (terrain looks "deleted")
        // -------------------------
        val fracWarp = ctx.noise.get(Noise.FractureWarp2D)
        val fx = xw + fracWarp.noise2D(xw + 5000.0, zw) * 70.0
        val fz = zw + fracWarp.noise2D(xw, zw - 5000.0) * 70.0

        val fractureRaw = ctx.noise.get(Noise.Fracture2D).noise2D(fx, fz) // [-1,1]
        // Convert to "line/tear" style mask by emphasizing center crossings
        val fractureLine01 = (1.0 - abs(fractureRaw)).coerceIn(0.0, 1.0)
        val fractureMask = smoothstep01(fractureLine01).pow(fractureSharpness)

        // Keep some fractures shallow, some catastrophic
        val fractureDepthMask = smoothstep01(((ridge01 * 0.6 + cont01 * 0.4) * 1.15).coerceIn(0.0, 1.0))
        val fractureCut = fractureMask * fractureCutAmp * (0.45 + 0.55 * fractureDepthMask)

        // -------------------------
        // 5) Surface offset (base topography)
        // -------------------------
        var offset = 0.0

        // Broad elevation
        offset += (cont01 - 0.5) * 2.0 * continentAmp

        // Broken plains / lumpy shelves
        offset += (plains01 - 0.5) * 2.0 * plainsAmp

        // Ridge influence (but not classic peaks)
        offset += ridge01.pow(1.8) * 18.0 * (0.65 + 0.35 * cont01)

        // Micro roughness
        offset += roughN * roughAmp

        // Law zones modify local terrain behavior
        // Compress zone: flattens vertical differences, creates eerie "squashed" regions
        if (compressZone > 0.0) {
          val t = (0.55 + 0.45 * (1.0 - compressNoise)).coerceIn(0.25, 1.0)
          offset *= (1.0 - 0.55 * compressZone * (1.0 - t))
        }

        // Stretch zone: exaggerates local relief in a patchy way
        if (stretchZone > 0.0) {
          val mult = 1.0 + (0.9 * stretchZone * stretchNoise)
          offset *= mult
        }

        // Fold zone: adds "buckled" shelves/folds horizontally (looks like terrain got bent)
        if (foldZone > 0.0) {
          val fold2d = ctx.noise.get(Noise.Fold3D).noise3D(xw * 0.8, wy * 0.02, zw * 0.8)
          offset += fold2d * 14.0 * foldZone
        }

        // Fracture deletions
        offset -= fractureCut

        // Optional terraces for "folded reality" vibe
        if (terraceStep > 0.0) {
          val q = floor(offset / terraceStep) * terraceStep
          offset = q * (1.0 - terraceBlend) + offset * terraceBlend
        }

        val surfaceY = baseSurface + offset

        // -------------------------
        // 6) Base density
        // -------------------------
        val base = surfaceY - wy
        var density = base

        // -------------------------
        // 7) Vertical distortion / pseudo inverted shelves
        // -------------------------
        // This doesn't fully replace a subshape overhang system; it adds "wrongness" in the core density.
        val fold3 = ctx.noise.get(Noise.Fold3D)
        val shear3 = ctx.noise.get(Noise.Shear3D)
        val void3 = ctx.noise.get(Noise.VoidPockets3D)

        // Height-relative bands around the notional surface create warped shelf regions
        val dy = wy - surfaceY
        val bandA = band(center01 = 0.35, halfWidth01 = 0.25, y01 = ((dy + 32.0) / 96.0).coerceIn(0.0, 1.0))
        val bandB = band(center01 = 0.62, halfWidth01 = 0.18, y01 = ((dy + 32.0) / 96.0).coerceIn(0.0, 1.0))

        val foldN = fold3.noise3D(xw, wy, zw)          // [-1,1]
        val shearN = shear3.noise3D(xw + 111.0, wy - 77.0, zw + 333.0)
        val voidN = void3.noise3D(xw - 444.0, wy + 222.0, zw - 999.0)

        // Folded "shelf add" (solids appearing where you'd expect emptiness)
        val shelfMask = (0.65 * bandA + 0.35 * bandB) * foldZone
        val shelfSignal = smoothstep01(((foldN + 1.0) * 0.5).coerceIn(0.0, 1.0)).pow(2.5)
        density += shelfSignal * shelfAddAmp * shelfMask

        // Shear undercuts: carve weird lateral voids beneath folds
        val undercutMask = smoothstep01(((shearN + 1.0) * 0.5).coerceIn(0.0, 1.0)).pow(3.0) * (0.35 + 0.65 * foldZone)
        density -= undercutMask * 12.0 * bandA

        // Pocket voids inside cliffs / shelves (eldritch "missing chunks")
        val voidMask = (1.0 - abs(voidN)).coerceIn(0.0, 1.0)
        val carvedPocket = smoothstep01(voidMask).pow(4.0)
        density -= carvedPocket * 10.0 * (0.25 + 0.75 * ridge01)

        // Edge blend support (if your edge alpha behaves this way)
        // If edge.fade01 exists in your system, multiply density or effects by it here.
        // For now we keep it neutral to avoid mismatching your interfaces.

        return DensityStack.densityStack(
          base = density,
          add = 0.0,
          carve = 0.0
        )
      }
    },

    // Reuse your existing overhang subshape system for extra impossible bridges / shelves
    listOf(
      AbyssStartOverhang(
        NoiseShaper(
          listOf(
            NoiseShaper.Point(-1.0, NoiseShaper.ShapingFunction.VALLEY),
            NoiseShaper.Point(-0.25, NoiseShaper.ShapingFunction.FLAT),
            NoiseShaper.Point(0.15, NoiseShaper.ShapingFunction.FLAT),
            NoiseShaper.Point(0.65, NoiseShaper.ShapingFunction.HILLS),
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

  // ---- Utility helpers (keep local if you want this file self-contained) ----

  private fun smoothstep01(t: Double): Double {
    val c = t.coerceIn(0.0, 1.0)
    return c * c * (3.0 - 2.0 * c)
  }

  private fun band(center01: Double, halfWidth01: Double, y01: Double): Double {
    val hw = halfWidth01.coerceAtLeast(1e-6)
    val t = (kotlin.math.abs(y01 - center01) / hw).coerceIn(0.0, 1.0)
    val s = t * t * (3.0 - 2.0 * t)
    return 1.0 - s
  }
}