package killercreepr.cruxworldgen.api.feature

import killercreepr.cruxworldgen.api.context.GenerateContext

interface HeightFilter{
  fun isWithinRange(minY : Int, maxY : Int, wy : Int) : Boolean
  fun isWithinRange(ctx : GenerateContext, wy : Int) : Boolean =
    isWithinRange(ctx.chunkContext.minHeight,ctx.chunkContext.maxHeight-1, wy)
}

data class RelativeHeightFilter(
  val minFrac : Float,
  val maxFrac : Float
) : HeightFilter {
  override fun isWithinRange(minY: Int, maxY: Int, wy: Int): Boolean {
    val span = (maxY - minY).coerceAtLeast(1)

    val checkMin = (minY + span * minFrac).toInt()
    val checkMax = (minY + span * maxFrac).toInt()
    return wy in checkMin..checkMax
  }
}