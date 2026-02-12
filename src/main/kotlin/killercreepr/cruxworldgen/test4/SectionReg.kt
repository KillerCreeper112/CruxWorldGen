package killercreepr.cruxworldgen.test4

import killercreepr.cruxworldgen.test4.info.SectionCtx

class SectionReg(
  val sections : List<Section>,
) {
  fun selectBiomes(ctx : SectionCtx, x : Int, z : Int) : SectionResult{
    val scored = sections.map { it to it.suitability(ctx, x, z).coerceAtLeast(0.0) }
      .filter { it.second > 0.0 }
      .sortedByDescending { it.second }
    val map = LinkedHashMap<Section, Double>()
    map.putAll(scored)

    return SectionResult(map)
  }
}

class SectionResult(
  val sections : LinkedHashMap<Section, Double>
){
  val dominateSection : Section get() = sections.firstEntry()!!.key
  val dominateWeight : Double get() = sections.firstEntry()!!.value
  val isEmpty : Boolean get() = sections.isEmpty()
}