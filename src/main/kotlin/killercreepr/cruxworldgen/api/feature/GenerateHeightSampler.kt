package killercreepr.cruxworldgen.api.feature

import killercreepr.cruxworldgen.api.context.GenerateContext

interface GenerateHeightSampler{
  companion object{
    fun relative(
      frac: Double
    ) = RelativeGenerateHeightSampler(frac)
  }

  fun sampleY(
    ctx: GenerateContext
  ): Int
}

class RelativeGenerateHeightSampler(
  val frac: Double
) : GenerateHeightSampler {
  override fun sampleY(
    ctx: GenerateContext
  ): Int {
    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight-1
    val span = (maxY - minY).coerceAtLeast(1)
    return (minY + span * frac).toInt()
  }
}