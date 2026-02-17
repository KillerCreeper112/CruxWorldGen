package killercreepr.cruxworldgen.core.feature

import killercreepr.cruxworldgen.api.context.GenerateContext

interface HeightAnchor {
  fun resolve(ctx: GenerateContext, worldX: Int, worldZ: Int): Int

  data class Absolute(val y: Int) : HeightAnchor {
    override fun resolve(ctx: GenerateContext, worldX: Int, worldZ: Int) = y
  }

  data class AboveBottom(val offset: Int) : HeightAnchor {
    override fun resolve(ctx: GenerateContext, worldX: Int, worldZ: Int) =
      ctx.chunkContext.minHeight + offset
  }

  data class BelowTop(val offset: Int) : HeightAnchor {
    override fun resolve(ctx: GenerateContext, worldX: Int, worldZ: Int) =
      (ctx.chunkContext.maxHeight - 1) - offset
  }

  data class AboveSea(val offset: Int) : HeightAnchor {
    override fun resolve(ctx: GenerateContext, worldX: Int, worldZ: Int) =
      ctx.chunkContext.seaLevel + offset
  }

  data class BelowSurface(val depth: Int) : HeightAnchor {
    override fun resolve(ctx: GenerateContext, worldX: Int, worldZ: Int) =
      surfaceY - depth
  }
}
