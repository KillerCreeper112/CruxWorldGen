package killercreepr.cruxworldgen.crux.util

import killercreepr.cruxblocks.api.block.group.CruxBlockGroup
import killercreepr.cruxblocks.core.block.component.CruxBlockComponents
import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockAdapter
import org.bukkit.Axis

object CruxTreeUtil {
  fun cachedOrientablePicker(group : CruxBlockGroup): (Axis) -> BlockData = run {
    val directionComp = group.components.get(CruxBlockComponents.DIRECTIONAL_GROUP)!!
    val cache = arrayOfNulls<BlockData>(Axis.entries.size)
    return@run { axis: Axis ->
      cache[axis.ordinal] ?: BukkitBlockAdapter.resolver()
        .resolve(directionComp.getBlock(axis)!!.key().asString())
        .also { cache[axis.ordinal] = it }
    }
  }
}