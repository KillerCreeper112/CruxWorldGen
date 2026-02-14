package killercreepr.cruxworldgen.test6.biome

import killercreepr.cruxworldgen.test6.context.GenerateContext
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.test6.density.DensityStack
import killercreepr.cruxworldgen.test6.material.MaterialContext
import killercreepr.cruxworldgen.test6.material.MaterialProvider
import org.bukkit.Material
import kotlin.math.pow

class OverhangMountains(
  override val caves: CaveShape = gCaves,
  override val decorations: List<Decoration> = listOf(),
  override val materialProvider: MaterialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): Material {
      return if (context.isSolid) Material.STONE else Material.AIR
    }
  },

  // --- Base mountain knobs ---
  private val baseHeight: Double = 42.0,
  private val baseAmp: Double = 28.0,
  private val ridgeAmp: Double = 110.0,

  // --- Shelf placement knobs (ABSOLUTE HEIGHT, not surface-relative) ---
  private val shelfBaseYAboveSea: Double = 140.0,   // try 70..140
  private val shelfYRange: Double = 55.0,          // try 30..90
  private val shelfHalfThicknessMin: Double = 4.0,
  private val shelfHalfThicknessMax: Double = 10.0,

  // --- Shelf distribution knobs ---
  private val shelfThreshold01: Double = 0.5,     // higher = fewer shelf bands
  private val shelfWarpAmp: Double = 22.0,
  private val shelfStrength: Double = 30.0,        // try 18..60 (don’t start at 90)

  // Chunkiness / breakup
  private val blobThreshold01: Double = 0.58,
  private val blobStrength: Double = 1.0,

  // Hollow underside (only when shelf dominates)
  private val hollowHeight: Double = 18.0,
  private val hollowStrength: Double = 14.0
) : Biome {

  override val shape = object : BiomeShape {
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      edge: BiomeEdgeContext
    ): DensityStack {

      val sea = ctx.chunkContext.seaLevel.toDouble()

      // -------------------------
      // 1) Base mountains (heightmap)
      // -------------------------
      val baseN = ctx.noise.mountainBaseHeight2D(worldX, worldZ) // [-1..1]
      val baseSurface = sea + baseHeight + baseN * baseAmp

      val ridgeN = ctx.noise.mountainRidge2D(worldX, worldZ)     // [-1..1]
      val ridge01 = (1.0 - kotlin.math.abs(ridgeN)).coerceIn(0.0, 1.0)
      val ridgeHeight = ridge01 * ridge01 * ridge01 * ridgeAmp

      val surfaceY = baseSurface + ridgeHeight
      val baseDensity = surfaceY - y.toDouble()

      // -------------------------
      // 2) Shelf field (ABSOLUTE altitude band)
      // -------------------------
      val wx = worldX.toDouble()
      val wz = worldZ.toDouble()

      // Domain warp so shelves meander
      val warpX = ctx.noise.ravineWarp2D(wx, wz) * shelfWarpAmp
      val warpZ = ctx.noise.ravineWarp2D(wx + 1000.0, wz + 1000.0) * shelfWarpAmp
      val xw = wx + warpX
      val zw = wz + warpZ

      // Band mask: long ribbons where shelves can exist
      val bandN = ctx.noise.ravineMask2D(xw, zw) // [-1..1]
      val band01 = (1.0 - kotlin.math.abs(bandN)).coerceIn(0.0, 1.0)
      val tBand = ((band01 - shelfThreshold01) / (1.0 - shelfThreshold01)).coerceIn(0.0, 1.0)
      val bandMask = smoothstep01(tBand)
      if (bandMask <= 0.0001) {
        return DensityStack(base = baseDensity, add = 0.0, carve = 0.0)
      }

      // Vary shelf height/thickness along the band
      val v01 = ((ctx.noise.ravineVar2D(xw, zw) + 1.0) * 0.5).coerceIn(0.0, 1.0)
      val shelfCenterY = sea + shelfBaseYAboveSea + shelfYRange * v01
      val halfThick = lerp(shelfHalfThicknessMin, shelfHalfThicknessMax, v01)

      // Vertical thickness band
      val vertical = halfThick - kotlin.math.abs(y.toDouble() - shelfCenterY)

      // Blob breakup (use lowish freq if possible; detail3D can work if not too noisy)
      val blob01 = ((ctx.noise.terrainDetailNoise01(worldX, y, worldZ) + 1.0) * 0.5).coerceIn(0.0, 1.0)
      val tBlob = ((blob01 - blobThreshold01) / (1.0 - blobThreshold01)).coerceIn(0.0, 1.0)
      val blobMask = smoothstep01(tBlob).pow(1.4) * blobStrength

      // Keep shelves mostly where mountains exist
      val mountainMask = smoothstep01(((ridge01 - 0.35) / 0.65).coerceIn(0.0, 1.0))

      val mask = (bandMask * blobMask * mountainMask).coerceIn(0.0, 1.0)

      // Turn mask into an actual solid field:
      // - negative when mask ~0
      // - positive when mask is strong + within vertical band
      val shelfDensity = vertical + (mask - 0.5) * shelfStrength

      // -------------------------
      // 3) Union (ground + shelves)
      // -------------------------
      val union = kotlin.math.max(baseDensity, shelfDensity)

      // -------------------------
      // 4) Hollow underside ONLY when shelf is the reason it's solid
      // -------------------------
      var carve = 0.0
      if (shelfDensity > baseDensity) {
        // only carve below shelf center
        val under = (shelfCenterY - y.toDouble()) // positive below center
        if (under > 0.0) {
          val t = (under / hollowHeight).coerceIn(0.0, 1.0)
          val band = smoothstep01(t) // stronger near underside, fades out downward
          carve = hollowStrength * mask * band
        }
      }

      return DensityStack(base = union, add = 0.0, carve = carve)
    }
  }

  private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
  private fun smoothstep01(t: Double) = t * t * (3.0 - 2.0 * t)
}


