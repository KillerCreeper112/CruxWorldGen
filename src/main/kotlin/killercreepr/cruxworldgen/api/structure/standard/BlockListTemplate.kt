package killercreepr.cruxworldgen.api.structure.standard

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.structure.*
import kotlin.math.max
import kotlin.math.min

class BlockListTemplate(
  private val blocks: List<RelBlock>
) : StructureTemplate {

  override val bounds: Aabb = computeBounds(blocks)

  override fun placeIntoChunk(
    region: LimitedRegion,
    inst: StructureInstance,
    processors: List<BlockProcessor>
  ) {
    val ctx = region.ctx
    val chunk = ctx.chunkContext

    val chunkWidth = chunk.width
    val chunkDepth = chunk.depth


    val chunkWorldX = ctx.chunkX * chunkWidth
    val chunkWorldZ = ctx.chunkZ * chunkDepth

    for (b in blocks) {
      val (rx, rz) = rotateXZ(b.x, b.z, inst.rot, bounds)

      val wx = inst.worldX + rx
      val wy = inst.worldY + b.y
      val wz = inst.worldZ + rz

      // ONLY write if inside THIS chunk (single-chunk mode)
      val lx = wx - chunkWorldX
      val lz = wz - chunkWorldZ
      if (lx !in 0..<chunkWidth || lz !in 0..<chunkDepth) continue
      if (wy !in chunk.minHeight until chunk.maxHeight) continue

      var mat = b.mat
      for (p in processors) {
        mat = p.process(ctx, wx, wy, wz, mat)
      }

      if (mat != BlockData.NONE) {
        region.setBlock(lx, wy, lz, mat)
      }
    }
  }

  companion object {
    private fun computeBounds(blocks: List<RelBlock>): Aabb {
      var minX = Int.MAX_VALUE
      var minY = Int.MAX_VALUE
      var minZ = Int.MAX_VALUE
      var maxX = Int.MIN_VALUE
      var maxY = Int.MIN_VALUE
      var maxZ = Int.MIN_VALUE

      for (b in blocks) {
        minX = min(minX, b.x); maxX = max(maxX, b.x)
        minY = min(minY, b.y); maxY = max(maxY, b.y)
        minZ = min(minZ, b.z); maxZ = max(maxZ, b.z)
      }
      if (blocks.isEmpty()) return Aabb.aabb(0, 0, 0, 0, 0, 0)
      return Aabb.aabb(minX, minY, minZ, maxX, maxY, maxZ)
    }

    /**
     * Rotate around template bounds so rotation stays "tight" and predictable.
     * rot is one of {0, 90, 180, 270}.
     */
    private fun rotateXZ(x: Int, z: Int, rot: Int, bounds: Aabb): Pair<Int, Int> {
      val w = bounds.sizeX
      val d = bounds.sizeZ

      // normalize x/z to 0..w-1 / 0..d-1 relative to bounds min
      val lx = x - bounds.minX
      val lz = z - bounds.minZ

      val (rx, rz) = when ((rot % 360 + 360) % 360) {
        0   -> Pair(lx, lz)
        90  -> Pair(d - 1 - lz, lx)
        180 -> Pair(w - 1 - lx, d - 1 - lz)
        270 -> Pair(lz, w - 1 - lx)
        else -> Pair(lx, lz)
      }

      // return rotated coords in the same "relative to bounds min" space,
      // then shift back so caller can anchor at inst.worldX/Z
      return Pair(rx + bounds.minX, rz + bounds.minZ)
    }
  }
}