package killercreepr.cruxworldgen.test6.prop

import killercreepr.cruxworldgen.test6.context.GenerateContext

interface PatternQuery {
  fun findCandidate(
    context: GenerateContext,
    startWorldX: Int,
    startWorldZ: Int,
    searchMinY: Int,
    searchMaxY: Int
  ): BlockCandidate?
}

data class BlockCandidate(
  val worldX: Int,
  val y: Int,
  val worldZ: Int,
  val surfaceY: Int
)
