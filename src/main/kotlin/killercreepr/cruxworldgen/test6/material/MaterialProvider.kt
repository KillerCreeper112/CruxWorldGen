package killercreepr.cruxworldgen.test6.material

import org.bukkit.Material

interface MaterialProvider {
  fun chooseMaterial(context: MaterialContext): Material
}
