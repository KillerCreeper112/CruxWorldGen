package killercreepr.cruxworldgen.test2.biome.decoration

import org.bukkit.Material
import kotlin.math.abs

class SimpleTreeFeature(
  private val attemptsPerOrigin: Int = 6,
  private val trunkMin: Int = 4,
  private val trunkMax: Int = 6,
  private val maxLeafRadius: Int = 2
) : DecoratorFeature {

  override fun placeFromOrigin(
    originChunkX: Int,
    originChunkZ: Int,
    targetChunkX: Int,
    targetChunkZ: Int,
    context: DecorationContext
  ) {
    val rng = context.rng

    val targetMinX = targetChunkX * 16
    val targetMaxX = targetMinX + 15
    val targetMinZ = targetChunkZ * 16
    val targetMaxZ = targetMinZ + 15

    // Determine origin biome once (use a sensible Y; origin column top is fine)
    val originCenterX = originChunkX * 16 + 8
    val originCenterZ = originChunkZ * 16 + 8
    val originY = context.originHeightAt(originCenterX, originCenterZ).let { if (it == Int.MIN_VALUE) (context.heightmap[8 + 8*16]).coerceAtLeast(64) else it }
    val originBiome = context.biomeAt(originCenterX, originY, originCenterZ)

    for (attempt in 0 until attemptsPerOrigin) {
      val rx = rng.nextInt(16)
      val rz = rng.nextInt(16)

      val wx = originChunkX * 16 + rx
      val wz = originChunkZ * 16 + rz

      // baseY: prefer originHeightAt for origin columns outside the target
      val baseY = if (wx in targetMinX..targetMaxX && wz in targetMinZ..targetMaxZ) {
        context.heightmap[(wx - targetMinX) + (wz - targetMinZ) * 16]
      } else {
        context.originHeightAt(wx, wz)
      }
      if (baseY == Int.MIN_VALUE) continue

      // Quick column-level permission: require origin biome to be dominant enough at this column
      val placementColumnBiome = context.biomeAt(wx, baseY, wz)
      if (placementColumnBiome::class != originBiome::class) continue

      // flatness check using originHeightAt for neighbors outside target
      var flat = true
      for (dx in -1..1) {
        for (dz in -1..1) {
          val nx = wx + dx
          val nz = wz + dz
          val nh = if (nx in targetMinX..targetMaxX && nz in targetMinZ..targetMaxZ) {
            context.heightmap[(nx - targetMinX) + (nz - targetMinZ) * 16]
          } else {
            context.originHeightAt(nx, nz)
          }
          if (nh == Int.MIN_VALUE || kotlin.math.abs(nh - baseY) > 1) { flat = false; break }
        }
        if (!flat) break
      }
      if (!flat) continue

      val trunkH = trunkMin + rng.nextInt(maxOf(1, trunkMax - trunkMin + 1))

      // vertical space check
      var spaceOk = true
      for (h in 1..(trunkH + maxLeafRadius + 1)) {
        val wy = baseY + h
        if (!context.canReplace(wx, wy, wz)) { spaceOk = false; break }
      }
      if (!spaceOk) continue

      // place trunk: per-voxel biome check (ensures trunk doesn't appear in other biome)
      for (h in 1..trunkH) {
        val wy = baseY + h
        val voxelBiome = context.biomeAt(wx, wy, wz)
        if (voxelBiome::class != originBiome::class) continue
        if (context.canReplace(wx, wy, wz)) context.placeBlock(wx, wy, wz, Material.OAK_LOG)
      }

      // place leaves: per-voxel biome check (prevents leaves from spawning in other biomes)
      val leafBase = baseY + trunkH
      for (dx in -maxLeafRadius..maxLeafRadius) {
        for (dz in -maxLeafRadius..maxLeafRadius) {
          for (dy in 0..maxLeafRadius) {
            val lxw = wx + dx
            val lyw = leafBase + dy
            val lzw = wz + dz
            val distSq = dx*dx + dz*dz + dy*dy
            if (distSq <= (maxLeafRadius*maxLeafRadius + 1)) {
              val leafVoxelBiome = context.biomeAt(lxw, lyw, lzw)
              if (leafVoxelBiome::class != originBiome::class) continue
              if (context.canReplace(lxw, lyw, lzw)) {
                context.placeBlock(lxw, lyw, lzw, Material.OAK_LEAVES)
              }
            }
          }
        }
      }
    }
  }
}