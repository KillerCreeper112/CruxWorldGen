package killercreepr.cruxworldgen.bukkit.util

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.bukkit.block.BukkitDataBlockData
import org.bukkit.Axis
import org.bukkit.block.BlockType
import org.bukkit.block.data.Orientable

object TreeUtil {
  fun cachedOrientablePicker(type: BlockType): (Axis) -> BlockData = run {
    val cache = arrayOfNulls<BlockData>(Axis.entries.size)
    return@run { axis: Axis ->
      cache[axis.ordinal] ?: BukkitDataBlockData(
        type.createBlockData().apply {
          (this as? Orientable)?.axis = axis
        }
      ).also { cache[axis.ordinal] = it }
    }
  }
}