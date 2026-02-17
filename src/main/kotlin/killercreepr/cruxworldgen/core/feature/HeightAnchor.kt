package killercreepr.cruxworldgen.core.feature

import killercreepr.cruxworldgen.api.context.LimitedRegion

interface HeightAnchor {
  fun resolve(region: LimitedRegion, worldX: Int, worldZ: Int): Int

  data class Absolute(val y: Int) : HeightAnchor {
    override fun resolve(region: LimitedRegion, worldX: Int, worldZ: Int) = y
  }

  data class AboveBottom(val offset: Int) : HeightAnchor {
    override fun resolve(region: LimitedRegion, worldX: Int, worldZ: Int) =
      region.ctx.chunkContext.minHeight + offset
  }

  data class BelowTop(val offset: Int) : HeightAnchor {
    override fun resolve(region: LimitedRegion, worldX: Int, worldZ: Int) =
      (region.ctx.chunkContext.maxHeight - 1) - offset
  }

  data class AboveSea(val offset: Int) : HeightAnchor {
    override fun resolve(region: LimitedRegion, worldX: Int, worldZ: Int) =
      region.ctx.chunkContext.seaLevel + offset
  }

  data class BelowSurface(val depth: Int) : HeightAnchor {
    override fun resolve(region: LimitedRegion, worldX: Int, worldZ: Int) =
      region.terrainSnapshot.terrain2D.surfaceY(worldX, worldZ) - depth
  }
}
