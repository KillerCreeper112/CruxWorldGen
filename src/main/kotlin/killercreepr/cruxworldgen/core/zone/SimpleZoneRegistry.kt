package killercreepr.cruxworldgen.core.zone

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.zone.Zone
import killercreepr.cruxworldgen.api.zone.ZoneRegistry

class SimpleZoneRegistry(override val zones: List<Zone>) : ZoneRegistry {
  override fun sampleZone(
    generateCtx: GenerateContext,
    x: Int,
    z: Int
  ): Zone {
    //todo
    return zones.first()
  }
}