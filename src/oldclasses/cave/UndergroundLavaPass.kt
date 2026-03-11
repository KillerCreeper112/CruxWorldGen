package killercreepr.cruxworldgen.test.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockAdapter
import org.bukkit.Material

class UndergroundLavaPass(
  // “Minecraft-like”: deep lava becomes common
  val startY: Int = -20,     // starts appearing
  val fullY: Int = -54,      // fully “eligible” by here

  // local lava table
  val baseLavaLevel: Int = -54,
  val lavaLevelAmp: Int = 10, // +/- variation of lava level

  // zone control (cluster lava into regions)
  val zoneThreshold: Double = 0.60, // higher = fewer lava regions
  val zoneSharpness: Double = 1.8,  // higher = tighter edges

  // flatness breakup
  val jitterBlocks: Double = 2.5,

  // safety: don’t put lava too close to surface openings
  val minDepthBelowSurface: Int = 10,

  // if true, biases toward pools on cave floors instead of fully flooding giant rooms
  val floorBias: Boolean = true
) : Noised {

  object LavaNoise : NoiseModule {
    object LavaZone2D : NoiseKey { override val id = "liquid.lava.zone_2D" }
    object LavaLevel2D : NoiseKey { override val id = "liquid.lava.level_2D" }
    object LavaJitter3D : NoiseKey { override val id = "liquid.lava.jitter_3D" }

    override fun install(bank: NoiseBank) {
      bank.register(LavaZone2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.010)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(LavaLevel2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.004)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(LavaJitter3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.030)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
    }
  }

  override val noiseModule = LavaNoise

  private fun smoothstep01(t: Double): Double {
    val x = t.coerceIn(0.0, 1.0)
    return x * x * (3.0 - 2.0 * x)
  }

  private fun depthMask(y: Int): Double {
    // 0 above startY, 1 at/below fullY
    if (y >= startY) return 0.0
    if (y <= fullY) return 1.0
    val t = (startY - y).toDouble() / (startY - fullY).toDouble()
    return smoothstep01(t)
  }

  private fun zoneMask(ctx: GenerateContext, worldX: Int, worldZ: Int): Double {
    val z01 = (ctx.noise.get(LavaNoise.LavaZone2D).noise2D(worldX, worldZ) + 1.0) * 0.5
    // remap around threshold into 0..1 with sharpening
    val t = ((z01 - zoneThreshold) / (1.0 - zoneThreshold)).coerceIn(0.0, 1.0)
    val s = smoothstep01(t)
    return Math.pow(s, zoneSharpness)
  }

  private fun localLavaLevel(ctx: GenerateContext, worldX: Int, worldZ: Int): Int {
    val n = ctx.noise.get(LavaNoise.LavaLevel2D).noise2D(worldX, worldZ) // -1..1
    return baseLavaLevel + (n * lavaLevelAmp).toInt()
  }

  fun run(region: LimitedRegion, chunkX: Int, chunkZ: Int) {
    val ctx = region.ctx

    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1

    val baseX = chunkX shl 4
    val baseZ = chunkZ shl 4

    // You likely have a way to query “depthBelowSurface” per block.
    // If you don’t: you can approximate by scanning down from surface per (x,z).
    // I’ll assume you have something like region.depthBelowSurface(x,y,z) or can compute it.
    fun depthBelowSurface(worldX: Int, y: Int, worldZ: Int): Int {
      // Replace this with your existing surface sampling/caching.
      val surfaceY = region.terrainSnapshot.terrain2D.surfaceY(worldX, worldZ)
      return surfaceY - y
    }

    // Helper predicates (replace with your block palette / resolver if needed)
    fun isCarvedAir(worldX: Int, y: Int, worldZ: Int): Boolean {
      return region.terrainQueries.isEmpty(worldX, y, worldZ)
    }
    fun isSolid(worldX: Int, y: Int, worldZ: Int): Boolean {
      return region.terrainQueries.isSolid(worldX, y, worldZ)
    }

    for (lz in 0..15) for (lx in 0..15) {
      val worldX = baseX + lx
      val worldZ = baseZ + lz

      val zone = zoneMask(ctx, worldX, worldZ)
      if (zone <= 0.001) continue

      val localLevel = localLavaLevel(ctx, worldX, worldZ)

      // Only meaningful below local lava level (plus jitter)
      val yTop = minOf(maxY, localLevel + lavaLevelAmp + 4)

      for (y in yTop downTo minY) {
        val dbs = depthBelowSurface(worldX, y, worldZ)
        if (dbs < minDepthBelowSurface) continue

        if (!isCarvedAir(worldX, y, worldZ)) continue

        val dMask = depthMask(y)
        if (dMask <= 0.0) continue

        // Break up flat sheets a bit
        val jitter = ctx.noise.get(LavaNoise.LavaJitter3D).noise3D(worldX, y, worldZ) * jitterBlocks
        val effectiveLevel = localLevel + jitter.toInt()

        if (y > effectiveLevel) continue

        // Optional: bias lava to settle into floors/pools rather than flooding everything equally
        if (floorBias) {
          val belowSolid = (y - 1 >= minY) && isSolid(worldX, y - 1, worldZ)
          if (!belowSolid) continue
        }

        // Final probability / mask (deterministic, no RNG needed)
        // “zone * depth” drives prevalence; deeper = more lava, inside zone = more lava
        val mask = zone * dMask
        if (mask < 0.20) continue  // tweak this threshold

        region.setBlock(worldX, y, worldZ, BukkitBlockAdapter.resolver().resolve(Material.LAVA))
      }
    }
  }
}