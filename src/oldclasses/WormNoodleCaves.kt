package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.util.HashUtil
import kotlin.math.*

class WormNoodleCaves(
  val cellSizeXZ: Int = 48,                  // bigger = fewer worm systems
  val cellSizeY: Int = 48,                  // bigger = fewer worm systems
  val wormChance: Double = 0.55,           // chance a cell spawns a worm
  val wormLengthMin: Double = 26.0,
  val wormLengthMax: Double = 256.0,//54.0,

  val horizontalRadiusBlocks: Double = 10.2,
  val verticalRadiusBlocks: Double = 7.0,

  val baseDepthBelowSurface: Double = 28.0,
  val depthVariationBlocks: Double = 10.0,

  val yawJitter: Double = 0.85,            // curve / direction variety
  val verticalWaveAmp: Double = 5.0,       // rise/fall amount along worm
  val verticalWaveFreq: Double = 1.35,

  val strength: Double = 1.10,
  val openMarginBlocks: Double = 8.0,

  val deepStart: Double = 8.0,
  val deepFull: Double = 20.0,
  val nearSurfaceDepth: Double = 10.0,
  val breakThreshold: Double = 0.84,

  override val surfaceFadeStart: Int = 3,
  override val surfaceFadeRamp: Int = 10
) : CaveType {

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0
    //if (cave.depthBelowSurface < 0) return 0.0

    val worldX = cave.worldX.toDouble()
    val worldY = cave.y.toDouble()
    val worldZ = cave.worldZ.toDouble()

    val cellX = floorDiv(cave.worldX, cellSizeXZ)
    val cellY = floorDiv(cave.y, cellSizeY)
    val cellZ = floorDiv(cave.worldZ, cellSizeXZ)

    var bestMask = 0.0

    for (dz in -1..1) {
      for (dx in -1..1) {
        for (dy in -1..1) {
          val cx = cellX + dx
          val cy = cellY + dy
          val cz = cellZ + dz

          val seed = HashUtil.hash3D(ctx.worldContext.seed, cx, cy, cz)
          val chance = rand01(seed, 0)
          if (chance > wormChance) continue

          val startX = cx * cellSizeXZ + rand01(seed, 1) * cellSizeXZ
          val startY = cy * cellSizeY + rand01(seed, 2) * cellSizeY
          val startZ = cz * cellSizeXZ + rand01(seed, 3) * cellSizeXZ

          val yaw = randRange(seed, 4, 0.0, Math.PI * 2.0)
          val length = randRange(seed, 5, wormLengthMin, wormLengthMax)

          val dxDir = cos(yaw)
          val dzDir = sin(yaw)

          // project onto horizontal worm axis
          val px = worldX - startX
          val pz = worldZ - startZ
          val along = px * dxDir + pz * dzDir
          if (along < 0.0 || along > length) continue

          val t = along / max(1.0, length)

          // base straight centerline in XZ
          val nearestX = startX + dxDir * along
          val nearestZ = startZ + dzDir * along

          // sideways bend so it feels wormy, not laser-straight
          val curveAngle = yaw + sin(t * Math.PI * 2.0) * yawJitter * 0.35
          val curveOffset = sin(t * Math.PI * 1.5 + randRange(seed, 6, 0.0, Math.PI * 2.0)) * 4.0

          val perpX = -sin(curveAngle)
          val perpZ = cos(curveAngle)

          val bentX = nearestX + perpX * curveOffset
          val bentZ = nearestZ + perpZ * curveOffset

          val sideDist = hypot(worldX - bentX, worldZ - bentZ)
          if (sideDist > horizontalRadiusBlocks * 2.2) continue

          // true world-space vertical centerline
          val wormY =
            startY +
              sin(t * Math.PI * 2.0 * verticalWaveFreq + randRange(seed, 7, 0.0, Math.PI * 2.0)) * verticalWaveAmp

          val yDist = abs(worldY - wormY)
          if (yDist > verticalRadiusBlocks * 2.2) continue

          val horizontalMask =
            1.0 - smooth01((sideDist / horizontalRadiusBlocks).coerceIn(0.0, 1.0))
          val verticalMask =
            1.0 - smooth01((yDist / verticalRadiusBlocks).coerceIn(0.0, 1.0))

          val endFade = endCaps(t)

          val mask = horizontalMask * horizontalMask * verticalMask * endFade
          if (mask > bestMask) bestMask = mask
        }
      }
    }

    if (bestMask <= 0.001) return 0.0

    val placementMask = depthWithBreakthrough(
      cave = cave,
      deepStart = deepStart,
      deepFull = deepFull,
      nearSurfaceDepth = nearSurfaceDepth,
      breakThreshold = breakThreshold,
      noiseSample = stable2DNoise(ctx.worldContext.seed, cave.worldX, cave.worldZ)
    )

    val finalMask = bestMask * placementMask
    if (finalMask <= 0.001) return 0.0

    return finalMask * (solidDensity * strength + openMarginBlocks)
  }

  private fun smooth01(t: Double): Double = t * t * (3.0 - 2.0 * t)

  private fun endCaps(t: Double): Double {
    val edge = min(t, 1.0 - t) / 0.18
    return smooth01(edge.coerceIn(0.0, 1.0))
  }

  private fun depthWithBreakthrough(
    cave: CaveContext,
    deepStart: Double,
    deepFull: Double,
    nearSurfaceDepth: Double,
    breakThreshold: Double,
    noiseSample: Double
  ): Double {
    val d = cave.depthBelowSurface.toDouble()

    val deepMask = ((d - deepStart) / max(0.0001, deepFull - deepStart)).coerceIn(0.0, 1.0)
    val deepSmooth = smooth01(deepMask)

    if (d >= nearSurfaceDepth) return deepSmooth

    val n01 = (noiseSample + 1.0) * 0.5
    val breakthrough = ((n01 - breakThreshold) / max(0.0001, 1.0 - breakThreshold)).coerceIn(0.0, 1.0)
    val breakMask = smooth01(breakthrough)

    return max(deepSmooth, breakMask)
  }

  private fun stable2DNoise(seed: Long, x: Int, z: Int): Double {
    val h = hash3(seed, x, 0, z)
    return rand01FromLong(h) * 2.0 - 1.0
  }

  private fun hash2(seed: Long, x: Int, z: Int): Long {
    var h = seed
    h = mix(h xor (x.toLong() * 0x9E377B97F4A7C15L))
    h = mix(h xor (z.toLong() * 0xC2B2A3D27D4EB4FL))
    return h
  }

  private fun hash3(seed: Long, x: Int, y: Int, z: Int): Long {
    var h = seed
    h = mix(h xor (x.toLong() * 0x9E3779B97FA7C15L))
    h = mix(h xor (y.toLong() * 0xC2B2AE3D2D4EB4FL))
    h = mix(h xor (z.toLong() * 0x165667B19E3779F9L))
    return h
  }

  private fun rand01(seed: Long, salt: Int): Double {
    return rand01FromLong(mix(seed xor (salt.toLong() * 0x9E379B97F4A7C15L)))
  }

  private fun randRange(seed: Long, salt: Int, min: Double, max: Double): Double {
    return min + rand01(seed, salt) * (max - min)
  }

  private fun rand01FromLong(v: Long): Double {
    val bits = (v ushr 11) and ((1L shl 53) - 1)
    return bits.toDouble() / (1L shl 53).toDouble()
  }

  private fun mix(v: Long): Long {
    var x = v
    x = (x xor (x ushr 30)) * -4658895280553007687L
    x = (x xor (x ushr 27)) * -7723592293110705685L
    x = x xor (x ushr 31)
    return x
  }

  private fun floorDiv(a: Int, b: Int): Int = kotlin.math.floor(a.toDouble() / b.toDouble()).toInt()
}