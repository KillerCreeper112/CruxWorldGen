package killercreepr.cruxworldgen.api.decor

import killercreepr.cruxworldgen.api.context.LimitedRegion

fun interface DecorHolder<T>{
  fun value(region : LimitedRegion, seed : Long) : T
}