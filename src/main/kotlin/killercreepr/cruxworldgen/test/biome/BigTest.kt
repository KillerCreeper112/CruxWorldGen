package killercreepr.cruxworldgen.test.biome

import io.papermc.paper.util.ItemComponentSanitizer.override
import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.biome.BiomeShape
import killercreepr.cruxworldgen.api.biome.BiomeShapeProfile
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.MaterialContext
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.material.MaterialProvider
import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.noise.NoiseField
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.noise.NoiseModule
import killercreepr.cruxworldgen.api.noise.Noised
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.api.util.Curve.bandMask
import killercreepr.cruxworldgen.api.util.Curve.bellMask
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockAdapter
import org.bukkit.Material
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

class AmplifiedBridgeTerrain(
  val baseHeight: Double = 30.0,
  val rollAmp: Double = 14.0,
  val ridgeAmp: Double = 20.0,

  val terraceStep: Double = 6.0,
  val terraceStrength: Double = 0.34,

  // Broad basin shaping
  val basinThreshold: Double = 0.60,
  val basinDepth: Double = 26.0,
  val rimLiftStrength: Double = 18.0,

  // Shelf / overhang layer
  val shelfAboveSurface: Double = 52.0,
  val shelfHalfThickness: Double = 26.0,
  val shelfStrength: Double = 270.0,
  val shelfThreshold: Double = 0.3,

  // How much lake centers suppress shelf formation
  val centerShelfSuppression: Double = 0.82,

  // Supports / attachment
  val supportStrength: Double = 112.0,

  // Underside and arch carving
  val undersideThreshold: Double = 0.54,
  val undersideStrength: Double = 84.0,

  val archThreshold: Double = 0.66,
  val archStrength: Double = 54.0,

  // Warp
  val warpAmpXZ: Double = 74.0,
  val warpAmpY: Double = 9.0,

  // Basin warp
  val basinWarpAmp: Double = 90.0
) : Biome.Noised {

  object Noise : NoiseModule {
    val Base2D = object : NoiseKey { override val id = "terrain.lakeshore_bridge.base2D" }
    val Ridge2D = object : NoiseKey { override val id = "terrain.lakeshore_bridge.ridge2D" }

    val Basin2D = object : NoiseKey { override val id = "terrain.lakeshore_bridge.basin2D" }
    val BasinWarpX2D = object : NoiseKey { override val id = "terrain.lakeshore_bridge.basin_warp_x2D" }
    val BasinWarpZ2D = object : NoiseKey { override val id = "terrain.lakeshore_bridge.basin_warp_z2D" }

    // Lets occasional bridge spans survive across basin space
    val Span2D = object : NoiseKey { override val id = "terrain.lakeshore_bridge.span2D" }

    val ShelfBody3D = object : NoiseKey { override val id = "terrain.lakeshore_bridge.shelf_body3D" }
    val Underside3D = object : NoiseKey { override val id = "terrain.lakeshore_bridge.underside3D" }
    val Arch3D = object : NoiseKey { override val id = "terrain.lakeshore_bridge.arch3D" }
    val Support2D = object : NoiseKey { override val id = "terrain.lakeshore_bridge.support2D" }

    val WarpX3D = object : NoiseKey { override val id = "terrain.lakeshore_bridge.warp_x3D" }
    val WarpY3D = object : NoiseKey { override val id = "terrain.lakeshore_bridge.warp_y3D" }
    val WarpZ3D = object : NoiseKey { override val id = "terrain.lakeshore_bridge.warp_z3D" }


    override fun install(bank: NoiseBank) {
      bank.register(Base2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.00155)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
          fractalLacunarity(2.0)
          fractalGain(0.5)
        }
      }

      bank.register(Ridge2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.00215)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(4)
          fractalLacunarity(2.0)
          fractalGain(0.5)
        }
      }

      bank.register(ShelfBody3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.001)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
          fractalLacunarity(2.0)
          fractalGain(0.5)
        }
      }

      bank.register(Underside3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0155)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(3)
          fractalLacunarity(2.0)
          fractalGain(0.5)
        }
      }

      bank.register(Arch3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0090)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
          fractalLacunarity(2.0)
          fractalGain(0.5)
        }
      }

      bank.register(Support2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0025)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
          fractalLacunarity(2.0)
          fractalGain(0.5)
        }
      }

      bank.register(WarpX3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0070)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
          fractalLacunarity(2.0)
          fractalGain(0.5)
        }
      }

      bank.register(WarpY3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0070)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
          fractalLacunarity(2.0)
          fractalGain(0.5)
        }
      }

      bank.register(WarpZ3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0070)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
          fractalLacunarity(2.0)
          fractalGain(0.5)
        }
      }

      bank.register(Basin2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.00072)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
          fractalLacunarity(2.0)
          fractalGain(0.5)
        }
      }

      bank.register(BasinWarpX2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.00135)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
          fractalLacunarity(2.0)
          fractalGain(0.5)
        }
      }

      bank.register(BasinWarpZ2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.00135)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
          fractalLacunarity(2.0)
          fractalGain(0.5)
        }
      }

      bank.register(Span2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.00155)
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(2)
          fractalLacunarity(2.0)
          fractalGain(0.5)
        }
      }
    }
  }

  override val noiseModule: NoiseModule = Noise

  override val materialProvider = object : MaterialProvider{
    override fun chooseMaterial(context: MaterialContext): BlockData {
      if(!context.isSolid) return BlockData.NONE

      if(context.depthBelowSurface == 0)
        return BukkitBlockAdapter.resolver().resolve(Material.GRASS_BLOCK)
      if(context.depthBelowSurface < 5)
        return BukkitBlockAdapter.resolver().resolve(Material.DIRT)

      return BukkitBlockAdapter.resolver().resolve(Material.STONE)
    }

  }

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
        val x = worldX.toDouble()
        val yy = y.toDouble()
        val z = worldZ.toDouble()

        // ------------------------------------------------------------
        // 1) Base macro terrain
        // ------------------------------------------------------------
        val baseN = ctx.noise.get(Noise.Base2D).noise2D(x, z)
        val ridgeN = ctx.noise.get(Noise.Ridge2D).noise2D(x, z)
        val ridge01 = (1.0 - abs(ridgeN)).pow(3.0)

        val rawSurfaceY = sea + baseHeight + baseN * rollAmp + ridge01 * ridgeAmp
        val terracedSurfaceY = Curve.lerp(
          rawSurfaceY,
          floor(rawSurfaceY / terraceStep) * terraceStep,
          ridge01 * terraceStrength
        )

        // ------------------------------------------------------------
        // 2) Broad lake basin mask
        //    - center gets lowered
        //    - rim gets boosted into cliffs
        // ------------------------------------------------------------
        val bwx = x + ctx.noise.get(Noise.BasinWarpX2D).noise2D(x, z) * basinWarpAmp
        val bwz = z + ctx.noise.get(Noise.BasinWarpZ2D).noise2D(x, z) * basinWarpAmp

        val basinNoise01 = (ctx.noise.get(Noise.Basin2D).noise2D(bwx, bwz) + 1.0) * 0.5
        val lakeMask = Curve.smoothstep(basinThreshold, 0.84, basinNoise01)

        // Strongest in basin center
        val lakeCenterMask = smoothstep01(lakeMask)

        // Ring around the basin edge for cliffs / overhang framing
        val lakeRimMask = bellMask(lakeMask, center = 0.58, half = 0.24)

        // Pull center downward into lake bowl
        val basinBowl = lakeCenterMask * basinDepth

        // Lift the rim so lakes are framed by cliffs instead of flat banks
        val rimLift = lakeRimMask * rimLiftStrength

        val surfaceY = terracedSurfaceY - basinBowl + rimLift
        val baseTerrain = surfaceY - yy

        // ------------------------------------------------------------
        // 3) Shelf placement relative to new lakeshore surface
        // ------------------------------------------------------------
        val terrainHeight01 = ((surfaceY - sea - 16.0) / 95.0).coerceIn(0.0, 1.0)
        val macroAnchor = smoothstep01((terrainHeight01 * 0.42 + ridge01 * 0.58).coerceIn(0.0, 1.0))

        // Occasional basin-crossing spans
        val spanNoise01 = (ctx.noise.get(Noise.Span2D).noise2D(bwx * 0.82, bwz * 0.82) + 1.0) * 0.5
        val spanMask = Curve.smoothstep(0.60, 0.82, spanNoise01) * lakeMask

        val shelfCenter =
          surfaceY +
            shelfAboveSurface +
            ridge01 * 8.0 +
            lakeRimMask * 12.0 +
            spanMask * 6.0

        val shelfBand = bandMask(yy, shelfCenter, shelfHalfThickness)

        // Prefer shelves around the rim and on dramatic terrain
        val shelfAnchor =
          (macroAnchor * 0.65 + lakeRimMask * 0.55 + spanMask * 0.30)
            .coerceIn(0.0, 1.0)

        // Suppress shelf formation over calm lake center,
        // but let span corridors break that rule sometimes
        val shelfRegionMask =
          (1.0 - lakeCenterMask * centerShelfSuppression + spanMask * 0.65)
            .coerceIn(0.0, 1.0)

        // Domain warp for upper mass shape
        val wx = x + ctx.noise.get(Noise.WarpX3D).noise3D(x, yy, z) * warpAmpXZ
        val wy = yy + ctx.noise.get(Noise.WarpY3D).noise3D(x, yy, z) * warpAmpY
        val wz = z + ctx.noise.get(Noise.WarpZ3D).noise3D(x, yy, z) * warpAmpXZ

        val shelfNoise01 =
          (ctx.noise.get(Noise.ShelfBody3D).noise3D(wx, wy * 0.86, wz) + 1.0) * 0.5

        val shelfSolid = Curve.smoothstep(shelfThreshold, 0.86, shelfNoise01)
        val elevatedShelf =
          shelfBand *
            shelfAnchor *
            //shelfRegionMask *
            shelfSolid *
            shelfStrength

        // ------------------------------------------------------------
        // 4) Connector supports
        //    Stronger near rim and span zones so shelves stay attached.
        // ------------------------------------------------------------
        val supportNoise01 = (ctx.noise.get(Noise.Support2D).noise2D(x, z) + 1.0) * 0.5
        val supportSeed = Curve.smoothstep(0.60, 0.84, supportNoise01)

        val supportMask =
          supportSeed *
            (macroAnchor * 0.45 + lakeRimMask * 0.45 + spanMask * 0.35)
              .coerceIn(0.0, 1.0)

        val supportCenter = (surfaceY + shelfCenter) * 0.5
        val supportHalf = max(12.0, (shelfCenter - surfaceY) * 0.58)
        val supportBand = bandMask(yy, supportCenter, supportHalf)

        val connectorSupports = supportMask * supportBand * supportStrength

        // ------------------------------------------------------------
        // 5) Underside erosion
        //    Extra bite around basin rims so the lake gets framed by huge ceilings.
        // ------------------------------------------------------------
        val undersideBias =
          smoothstep01(((shelfCenter - yy) / shelfHalfThickness).coerceIn(0.0, 1.0))

        val undersideNoise01 =
          (ctx.noise.get(Noise.Underside3D).noise3D(wx, wy * 0.92, wz) + 1.0) * 0.5

        val undersideLocalStrength =
          undersideStrength * (1.0 + lakeRimMask * 0.28 + spanMask * 0.12)

        val undersideCarve =
          Curve.smoothstep(undersideThreshold, 0.84, undersideNoise01) *
            shelfBand *
            undersideBias *
            shelfAnchor *
            undersideLocalStrength

        // ------------------------------------------------------------
        // 6) Arch windows / open spans
        // ------------------------------------------------------------
        val archNoise01 =
          (ctx.noise.get(Noise.Arch3D).noise3D(wx * 0.72, wy * 0.42, wz * 0.72) + 1.0) * 0.5

        val archLocalStrength =
          archStrength * (1.0 + lakeRimMask * 0.18 + spanMask * 0.20)

        val archCarve =
          Curve.smoothstep(archThreshold, 0.88, archNoise01) *
            shelfBand *
            shelfAnchor *
            archLocalStrength

        // ------------------------------------------------------------
        // 7) Final density
        // ------------------------------------------------------------
        val finalDensity =
          baseTerrain +
            elevatedShelf +
            connectorSupports -
            undersideCarve -
            archCarve

        // If your project uses a different helper, only swap this line.
        return DensityStack.densityStack(
          base = finalDensity
        )
      }
    },
    types = listOf()
  )

  /*private fun bandMask(y: Double, center: Double, half: Double): Double {
    if (half <= 0.0) return 0.0
    val t = ((half - abs(y - center)) / half).coerceIn(0.0, 1.0)
    return smoothstep01(t)
  }

  private fun smoothstep01(x: Double): Double {
    val t = x.coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
  }

  private fun lerp(a: Double, b: Double, t: Double): Double {
    return a + (b - a) * t.coerceIn(0.0, 1.0)
  }*/
}