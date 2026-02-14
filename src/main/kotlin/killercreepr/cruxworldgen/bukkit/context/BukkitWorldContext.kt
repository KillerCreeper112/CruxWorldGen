package killercreepr.cruxworldgen.bukkit.context

import killercreepr.cruxworldgen.api.context.WorldContext
import org.bukkit.generator.WorldInfo

class BukkitWorldContext(val worldInfo : WorldInfo) : WorldContext {
  override val seed: Long = worldInfo.seed
}