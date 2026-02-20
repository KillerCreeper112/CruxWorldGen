package killercreepr.cruxworldgen.api.noise

import killercreepr.cruxworldgen.api.biome.BiomeShapeProfile
import killercreepr.cruxworldgen.api.cave.CaveProfile
import killercreepr.cruxworldgen.api.zone.ZoneRegistry
import killercreepr.cruxworldgen.core.biome.volumetric.VolumetricBiomeRegistry

class NoiseAutoInstaller(val noise : NoiseBank) {
  val installed = mutableSetOf<NoiseModule>()
  fun installAllFromZones(zones : ZoneRegistry){
    zones.zones.forEach { zone ->
      install(zone as? Noised)
      zone.biomes.biomes.forEach { biome ->
        install(biome as? Noised)
        (biome.shape as? BiomeShapeProfile)?.let { profile ->
          profile.types.forEach { type -> install(type as? Noised) }
        }

        install(biome.caves as? Noised)
        (biome.caves as? CaveProfile)?.let {
          for (type in it.caveTypes) {
            install(type as? Noised)
          }
        }
      }
    }
  }

  fun installFromAll(reg : VolumetricBiomeRegistry){
    reg.biomes.forEach { biome ->
      install(biome as? Noised)
    }
  }

  fun install(noised : Noised?) : Boolean{
    if(noised == null) return false
    return install(noised.noiseModule)
  }

  fun install(module : NoiseModule) : Boolean{
    if(installed.contains(module)) return false
    installed.add(module)
    module.install(noise)
    return true
  }
}