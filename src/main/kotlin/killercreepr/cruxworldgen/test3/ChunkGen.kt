package killercreepr.cruxworldgen.test3

import org.bukkit.Material
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import java.util.Random

class ChunkGen(
  val densityPipeline: DensityPipeline
) : ChunkGenerator() {

  override fun generateNoise(
    worldInfo: WorldInfo,
    random: Random,
    chunkX: Int,
    chunkZ: Int,
    chunkData: ChunkData
  ) {
    val noise = NoiseBank(worldInfo.seed)

    val baseX = chunkX shl 4
    val baseZ = chunkZ shl 4

    val minY = worldInfo.minHeight
    val maxY = worldInfo.maxHeight - 1

    // 1) Fill terrain by density
    for (dx in 0 until 16) {
      for (dz in 0 until 16) {
        val x = baseX + dx
        val z = baseZ + dz

        for (y in minY..maxY) {
          val d = densityPipeline.density(worldInfo.seed, noise, x, y, z)
          if (d > 0.0) {
            chunkData.setBlock(dx, y, dz, Material.STONE) // base material
          }
        }
      }
    }

    // 2) Surface painting
    val topDepth = 4

    for (dx in 0 until 16) {
      for (dz in 0 until 16) {
        val x = baseX + dx
        val z = baseZ + dz

        // Find top solid
        var topY = maxY
        while (topY >= minY && chunkData.getType(dx, topY, dz) == Material.AIR) topY--
        if (topY < minY) continue

        // Neighbor top helper (in-chunk only; good enough for now)
        fun topAt(lx: Int, lz: Int): Int {
          if (lx !in 0..15 || lz !in 0..15) return topY
          var y = maxY
          while (y >= minY && chunkData.getType(lx, y, lz) == Material.AIR) y--
          return y
        }

        val topX1 = topAt(dx + 1, dz)
        val topZ1 = topAt(dx, dz + 1)
        val dh = maxOf(kotlin.math.abs(topY - topX1), kotlin.math.abs(topY - topZ1))
        val slope = (dh / 8.0).coerceIn(0.0, 1.0)

        val mix = densityPipeline.resolver.mix(BiomeContext(worldInfo.seed, noise), x, z)
        val rule = pickSurfaceRule(mix)

        // Paint down from the top, but stop if we hit air (cave ceiling)
        for (d in 0..topDepth) {
          val y = topY - d
          if (y < minY) break
          if (chunkData.getType(dx, y, dz) == Material.AIR) break

          val mat = rule.material(
            SurfaceContext(
              seed = worldInfo.seed,
              x = x,
              z = z,
              topY = topY,
              y = y,
              depthFromTop = d,
              slope = slope
            )
          )
          chunkData.setBlock(dx, y, dz, mat)
        }
      }
    }
  }

  fun pickSurfaceRule(mix: BiomeMix, snap: Double = 0.62): SurfaceRule {
    return when {
      mix.wa >= snap -> mix.a.surfaceRule()
      mix.wb >= snap -> mix.b.surfaceRule()
      else -> mix.dominantBiome.surfaceRule()
    }
  }
}

