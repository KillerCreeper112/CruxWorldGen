package killercreepr.cruxworldgen.bukkit.util

import org.bukkit.Axis
import kotlin.math.abs

object AxisUtil {
  fun axisFromDir(dx: Int, dz: Int): Axis =
    if (abs(dx) >= abs(dz)) Axis.X else Axis.Z
}