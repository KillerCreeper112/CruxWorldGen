package killercreepr.cruxworldgen.test.biome

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.biome.BiomeShape
import killercreepr.cruxworldgen.api.biome.BiomeShapeProfile
import killercreepr.cruxworldgen.api.biome.BiomeShapeType
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.cave.CaveProfile
import killercreepr.cruxworldgen.api.cave.CaveShape
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.MaterialContext
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.density.DensityBank
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.material.MaterialProvider
import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.noise.NoiseField
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.noise.NoiseModule
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.Curve.band
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import killercreepr.cruxworldgen.api.util.NoiseShaper
import killercreepr.cruxworldgen.api.util.NoiseShaper.Point
import killercreepr.cruxworldgen.api.util.NoiseShaper.ShapingFunction
import killercreepr.cruxworldgen.bukkit.biome.BukkitBiome
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import org.bukkit.Material
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

class TestAmp(
  override val caves: CaveShape = CaveProfile(buildList { }),
  override val decorations: List<Decoration> = listOf(),

  override val materialProvider: MaterialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): BlockData {
      if (!context.isSolid) return BlockData.NONE

      // Simple “vanilla-ish amplified”: grass/dirt up top, stone/basalt on steep/high.
      val depth = context.depthBelowSurface

      // crude cliff/high-alt rule of thumb
      val isHigh = context.surfaceY >= context.generateContext.chunkContext.seaLevel + 110
      val isCliff = context.airBlocksAbove <= 1 && depth == 0 && context.caveAirBlocksBelow >= 6

      return when {
        depth <= 0 && (isHigh || isCliff) -> BukkitBlockResolver.INSTANCE.resolve(Material.STONE)
        depth <= 0 -> BukkitBlockResolver.INSTANCE.resolve(Material.GRASS_BLOCK)
        depth <= 3 -> BukkitBlockResolver.INSTANCE.resolve(Material.DIRT)
        else -> BukkitBlockResolver.INSTANCE.resolve(Material.STONE)
      }
    }
  },

  // ===== AMPLIFIED macro knobs =====
  private val baseHeight: Double = 66.0,     // around sea-ish baseline
  private val rollAmp: Double = 42.0,        // broad hills
  private val ridgeAmp: Double = 100.0,      // BIG mountains
  private val verticalExaggeration: Double = 1.3, // makes everything taller

  // deep cuts / valleys (optional)
  private val cutAmp: Double = 55.0
) : Biome.Noised, BukkitBiome {

  override fun toBukkitBiome(): org.bukkit.block.Biome = org.bukkit.block.Biome.MEADOW

  object Noise : NoiseModule {
    object Roll2D : NoiseKey { override val id = "biome.amplified.roll2D" }
    object Ridge2D : NoiseKey { override val id = "biome.amplified.ridge2D" }
    object Cut2D : NoiseKey { override val id = "biome.amplified.cut2D" }
    object Warp2D : NoiseKey { override val id = "biome.amplified.warp2D" }

    // floating-island system
    object IslandRegion2D : NoiseKey { override val id = "biome.amplified.island.region2D" }
    object IslandHeight2D : NoiseKey { override val id = "biome.amplified.island.height2D" }
    object IslandBlob3D : NoiseKey { override val id = "biome.amplified.island.blob3D" }
    object IslandWindows3D : NoiseKey { override val id = "biome.amplified.island.windows3D" }

    // bridge system
    object BridgeRegion2D : NoiseKey { override val id = "biome.amplified.bridge.region2D" }
    object BridgeCave3D : NoiseKey { override val id = "biome.amplified.bridge.cave3D" }
    object BridgePillar2D : NoiseKey { override val id = "biome.amplified.bridge.pillar2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Roll2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0012)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3)
            .fractalGain(0.5)
            .fractalLacunarity(2.0)
        }
      }
      bank.register(Ridge2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0020)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.Ridged)
            .fractalOctaves(3)
            .fractalGain(0.55)
            .fractalLacunarity(2.05)
        }
      }
      bank.register(Cut2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0024)
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(Warp2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0009)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }

      // --- islands: BIG coherent blobs (LOW freq 3D is essential) ---
      bank.register(IslandRegion2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0012) // huge patches
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(IslandHeight2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0015) // drift island layer height slowly
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(IslandBlob3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0009) // <-- THIS controls island “size”. Smaller = bigger islands.
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(IslandWindows3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.015) // holes/arches detail
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3)
        }
      }

      // --- bridges ---
      bank.register(BridgeRegion2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0022)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(BridgeCave3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.018)
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(BridgePillar2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.007)
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
    }
  }

  override val noiseModule = Noise

  private val shaper = NoiseShaper(
    listOf(
      Point(-1.0, ShapingFunction.VALLEY),
      Point(-0.25, ShapingFunction.FLAT),
      Point(0.0, ShapingFunction.FLAT),
      Point(0.65, ShapingFunction.HILLS),
      Point(1.0, ShapingFunction.MOUNTAIN)
    )
  )

  override val shape = BiomeShapeProfile(
    base = object : BiomeShape {
      override fun density(
        ctx: GenerateContext,
        worldX: Int,
        y: Int,
        worldZ: Int,
        edge: BiomeEdgeContext,
        signalWriter: SignalWriter
      ): DensityStack {
        val sea = ctx.chunkContext.seaLevel

        // domain warp for less “samey” ridges
        val wx = worldX.toDouble()
        val wz = worldZ.toDouble()
        val warp = ctx.noise.get(Noise.Warp2D).noise2D(wx, wz)
        val xw = wx + warp * 220.0
        val zw = wz + warp * 220.0

        // roll (broad)
        val rollN = shaper.smoothShape(ctx.noise.get(Noise.Roll2D).noise2D(xw, zw))
        val rollY = rollN * rollAmp

        // ridges (peaks) -> exaggerate strongly
        val ridgeN = shaper.smoothShape(ctx.noise.get(Noise.Ridge2D).noise2D(xw, zw))
        val ridge01 = (1.0 - abs(ridgeN)).coerceIn(0.0, 1.0)
        val ridgeBoost = ridge01.pow(4.0) // makes fewer but MUCH taller peaks
        val ridgeY = ridgeBoost * ridgeAmp

        // deep cuts/valleys
        val cutN = ctx.noise.get(Noise.Cut2D).noise2D(xw + 999.0, zw - 999.0) // -1..1
        val cut01 = (1.0 - abs(cutN)).coerceIn(0.0, 1.0)
        val cutY = cut01.pow(3.0) * cutAmp

        // amplified macro surface
        var surfaceY = sea + baseHeight + rollY + ridgeY
        surfaceY = sea + (surfaceY - sea) * verticalExaggeration
        surfaceY -= cutY

        val baseDensity = surfaceY - y.toDouble()
        return DensityStack.densityStack(base = baseDensity, add = 0.0, carve = 0.0)
      }
    },
    types = listOf(
      // Big “connected floating island” mass above mountains
      AmplifiedFloatingIslandsModifier(
        region2D = Noise.IslandRegion2D,
        height2D = Noise.IslandHeight2D,
        blob3D = Noise.IslandBlob3D,
        windows3D = Noise.IslandWindows3D,

        minMacroSurfaceY = 80.0,     // only when terrain is tall enough
        islandAboveSurface = 26.0,   // base “hover” height above macro surface
        islandHeightAmp = 34.0,      // drift up/down
        stepBlocks = 14.0,           // flatten into layers (0 disables)

        slabHalfThickness = 26.0,    // thick island layer
        regionThreshold = 0.12,
        regionPower = 2.2,

        blobThreshold = 0.56,        // higher => fewer islands, larger “cores”
        blobPower = 2.3,

        addStrength = 750.0,         // KEY: must beat (y - surfaceY) often
        undercutStrength = 350.0,    // big void under it so it reads as an overhang
        undercutOffset = 50.0,
        undercutHalfThickness = 18.0,

        windowThreshold = 0.62,      // holes/windows inside islands
        windowStrength = 240.0
      ),

      // Bridges / shelves near peaks for extra “amplified chaos”
      AmplifiedBridgeModifier(
        mask2D = Noise.BridgeRegion2D,
        cave3D = Noise.BridgeCave3D,
        pillar2D = Noise.BridgePillar2D,

        minMacroSurfaceY = 95.0,
        bridgeBelowSurface = 10.0,
        slabHalfWidth = 14.0,

        slabStrength = 140.0,
        caveThreshold = 0.64,
        caveStrength = 220.0,

        pillarStrength = 80.0
      )
    )
  )
}

