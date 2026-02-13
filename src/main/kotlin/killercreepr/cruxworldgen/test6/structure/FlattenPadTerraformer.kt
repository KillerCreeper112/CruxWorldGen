package killercreepr.cruxworldgen.test6.structure

import killercreepr.cruxworldgen.test6.context.GenerateContext
import org.bukkit.Material
import kotlin.math.sqrt

class FlattenPadTerraformer(
  private val bufferRadius: Int = 6,   // how far it blends into terrain
  private val jitterAmp: Double = 0.75 // make pad slightly imperfect
) : Terraformer {

  override fun terraformChunk(ctx: GenerateContext, inst: StructureInstance, template: StructureTemplate) {
    val chunk = ctx.chunkContext
    val chunkWorldX = ctx.chunkX * 16
    val chunkWorldZ = ctx.chunkZ * 16

    // footprint in local chunk coords (single-chunk mode assumes inst anchor is inside this chunk)
    val anchorLX = inst.worldX - chunkWorldX
    val anchorLZ = inst.worldZ - chunkWorldZ
    if (anchorLX !in 0..15 || anchorLZ !in 0..15) return

    // Compute footprint AABB (rotated size)
    val fp = footprintAabb(template.bounds, inst.rot)

    val minLX = anchorLX + fp.minX
    val maxLX = anchorLX + fp.maxX
    val minLZ = anchorLZ + fp.minZ
    val maxLZ = anchorLZ + fp.maxZ

    // If footprint leaves the chunk, bail (single-chunk mode)
    if (minLX < 0 || maxLX > 15 || minLZ < 0 || maxLZ > 15) return

    // Pick target height = median of surface in footprint
    val samples = ArrayList<Int>()
    for (x in minLX..maxLX) for (z in minLZ..maxLZ) {
      samples.add(ctx.queries.surfaceY(x, z))
    }
    val targetY = median(samples)

    // Apply flatten + falloff over footprint + buffer
    val minX = (minLX - bufferRadius).coerceIn(0, 15)
    val maxX = (maxLX + bufferRadius).coerceIn(0, 15)
    val minZ = (minLZ - bufferRadius).coerceIn(0, 15)
    val maxZ = (maxLZ + bufferRadius).coerceIn(0, 15)

    for (lx in minX..maxX) {
      for (lz in minZ..maxZ) {
        val surface = ctx.queries.surfaceY(lx, lz)
        val dist = distanceToRect(lx, lz, minLX, minLZ, maxLX, maxLZ)

        // t=1 inside footprint, fades to 0 at bufferRadius
        val t = ((bufferRadius - dist) / bufferRadius.toDouble()).coerceIn(0.0, 1.0)
        val falloff = smoothstep01(t)

        if (falloff <= 0.001) continue

        val wx = chunkWorldX + lx
        val wz = chunkWorldZ + lz

        val jitter = (hashSigned01(inst.seed xor (wx.toLong() * 341873128712L) xor (wz.toLong() * 132897987541L))) * jitterAmp

        val desiredSurface = (surface + (targetY - surface) * falloff + jitter).toInt()

        // If surface too high -> carve down
        if (surface > desiredSurface) {
          for (y in surface downTo desiredSurface + 1) {
            if (y in chunk.minHeight until chunk.maxHeight) {
              chunk.setBlock(lx, y, lz, Material.AIR)
            }
          }
          // Paint top
          paintTopSoil(chunk, lx, desiredSurface, lz)
        }

        // If surface too low -> fill up
        if (surface < desiredSurface) {
          for (y in (surface + 1)..desiredSurface) {
            if (y in chunk.minHeight until chunk.maxHeight) {
              // foundation filler (later: biome palette)
              chunk.setBlock(lx, y, lz, Material.DIRT)
            }
          }
          paintTopSoil(chunk, lx, desiredSurface, lz)
        }
      }
    }
  }

  private fun paintTopSoil(chunk: killercreepr.cruxworldgen.test6.context.ChunkContext, x: Int, y: Int, z: Int) {
    if (y !in chunk.minHeight until chunk.maxHeight) return
    // super simple for now (later: biome palette)
    chunk.setBlock(x, y, z, Material.GRASS_BLOCK)
    if (y - 1 >= chunk.minHeight) chunk.setBlock(x, y - 1, z, Material.DIRT)
    if (y - 2 >= chunk.minHeight) chunk.setBlock(x, y - 2, z, Material.DIRT)
  }

  private fun footprintAabb(bounds: Aabb, rot: Int): Aabb {
    val w = bounds.sizeX
    val d = bounds.sizeZ
    val (sx, sz) = when ((rot % 360 + 360) % 360) {
      0, 180 -> Pair(w, d)
      90, 270 -> Pair(d, w)
      else -> Pair(w, d)
    }
    // footprint relative to anchor (we treat anchor at bounds.min corner space)
    return Aabb(bounds.minX, 0, bounds.minZ, bounds.minX + sx - 1, 0, bounds.minZ + sz - 1)
  }

  private fun distanceToRect(x: Int, z: Int, minX: Int, minZ: Int, maxX: Int, maxZ: Int): Double {
    val dx = when {
      x < minX -> (minX - x).toDouble()
      x > maxX -> (x - maxX).toDouble()
      else -> 0.0
    }
    val dz = when {
      z < minZ -> (minZ - z).toDouble()
      z > maxZ -> (z - maxZ).toDouble()
      else -> 0.0
    }
    return sqrt(dx * dx + dz * dz)
  }

  private fun median(list: List<Int>): Int {
    if (list.isEmpty()) return 64
    val s = list.sorted()
    return s[s.size / 2]
  }

  private fun smoothstep01(t: Double): Double = t * t * (3.0 - 2.0 * t)

  private fun hashSigned01(seed: Long): Double {
    var v = seed
    v = (v xor (v ushr 30)) * -4658895280553007687L
    v = (v xor (v ushr 27)) * -7723592293110705685L
    v = v xor (v ushr 31)
    // map to [-1,1]
    val u = (v and Long.MAX_VALUE).toDouble() / Long.MAX_VALUE.toDouble()
    return u * 2.0 - 1.0
  }
}

