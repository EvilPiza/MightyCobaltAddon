package com.mighty.pathfinder.helper

import com.mighty.module.Rotations
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.MathHelper
import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.render.WorldRenderEvent
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

object Rotation {
  private val mc = MinecraftClient.getInstance()

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

  fun setTarget(yaw: Float, pitch: Float? = null) {
    val player = mc.player ?: return
    val distance = abs(MathHelper.wrapDegrees(yaw - player.yaw))

    if (!active || distance >= Rotations.retargetThreshold.toFloat()) {
      val (min, max) = Rotations.randomPitchRange
      randomPitch = Random.nextInt(min.toInt(), max.toInt()).toFloat()
      lastFrameNanos = System.nanoTime()
      active = true
    }

    targetYaw = yaw
    targetPitch = pitch ?: randomPitch
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

    val stiffness = Rotations.stiffness.toFloat()
    val dampingCoefficient = 2f * Rotations.dampingRatio.toFloat() * sqrt(stiffness)

    val yawError = MathHelper.wrapDegrees(targetYaw - player.yaw)
    yawVelocity += (stiffness * yawError - dampingCoefficient * yawVelocity) * deltaSeconds
    player.yaw += yawVelocity * deltaSeconds

    val pitchError = MathHelper.wrapDegrees(targetPitch - player.pitch)
    pitchVelocity += (stiffness * pitchError - dampingCoefficient * pitchVelocity) * deltaSeconds
    player.pitch = MathHelper.clamp(player.pitch + pitchVelocity * deltaSeconds, -90f, 90f)

    if (abs(yawError) < arrivedYawThreshold && abs(yawVelocity) < Rotations.arrivedVelocityThreshold.toFloat()) {
      active = false
      yawVelocity = 0f
      pitchVelocity = 0f
    }
  }
}
