package killercreepr.cruxworldgen.api.prop

import killercreepr.cruxworldgen.api.context.GenerateContext

interface PatternQuery {
  fun findCandidate(
    context: GenerateContext,
    startWorldX: Int,
    startWorldZ: Int,
    searchMinY: Int,
    searchMaxY: Int
  ): BlockCandidate?
}

interface BlockCandidate{
  val worldX: Int
  val y: Int
  val worldZ: Int
  val surfaceY: Int
}