data class Plane(val a: Double, val b: Double, val c: Double) {
  fun yAt(x: Double, z: Double) = a * x + b * z + c
}

private fun fitPlaneLeastSquares(samples: List<Triple<Int, Int, Int>>): Plane {
  // samples: (x,z,y) in LOCAL coords
  // simple least squares for a,b,c
  var sx = 0.0; var sz = 0.0; var sy = 0.0
  var sxx = 0.0; var szz = 0.0; var sxz = 0.0
  var sxy = 0.0; var szy = 0.0
  val n = samples.size.toDouble().coerceAtLeast(1.0)

  for ((x,z,y) in samples) {
    val X = x.toDouble()
    val Z = z.toDouble()
    val Y = y.toDouble()
    sx += X; sz += Z; sy += Y
    sxx += X*X; szz += Z*Z; sxz += X*Z
    sxy += X*Y; szy += Z*Y
  }

  // Solve normal equations for [a b c]
  // |sxx sxz sx| |a| = |sxy|
  // |sxz szz sz| |b| = |szy|
  // |sx  sz  n | |c| = |sy |
  val det =
    sxx*(szz*n - sz*sz) -
      sxz*(sxz*n - sz*sx) +
      sx *(sxz*sz - szz*sx)

  if (kotlin.math.abs(det) < 1e-9) {
    // fallback to flat median
    val ys = samples.map { it.third }.sorted()
    val m = ys[ys.size/2].toDouble()
    return Plane(0.0, 0.0, m)
  }

  fun det3(a11: Double,a12: Double,a13: Double, a21: Double,a22: Double,a23: Double, a31: Double,a32: Double,a33: Double): Double =
    a11*(a22*a33 - a23*a32) - a12*(a21*a33 - a23*a31) + a13*(a21*a32 - a22*a31)

  val detA = det3(sxy, sxz, sx,  szy, szz, sz,  sy,  sz,  n)
  val detB = det3(sxx, sxy, sx,  sxz, szy, sz,  sx,  sy,  n)
  val detC = det3(sxx, sxz, sxy, sxz, szz, szy, sx,  sz,  sy)

  return Plane(detA/det, detB/det, detC/det)
}