/*class OverhangMountains(
  override val caves: CaveShape = gCaves,
  override val decorations: List<Decoration> = listOf(),
  override val materialProvider: MaterialProvider = object : MaterialProvider {
    override fun chooseMaterial(context: MaterialContext): Material {
      return if (context.isSolid) Material.STONE else Material.AIR
    }
  },

  // --- Base mountain knobs ---
  private val baseHeight: Double = 42.0,       // baseline above sea
  private val baseAmp: Double = 28.0,          // rolling ground
  private val ridgeAmp: Double = 110.0,        // big ridges

  // --- Overhang shelf knobs ---
  private val shelfThreshold01: Double = 0.3,//0.5,//0.78, // higher = fewer shelf bands
  private val shelfWarpAmp: Double = 22.0,     // meander shelves sideways
  private val shelfDropMin: Double = 10.0,     // how far below surface shelves form
  private val shelfDropMax: Double = 100.0,//42.0,
  private val shelfHalfThicknessMin: Double = 2.0,
  private val shelfHalfThicknessMax: Double = 5.0,
  private val shelfStrength: Double = 90.0,//30.0,//18.0,   // how “solid” shelves are (bigger = more pronounced)

  // Makes shelves chunky instead of uniform
  private val blobThreshold01: Double = 0.55,
  private val blobStrength: Double = 1.0,

  // Hollow underside so it reads like an overhang
  private val hollowHeight: Double = 14.0,
  private val hollowStrength: Double = 12.0

) : Biome {

  override val shape = object : BiomeShape {
    override fun density(
      ctx: GenerateContext,
      worldX: Int,
      y: Int,
      worldZ: Int,
      edge: BiomeEdgeContext
    ): DensityStack {

      val sea = ctx.chunkContext.seaLevel.toDouble()

      // -------------------------
      // 1) Base mountains surface
      // -------------------------
      val baseN = ctx.noise.mountainBaseHeight2D(worldX, worldZ) // [-1..1]
      val baseSurface = sea + baseHeight + baseN * baseAmp

      val ridgeN = ctx.noise.mountainRidge2D(worldX, worldZ)     // [-1..1]
      val ridge01 = (1.0 - abs(ridgeN)).coerceIn(0.0, 1.0)
      val ridgeHeight = ridge01.pow(3.0) * ridgeAmp

      val surfaceY = baseSurface + ridgeHeight

      val baseDensity = surfaceY - y.toDouble()

      // -------------------------
      // 2) Shelf / island field
      //    (creates overhang caps)
      // -------------------------
      val wx = worldX.toDouble()
      val wz = worldZ.toDouble()

      // domain warp so shelves meander
      val warpX = ctx.noise.ravineWarp2D(wx, wz) * shelfWarpAmp
      val warpZ = ctx.noise.ravineWarp2D(wx + 1000.0, wz + 1000.0) * shelfWarpAmp
      val xw = wx + warpX
      val zw = wz + warpZ

      // Use a ridge-like 2D mask to make long “bands” where shelves appear.
      // If you don’t have a dedicated shelf mask noise, reuse something:
      val bandN = ctx.noise.ravineMask2D(xw, zw) // [-1..1] works well as a stripe generator
      val band01 = (1.0 - abs(bandN)).coerceIn(0.0, 1.0)

      val tBand = ((band01 - shelfThreshold01) / (1.0 - shelfThreshold01)).coerceIn(0.0, 1.0)
      val bandMask = smoothstep01(tBand)
      if (bandMask <= 0.0001) {
        return DensityStack(base = baseDensity, add = 0.0, carve = 0.0)
      }

      // Variation along the band
      val v01 = ((ctx.noise.ravineVar2D(xw, zw) + 1.0) * 0.5).coerceIn(0.0, 1.0)

      val shelfDrop = lerp(shelfDropMin, shelfDropMax, v01)
      val shelfCenterY = surfaceY - shelfDrop

      val halfThick = lerp(shelfHalfThicknessMin, shelfHalfThicknessMax, v01)

      // vertical “cap thickness” band around shelfCenterY
      val dy = abs(y.toDouble() - shelfCenterY)
      val vertical = (halfThick - dy) // positive inside the cap thickness

      // 3D blob mask to break up uniform shelves
      // If you don’t have shelfBlob3D, reuse detail3D (but scale)
      val blobRaw01 = ((ctx.noise.detail3D(worldX, y, worldZ) + 1.0) * 0.5).coerceIn(0.0, 1.0)
      val tBlob = ((blobRaw01 - blobThreshold01) / (1.0 - blobThreshold01)).coerceIn(0.0, 1.0)
      val blobMask = smoothstep01(tBlob).pow(1.4) * blobStrength

      // Combine masks
      val mask = (bandMask * blobMask).coerceIn(0.0, 1.0)

      // Make shelfDensity negative when mask=0, positive only where mask is strong.
      // This is the trick that makes it behave like “islands”.
      val bias = (halfThick + shelfStrength)
      val shelfDensity = vertical + mask * bias - bias

      // -------------------------
      // 3) Union ground + shelves
      // -------------------------
      val unionDensity = max(baseDensity, shelfDensity)

      // -------------------------
      // 4) Hollow underside carve
      //    (prevents “fat lumps”)
      // -------------------------
      // Carve only under shelf center, only when shelf is “active”.
      val under = (shelfCenterY - y.toDouble()) // positive below shelf center
      val underT = (under / hollowHeight).coerceIn(0.0, 1.0)
      val hollowBand = smoothstep01(underT) * (1.0 - smoothstep01(((under - hollowHeight) / hollowHeight).coerceIn(0.0, 1.0)))
      val hollowCarve = hollowStrength * mask * hollowBand

      return DensityStack(
        base = unionDensity,
        add = 0.0,
        carve = hollowCarve
      )
    }
  }

  private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
  private fun smoothstep01(t: Double) = t * t * (3.0 - 2.0 * t)
}*/
