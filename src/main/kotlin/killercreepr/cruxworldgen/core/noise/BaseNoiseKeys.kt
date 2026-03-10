package killercreepr.cruxworldgen.core.noise

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.noise.NoiseField
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.noise.NoiseModule

object BaseNoiseKeys {
  object Temperature : NoiseKey{ override val id = "temperature" }
  object Humidity : NoiseKey{ override val id = "humidity" }
  object Continental : NoiseKey{ override val id = "continental" }
  object Weirdness : NoiseKey{ override val id = "weirdness" }
  object Zone2D : NoiseKey{ override val id = "zone2D" }
  object Biome3D : NoiseKey{ override val id = "biome3D" }
  object TerrainHeight : NoiseKey{ override val id = "terrain.height" }
  object TerrainDetail : NoiseKey{ override val id = "terrain.detail" }
  object CaveDetail : NoiseKey{ override val id = "cave.detail" }
  object AquiferLevel2D : NoiseKey { override val id = "fluid.aquifer.level2D" }
  object AquiferFill3D  : NoiseKey { override val id = "fluid.aquifer.fill3D" }
  object AquiferLava3D  : NoiseKey { override val id = "fluid.aquifer.lava3D" }
  object AquiferDepth2D : NoiseKey { override val id = "fluid.aquifer.depth2D" }
}

object BaseNoiseModule : NoiseModule{
  override fun install(bank: NoiseBank) {
    bank.register(BaseNoiseKeys.Temperature) { seed ->
      NoiseField.noiseField(seed){
        frequency(0.005)
        noiseType(CruxNoise.NoiseType.OpenSimplex2)
        fractalType(CruxNoise.FractalType.FBm)
        fractalOctaves(5)
      }
    }

    bank.register(BaseNoiseKeys.Humidity) { seed ->
      NoiseField.noiseField(seed){
        frequency(0.002)
        noiseType(CruxNoise.NoiseType.OpenSimplex2)
        fractalType(CruxNoise.FractalType.FBm)
        fractalOctaves(7)
      }
    }

    bank.register(BaseNoiseKeys.Continental) { seed ->
      NoiseField.noiseField(seed){
        frequency(0.01)
        .noiseType(CruxNoise.NoiseType.OpenSimplex2)
        .fractalType(CruxNoise.FractalType.FBm)
        .fractalOctaves(3)
      }
    }

    bank.register(BaseNoiseKeys.Weirdness) { seed ->
      NoiseField.noiseField(seed){
        frequency(0.03)
          .noiseType(CruxNoise.NoiseType.OpenSimplex2)
          .fractalType(CruxNoise.FractalType.FBm)
          .fractalOctaves(5)
      }
    }

    bank.register(BaseNoiseKeys.Zone2D) { seed ->
      NoiseField.noiseField(seed){
        frequency(0.001)
          .fractalOctaves(2)
          .fractalType(CruxNoise.FractalType.FBm)
          .fractalLacunarity(2.0)
          .fractalGain(0.5)
      }
    }

    bank.register(BaseNoiseKeys.Biome3D) { seed ->
      NoiseField.noiseField(seed){
        frequency(0.005)
          .fractalOctaves(3)
          .fractalType(CruxNoise.FractalType.FBm)
          .fractalLacunarity(2.0)
          .fractalGain(0.5)
      }
    }

    bank.register(BaseNoiseKeys.TerrainHeight) { seed ->
      NoiseField.noiseField(seed){
        frequency(0.001)
          .noiseType(CruxNoise.NoiseType.OpenSimplex2)
          .fractalType(CruxNoise.FractalType.FBm)
          .fractalOctaves(4)
      }
    }

    bank.register(BaseNoiseKeys.TerrainDetail) { seed ->
      NoiseField.noiseField(seed){
        frequency(0.02)
          .noiseType(CruxNoise.NoiseType.OpenSimplex2)
          .fractalType(CruxNoise.FractalType.FBm)
          .fractalOctaves(3)
      }
    }

    bank.register(BaseNoiseKeys.CaveDetail) { seed ->
      NoiseField.noiseField(seed){
        frequency(0.024)
          .noiseType(CruxNoise.NoiseType.OpenSimplex2)
          .fractalType(CruxNoise.FractalType.FBm)
          .fractalOctaves(3)
      }
    }

    bank.register(BaseNoiseKeys.AquiferLevel2D) { seed ->
      NoiseField.noiseField(seed) {
        frequency(0.0012) // slow changes = regions
        noiseType(CruxNoise.NoiseType.OpenSimplex2)
        fractalType(CruxNoise.FractalType.FBm)
        fractalOctaves(2)
      }
    }

    bank.register(BaseNoiseKeys.AquiferFill3D) { seed ->
      NoiseField.noiseField(seed) {
        frequency(0.020) // pockety
        noiseType(CruxNoise.NoiseType.OpenSimplex2)
        fractalType(CruxNoise.FractalType.FBm)
        fractalOctaves(1)
      }
    }

    bank.register(BaseNoiseKeys.AquiferLava3D) { seed ->
      NoiseField.noiseField(seed) {
        frequency(0.018)
        noiseType(CruxNoise.NoiseType.OpenSimplex2)
        fractalType(CruxNoise.FractalType.FBm)
        fractalOctaves(1)
      }
    }

    bank.register(BaseNoiseKeys.AquiferDepth2D) { seed ->
      NoiseField.noiseField(seed) {
        frequency(0.02)
        noiseType(CruxNoise.NoiseType.OpenSimplex2)
        fractalType(CruxNoise.FractalType.FBm)
        fractalOctaves(1)
      }
    }

  }

}