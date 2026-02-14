package killercreepr.cruxworldgen.api.cave

import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext

interface CaveShape {
  fun carve(ctx: GenerateContext, cave: CaveContext): Double
  fun add(ctx: GenerateContext, c: CaveContext): Double = 0.0
}