package killercreepr.cruxworldgen.api.zone

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.core.zone.SimpleZoneRegistry

interface ZoneRegistry{
  companion object{
    fun zoneRegistry(zones : List<Zone>) : ZoneRegistry = SimpleZoneRegistry(zones)
  }

  val zones : List<Zone>
  fun sampleZone(generateCtx : GenerateContext, x : Int, z : Int) : Zone
}