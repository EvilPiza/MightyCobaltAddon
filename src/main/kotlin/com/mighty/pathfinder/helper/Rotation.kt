package com.mighty.pathfinder.helper

import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.MathHelper
import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.render.WorldRenderEvent
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

object Rotation {
  private val mc = MinecraftClient.getInstance()

  private const val stiffness = 190f
  private const val dampingRatio = 0.58f
  private const val retargetThreshold = 2.5f
  private const val arrivedYawThreshold = 0.15f
  private const val arrivedVelocityThreshold = 1.5f
  private const val maxDeltaSeconds = 0.1f

  private var active = false
  private var targetYaw = 0f
  private var targetPitch: Float? = null
  private var yawVelocity = 0f
  private var pitchVelocity = 0f
  private var lastFrameNanos = 0L
  private var randomPitch = 0f

  fun setTarget(yaw: Float, pitch: Float? = null) {
    val player = mc.player ?: return
    val distance = abs(MathHelper.wrapDegrees(yaw - player.yaw))

    if (!active || distance >= retargetThreshold) {
      randomPitch = Random.nextInt(5, 15).toFloat()
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

  fun isRotating(): Boolean = active

  @SubscribeEvent
  fun onFrame(event: WorldRenderEvent) {
    if (!active) return
    val player = mc.player ?: return

    val now = System.nanoTime()
    val deltaSeconds = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, maxDeltaSeconds)
    lastFrameNanos = now

    val dampingCoefficient = 2f * dampingRatio * sqrt(stiffness)

    val yawError = MathHelper.wrapDegrees(targetYaw - player.yaw)
    val yawAcceleration = stiffness * yawError - dampingCoefficient * yawVelocity
    yawVelocity += yawAcceleration * deltaSeconds
    player.yaw += yawVelocity * deltaSeconds

    targetPitch?.let { pitch ->
      val pitchError = MathHelper.wrapDegrees(pitch - player.pitch)
      val pitchAcceleration = stiffness * pitchError - dampingCoefficient * pitchVelocity
      pitchVelocity += pitchAcceleration * deltaSeconds
      player.pitch = MathHelper.clamp(player.pitch + pitchVelocity * deltaSeconds, -90f, 90f)
    }

    if (abs(yawError) < arrivedYawThreshold && abs(yawVelocity) < arrivedVelocityThreshold) {
      active = false
      yawVelocity = 0f
      pitchVelocity = 0f
    }
  }
}