class AmplifiedFloatingIslandsModifier(
  private val region2D: NoiseKey,
  private val height2D: NoiseKey,
  private val blob3D: NoiseKey,
  private val windows3D: NoiseKey? = null,

  private val minMacroSurfaceY: Double = 80.0,

  private val islandAboveSurface: Double = 22.0,
  private val islandHeightAmp: Double = 30.0,
  private val stepBlocks: Double = 12.0,

  private val slabHalfThickness: Double = 24.0,

  private val regionThreshold: Double = 0.15,
  private val regionPower: Double = 2.0,

  // blob3D is a 0..1 thresholded field; higher threshold => fewer cores => bigger masses
  private val blobThreshold: Double = 0.58,
  private val blobPower: Double = 2.0,

  // Strength is in “density units”.
  // To make solid at 60 blocks above surface, you need addStrength commonly >= 60.
  // For thick islands, you usually want 200-700+ depending on your macro scale.
  private val addStrength: Double = 520.0,

  // Undercut: makes it read as a shelf/island (big air volume beneath)
  private val undercutStrength: Double = 240.0,
  private val undercutOffset: Double = 26.0,
  private val undercutHalfThickness: Double = 16.0,

  // Windows/arches inside the island body
  private val windowThreshold: Double = 0.62,
  private val windowStrength: Double = 220.0
) : BiomeShapeType {

  override fun density(
    ctx: GenerateContext,
    worldX: Int,
    y: Int,
    worldZ: Int,
    edge: BiomeEdgeContext,
    signalWriter: SignalWriter,
    baseStack: DensityStack,
    out: DensityBank
  ) {
    // baseStack.base = macroSurfaceY - y
    val base = baseStack.base
    val macroSurfaceY = y.toDouble() + base
    if (macroSurfaceY < minMacroSurfaceY) return

    // --- region gate (big patches) ---
    val rm = ctx.noise.get(region2D).noise2D(worldX, worldZ) // -1..1
    val rm01 = (1.0 - abs(rm)).coerceIn(0.0, 1.0)          // 0..1
    val region = ((rm01 - regionThreshold) / (1.0 - regionThreshold)).coerceIn(0.0, 1.0)
    val regionShaped = smoothstep01(region).pow(regionPower)
    if (regionShaped <= 1e-6) return

    // --- choose island plane (above macro surface) ---
    val hN = ctx.noise.get(height2D).noise2D(worldX + 1337, worldZ - 777) // -1..1
    var islandY = macroSurfaceY + islandAboveSurface + hN * islandHeightAmp
    if (stepBlocks > 0.0) islandY = floor(islandY / stepBlocks) * stepBlocks

    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1
    val H = (maxY - minY + 1).toDouble()
    val y01 = (y - minY) / H

    // vertical slab band where island lives
    val slab = band(
      center01 = (islandY - minY) / H,
      halfWidth01 = slabHalfThickness / H,
      y01 = y01
    )
    if (slab <= 1e-6) return

    // --- blob volume (this defines “island footprint”) ---
    val n = ctx.noise.get(blob3D).noise3D(worldX, y, worldZ) // -1..1
    val n01 = (n * 0.5 + 0.5).coerceIn(0.0, 1.0)
    val blob = smoothstep01(((n01 - blobThreshold) / (1.0 - blobThreshold)).coerceIn(0.0, 1.0))
      .pow(blobPower)
    if (blob <= 1e-6) return

    // --- anchoring / connection gate ---
    // Above macro surface => base is negative. We *want* to add in air,
    // but also allow shallow rock so it connects to mountains.
    val heightAboveSurface = (-base) // >0 in air
    val airGate = smoothstep01(((heightAboveSurface + 4.0) / 18.0).coerceIn(0.0, 1.0))

    val insideRock = max(0.0, base) // how far inside macro-solid we are
    val rockGate = smoothstep01(((10.0 - insideRock) / 10.0).coerceIn(0.0, 1.0))

    val connectGate = max(airGate, rockGate * 0.7)
    if (connectGate <= 1e-6) return

    // --- add island mass ---
    out.addAdditive(regionShaped * slab * blob * connectGate * addStrength)

    // --- carve a BIG undercut band below island so it reads as “overhang/island” ---
    val under = band(
      center01 = ((islandY - undercutOffset) - minY) / H,
      halfWidth01 = undercutHalfThickness / H,
      y01 = y01
    )
    out.addCarve(regionShaped * under * blob * undercutStrength)

    // --- windows / arches inside island body (optional) ---
    if (windows3D != null) {
      val w = ctx.noise.get(windows3D).noise3D(worldX, y, worldZ) // -1..1
      val w01 = (w * 0.5 + 0.5).coerceIn(0.0, 1.0)
      val hole = smoothstep01(((w01 - windowThreshold) / (1.0 - windowThreshold)).coerceIn(0.0, 1.0))
      out.addCarve(regionShaped * slab * blob * hole * windowStrength)
    }
  }
}
class AmplifiedBridgeModifier(
  private val mask2D: NoiseKey,
  private val cave3D: NoiseKey,
  private val pillar2D: NoiseKey,

  private val minMacroSurfaceY: Double = 95.0,
  private val bridgeBelowSurface: Double = 10.0,
  private val slabHalfWidth: Double = 14.0,

  private val slabStrength: Double = 140.0,

  private val caveThreshold: Double = 0.64,
  private val caveStrength: Double = 220.0,

  private val pillarStrength: Double = 80.0
) : BiomeShapeType {

  override fun density(
    ctx: GenerateContext,
    worldX: Int,
    y: Int,
    worldZ: Int,
    edge: BiomeEdgeContext,
    signalWriter: SignalWriter,
    baseStack: DensityStack,
    out: DensityBank
  ) {
    val base = baseStack.base
    val macroSurfaceY = y.toDouble() + base
    if (macroSurfaceY < minMacroSurfaceY) return

    // region gate
    val mN = ctx.noise.get(mask2D).noise2D(worldX, worldZ)
    val m01 = 1.0 - abs(mN)
    val region = ((m01 - 0.35) / 0.65).coerceIn(0.0, 1.0)
    val region2 = region * region
    if (region2 <= 1e-6) return

    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1
    val H = (maxY - minY + 1).toDouble()
    val y01 = (y - minY) / H

    val bridgeY = macroSurfaceY - bridgeBelowSurface
    val slab = band(
      center01 = (bridgeY - minY) / H,
      halfWidth01 = slabHalfWidth / H,
      y01 = y01
    )
    if (slab <= 1e-6) return

    // add slab
    out.addAdditive(region2 * slab * slabStrength)

    // carve arches/windows in slab
    val c = ctx.noise.get(cave3D).noise3D(worldX, y, worldZ) // -1..1
    val c01 = (c * 0.5 + 0.5).coerceIn(0.0, 1.0)
    val hole = ((c01 - caveThreshold) / (1.0 - caveThreshold)).coerceIn(0.0, 1.0)
    out.addCarve(region2 * slab * hole * hole * caveStrength)

    // pillars (optional)
    val p = ctx.noise.get(pillar2D).noise2D(worldX, worldZ)
    val p01 = (1.0 - abs(p)).coerceIn(0.0, 1.0)
    val pillar = ((p01 - 0.78) / 0.22).coerceIn(0.0, 1.0)

    val below = ((bridgeY - y.toDouble()) / 90.0).coerceIn(0.0, 1.0)
    out.addAdditive(region2 * pillar * below * pillarStrength)
  }
}

