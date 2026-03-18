package killercreepr.cruxworldgen.test.biome

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.biome.BiomeShape
import killercreepr.cruxworldgen.api.biome.BiomeShapeProfile
import killercreepr.cruxworldgen.api.biome.FineBiomeShape
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.cave.CaveProfile
import killercreepr.cruxworldgen.api.cave.CaveShape
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.MaterialContext
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
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
import killercreepr.cruxworldgen.api.util.Curve.smoothstep
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import killercreepr.cruxworldgen.api.util.HashUtil
import killercreepr.cruxworldgen.api.util.HashUtil.hash01
import killercreepr.cruxworldgen.api.util.NoiseShaper
import killercreepr.cruxworldgen.bukkit.biome.BukkitBiome
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockAdapter
import killercreepr.cruxworldgen.extension.remap01
import killercreepr.cruxworldgen.standard.cave.SpaghettiCaves
import killercreepr.cruxworldgen.standard.cave.Standard3DCaves
import killercreepr.cruxworldgen.standard.cave.WormCaves
import org.bukkit.Material
import kotlin.math.*

class CharredWastes(
  override val caves: CaveShape<*, *> = CaveProfile(
    listOf(
      /*SpaghettiCaves(),
      WormCaves(),
      Standard3DCaves(),*/
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
        x, y + 1, z,
        Signal.CRACK_MAGMA, 0.0
      )
      if (crackMagma > 0.8 && context.depthBelowSurface < 9) {
        if (context.generateContext.random.nextDouble() < 0.2)
          return BukkitBlockAdapter.resolver().resolve(Material.LAVA)
        return BukkitBlockAdapter.resolver().resolve(Material.MAGMA_BLOCK)
      }

      if (context.depthBelowSurface <= 0)
        return BukkitBlockAdapter.resolver().resolve(Material.BLACKSTONE)
      if (context.depthBelowSurface <= 2)
        return BukkitBlockAdapter.resolver().resolve(Material.BASALT)

      return BukkitBlockAdapter.resolver().resolve(Material.BASALT)
    }
  },

  // ===== Plateau knobs =====
  private val baseHeight: Double = 70.0,     // how high above sea level the wastes sit
  private val rollAmp: Double = 18.0,        // broad rolling
  private val ridgeAmp: Double = 25.0,       // raised ridges/knuckles

  // ===== Crack knobs (height depressions) =====
  private val crackThreshold01: Double = 0.82, // higher => fewer cracks
  private val crackDepth: Double = 12.0,       // how deep the cracks depress the surface
  private val crackWarpAmp: Double = 14.0,     // meander cracks

  // ===== Fissure carve knobs =====
  private val fissureThreshold01: Double = 0.83, // higher => rarer/thinner fissures
  private val fissureDepth: Double = 40.0,       // how far down the slit carves
  private val fissureStrength: Double = 26.0,    // how strongly it punches open
  private val fissureWallSoftness: Double = 1.1,  // higher => sharper walls
  // ===== Ring arch knobs =====
  private val ringCellSize: Int = 80,             // spacing between possible arch cells
  private val ringChancePerCell: Double = 0.3,        // lower = rarer
  private val ringAddStrength: Double = 50.0,          // density added when inside the arch body
  private val ringBaseHeightMin: Double = 10.0,        // min center above local surface
  private val ringBaseHeightMax: Double = 24.0,        // max center above local surface
  private val ringRadiusMin: Double = 4.0,
  private val ringRadiusMax: Double = 9.0,
  private val ringTubeRadiusMin: Double = 3.0,
  private val ringTubeRadiusMax: Double = 6.0,
  private val ringPlaneThicknessMin: Double = 3.0,     // thickness perpendicular to the ring plane
  private val ringPlaneThicknessMax: Double = 6.0,
  private val ringVerticalSquashMin: Double = 0.75,    // <1 squashes vertically, >1 stretches
  private val ringVerticalSquashMax: Double = 1.15,
  private val ringSalt: Long = 91824561L,
  ) : Biome.Noised, BukkitBiome {

  override fun toBukkitBiome() = org.bukkit.block.Biome.BASALT_DELTAS

  object Signal {
    val CRACK_MAGMA = SignalKey.doubleSignalKey()
  }

  object Noise : NoiseModule {
    object FissureWarp2D : NoiseKey {
      override val id = "biome.charred_wastes.fissure.warp2D"
    }

    object FissureMask2D : NoiseKey {
      override val id = "biome.charred_wastes.fissure.mask2D"
    }

    object Base2D : NoiseKey {
      override val id = "biome.charred_wastes.base2D"
    }

    object Ridge2D : NoiseKey {
      override val id = "biome.charred_wastes.ridge2D"
    }

    object CrackWarp2D : NoiseKey {
      override val id = "biome.charred_wastes.crack.warp2D"
    }

    object CrackMask2D : NoiseKey {
      override val id = "biome.charred_wastes.crack.mask2D"
    }

    object Overhang3D : NoiseKey {
      override val id = "biome.charred_wastes.overhang3D"
    }

    object Arch3D : NoiseKey {
      override val id = "biome.charred_wastes.arch3D"
    }

    object OverhangHeight2D : NoiseKey {
      override val id = "biome.charred_wastes.overhang.height2D"
    }

    object OverhangMask2D : NoiseKey {
      override val id = "biome.charred_wastes.overhang.mask2D"
    }

    object OverhangWarp2D : NoiseKey {
      override val id = "biome.charred_wastes.overhang.warp2D"
    }

    override fun install(bank: NoiseBank) {
      bank.register(Base2D) { seed ->
        NoiseField.Companion.noiseField(seed) {
          frequency(0.0017) // big rolling, ~700 block wavelength
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3)
            .fractalGain(0.5)
            .fractalLacunarity(2.0)
        }
      }
      bank.register(Ridge2D) { seed ->
        NoiseField.Companion.noiseField(seed) {
          frequency(0.0019)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.Ridged)
            .fractalOctaves(3)
            .fractalGain(0.5)
            .fractalLacunarity(2.0)
        }
      }
      bank.register(CrackWarp2D) { seed ->
        NoiseField.Companion.noiseField(seed) {
          frequency(0.0028)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(CrackMask2D) { seed ->
        NoiseField.Companion.noiseField(seed) {
          frequency(0.018)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(FissureWarp2D) { seed ->
        NoiseField.Companion.noiseField(seed) {
          frequency(0.0018)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(FissureMask2D) { seed ->
        NoiseField.Companion.noiseField(seed) {
          frequency(0.0065) // lower = longer, fewer fissures
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(Overhang3D) { seed ->
        NoiseField.Companion.noiseField(seed) {
          frequency(0.05)
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(OverhangMask2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.02)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }

      bank.register(OverhangHeight2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.006)
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }

      bank.register(OverhangWarp2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.005)
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3)
        }
      }
      bank.register(Arch3D) { seed ->
        NoiseField.Companion.noiseField(seed) {
          frequency(0.026)
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
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
      NoiseShaper.Point(0.35, NoiseShaper.ShapingFunction.FLAT),
      NoiseShaper.Point(0.65, NoiseShaper.ShapingFunction.HILLS),
      NoiseShaper.Point(0.82, NoiseShaper.ShapingFunction.HILLS),
      NoiseShaper.Point(0.92, NoiseShaper.ShapingFunction.FLAT),
      NoiseShaper.Point(1.0, NoiseShaper.ShapingFunction.FLAT)
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

        val fissure01 = fissureMask01(ctx, worldX, y, worldZ, surfaceY)
        val fissureCarve = fissure01.pow(fissureWallSoftness) * fissureStrength

        // ----- 3) Magma fissure SLITS (macro carve) -----
        // Similar to cracks but used as a density carve so it opens to sky.

        // Base density is simple surface field
        var aboveSurface = (y - surfaceY).coerceAtLeast(0.0)
        val gradientBias = aboveSurface * 0.25
        val baseDensity = surfaceY - y.toDouble() - gradientBias

        aboveSurface = (-baseDensity).coerceAtLeast(0.0)

        val oh = ctx.noise.get(Noise.Overhang3D).noise3D(worldX, y, worldZ).remap01() * 50.0
        val ohFinal = oh.coerceAtLeast(0.0)

        return DensityStack.densityStack(
          base = baseDensity,
          add = 0.0,
          carve = fissureCarve + ohFinal
        )
      }
    },
    listOf(
    )
  )

  override val fineShape = object: FineBiomeShape{
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      edge: BiomeEdgeContext,
      volCtx: VolumeEnv,
      signalWriter: SignalWriter
    ): DensityStack {
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
      //surfaceY -= crackShape * crackDepth

      signalWriter.max(
        worldX, y, worldZ,
        Signal.CRACK_MAGMA,
        crackLine
      )

      return DensityStack.densityStack(
        base = 0.0,
        add = 0.0,
        carve = (crackShape * crackDepth)
      )
    }
  }

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

  private fun ringArchDensity(
    ctx: GenerateContext,
    wx: Int,
    y: Int,
    wz: Int
  ): Double {
    // optional: only allow these high in the charred plateau
    //if (localSurfaceY < ctx.chunkContext.seaLevel + 45.0) return 0.0

    val cellX = Math.floorDiv(wx, ringCellSize)
    val cellZ = Math.floorDiv(wz, ringCellSize)

    var best = 0.0

    // search neighboring cells so arches can affect across borders
    for (dz in -1..1) {
      for (dx in -1..1) {
        val cx = cellX + dx
        val cz = cellZ + dz

        val seed = HashUtil.hash2D(ctx.worldContext.seed xor ringSalt, cx, cz)

        if (!HashUtil.chance(seed, ringChancePerCell)) continue

        // jitter the arch center inside the cell
        val jx = ((hash01(seed xor 0x1A2B3C4DL) - 0.5) * ringCellSize * 0.7)
        val jz = ((hash01(seed xor 0x5E6F7788L) - 0.5) * ringCellSize * 0.7)

        val centerX = cx * ringCellSize + ringCellSize / 2.0 + jx
        val centerZ = cz * ringCellSize + ringCellSize / 2.0 + jz

        // random orientation in XZ
        val yaw = hash01(seed xor 0xCAFEBABEL) * Math.PI * 2.0
        val cosY = cos(yaw)
        val sinY = sin(yaw)

        // shape parameters
        val radius = Curve.lerp(ringRadiusMin, ringRadiusMax, hash01(seed xor 0x11111111L))
        val tubeRadius = Curve.lerp(ringTubeRadiusMin, ringTubeRadiusMax, hash01(seed xor 0x22222222L))
        val planeThickness = Curve.lerp(ringPlaneThicknessMin, ringPlaneThicknessMax, hash01(seed xor 0x33333333L))
        val verticalSquash = Curve.lerp(ringVerticalSquashMin, ringVerticalSquashMax, hash01(seed xor 0x44444444L))

        val anchorSurfaceY = surfaceYAt(
          ctx,
          round(centerX).toInt(),
          round(centerZ).toInt()
        )

        val centerY = anchorSurfaceY + Curve.lerp(
          ringBaseHeightMin,
          ringBaseHeightMax,
          hash01(seed xor 0x55555555L)
        )

        // local coords relative to ring center
        val rx = wx - centerX
        val rz = wz - centerZ
        val ry = y - centerY

        // rotate into ring-aligned space
        val u = rx * cosY + rz * sinY        // in ring plane
        val w = -rx * sinY + rz * cosY       // perpendicular to ring plane
        val v = ry / verticalSquash          // vertical squash/stretch

        // Vertical torus-ish SDF:
        // ring loop lives in (u,v), thickness extends along w
        val q = sqrt(u * u + v * v) - radius

        // normalize perpendicular thickness so the ring isn't infinitely thin
        val sdf = sqrt(q * q + (w / planeThickness) * (w / planeThickness)) - tubeRadius

        // Only contribute if we're inside the body
        if (sdf < 0.0) {
          // stronger toward center of stone body
          var add = (-sdf) * ringAddStrength

          val terrainDensity = surfaceYAt(ctx, wx, wz) - y
          val attach = smoothstep(-8.0, 6.0, terrainDensity)

          add *= attach

          // soften the very bottom so it merges into terrain instead of making a perfect loop outline
          val bottomFade = smoothstep(-radius * 0.95, -radius * 0.45, v)
          add *= bottomFade

          // slight breakup so they aren't too mathematically perfect
          val breakup = ctx.noise.get(Noise.Overhang3D).noise3D(wx, y, wz) * 0.5 + 0.5
          add *= smoothstep(0.20, 0.85, breakup)

          if (add > best) best = add
        }
      }
    }

    return best
  }

  private fun surfaceYAt(ctx: GenerateContext, worldX: Int, worldZ: Int): Double {
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

    return surfaceY
  }
}