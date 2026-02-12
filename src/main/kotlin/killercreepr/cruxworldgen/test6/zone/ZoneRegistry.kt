package killercreepr.cruxworldgen.test6.zone

import killercreepr.cruxworldgen.test6.context.GenerateContext

class ZoneRegistry(
  val zones : List<Zone>
) {
  fun sampleZone(generateCtx : GenerateContext, x : Int, z : Int) : Zone{
    return zones.first() //todo
  }
}