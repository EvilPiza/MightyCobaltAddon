package com.mighty.pathfinder.helper

import com.mighty.module.MightyRotations
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import org.cobalt.event.annotation.SubscribeEvent
import org.cobalt.event.impl.WorldRenderEvent
import org.cobalt.module.impl.misc.Rotations
import org.cobalt.util.rotation.Rotation

object Rotation {
  private val mc = Minecraft.getInstance()

  private var active = false
  private var targetYaw = 0f
  private var targetPitch = 0f
  private var randomPitch = 0f
  private var yawVelocity = 0f
  private var pitchVelocity = 0f
  private var lastFrameNanos = 0L

  // Hardcoded Settings that are just better than anything else
  private var maxDeltaSeconds = .2f // anything lower is jittery
  private var arrivedYawThreshold = .15f // anything less is unstable, anything more is too strict

  fun setTarget(yaw: Float) {
    val (min, max) = MightyRotations.randomPitchRange
    val pitch = Random.nextInt(min, max).toFloat()

    if (MightyRotations.useCobaltRots) {
      Rotations.start(Rotation(yaw, pitch, false))
      return
    }

    val player = mc.player ?: return
    val distance = abs(Mth.wrapDegrees(yaw - player.yRot))

    if (!active || distance >= (MightyRotations.retargetThreshold / 100.0).toFloat()) {
      lastFrameNanos = System.nanoTime()
      active = true
    }

    targetYaw = yaw
  }

  fun clearTarget() {
    active = false
    yawVelocity = 0f
    pitchVelocity = 0f
  }

  @SubscribeEvent
  fun onFrame(event: WorldRenderEvent) {
    if (!active) return
    val player = mc.player ?: return

    val now = System.nanoTime()
    val deltaSeconds = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, maxDeltaSeconds)
    lastFrameNanos = now

    val stiffness = MightyRotations.stiffness.toFloat()
    val dampingCoefficient = 2f * (MightyRotations.dampingRatio / 100.0).toFloat() * sqrt(stiffness)

    val yawError = Mth.wrapDegrees(targetYaw - player.yRot)
    yawVelocity += (stiffness * yawError - dampingCoefficient * yawVelocity) * deltaSeconds
    player.yRot += yawVelocity * deltaSeconds

    val pitchError = Mth.wrapDegrees(targetPitch - player.xRot)
    pitchVelocity += (stiffness * pitchError - dampingCoefficient * pitchVelocity) * deltaSeconds
    player.yRot = Mth.clamp(player.yRot + pitchVelocity * deltaSeconds, -90f, 90f)

    if (abs(yawError) < arrivedYawThreshold && abs(yawVelocity) < (MightyRotations.arrivedVelocityThreshold / 100.0).toFloat()) {
      active = false
      yawVelocity = 0f
      pitchVelocity = 0f
    }
  }
}
