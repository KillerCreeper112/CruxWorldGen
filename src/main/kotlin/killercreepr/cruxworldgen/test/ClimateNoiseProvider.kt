package killercreepr.cruxworldgen.test

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.world.NoiseProvider

class ClimateNoiseProvider(
  val temperatureNoise : NoiseProvider,
  val humidityNoise : NoiseProvider,
  val continentalNoise : NoiseProvider,
  val erosionNoise : NoiseProvider,
  val weirdNoise : NoiseProvider,
) {
}