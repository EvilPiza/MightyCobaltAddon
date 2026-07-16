package com.mightystore.pathfinder

import com.mightystore.pathfinder.helper.Node
import com.mightystore.pathfinder.helper.PathRenderer
import kotlin.math.atan2
import kotlin.math.sqrt
import com.mightystore.pathfinder.helper.Rotation
import kotlin.random.Random
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import org.cobalt.event.annotation.SubscribeEvent
import org.cobalt.event.impl.TickEvent
import org.cobalt.event.impl.WorldEvent

object PathWalker {
  private val mc = Minecraft.getInstance()

  private var currentPath: List<Node> = emptyList()
  private var currentNodeIndex = 0
  private var isActive = false

  private var lastPlayerX = 0.0
  private var lastPlayerZ = 0.0
  private var lastFrameTimeNanos = 0L
  private var pathStartTimeNanos = 0L
  private var stuckDurationSeconds = 0f

  private var minStuckThresholdSeconds = 0.05f
  private var maxStuckThresholdSeconds = 0.1f

  private const val REACH_DISTANCE = 0.75f
  private const val MOVEMENT_STUCK_THRESHOLD = 0.05f
  private const val MAX_FRAME_DELTA_SECONDS = 0.05f
  private const val START_GRACE_PERIOD_SECONDS = 0.25f

  fun setPath(newPath: List<Node>?) {
    if (newPath.isNullOrEmpty()) {
      stop()
      return
    }
    currentPath = newPath
    currentNodeIndex = 0
    stuckDurationSeconds = 0f
    pathStartTimeNanos = System.nanoTime()
    isActive = true
  }

  fun stop() {
    isActive = false
    stuckDurationSeconds = 0f
    releaseKeys()
    Rotation.clearTarget()
  }

  @SubscribeEvent
  fun onTick(event: TickEvent.End) {
    if (!isActive) return
    val player = mc.player ?: return

    if (currentNodeIndex >= currentPath.size) {
      stop()
      PathRenderer.setPath(null)
      return
    }

    var targetPos = currentPath[currentNodeIndex].data.pos

    while (isWithinReach(player, targetPos)) {
      currentNodeIndex++
      stuckDurationSeconds = 0f
      if (currentNodeIndex >= currentPath.size) {
        stop()
        return
      }
      targetPos = currentPath[currentNodeIndex].data.pos
    }

    rotateTowards(player, targetPos)
    applyMovementKeys()
  }

  @SubscribeEvent
  fun onFrame(event: WorldEvent.Render) {
    if (!isActive) return
    val player = mc.player ?: return

    val now = System.nanoTime()
    val deltaSeconds = ((now - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0f, MAX_FRAME_DELTA_SECONDS)
    lastFrameTimeNanos = now

    updateStuckState(player, deltaSeconds)
  }

  private fun isWithinReach(player: LocalPlayer, targetPos: BlockPos): Boolean {
    val dx = (targetPos.x + 0.5) - player.x
    val dz = (targetPos.z + 0.5) - player.z
    return sqrt(dx * dx + dz * dz) < REACH_DISTANCE
  }

  private fun rotateTowards(player: LocalPlayer, targetPos: BlockPos) {
    val dx = (targetPos.x + 0.5) - player.x
    val dz = (targetPos.z + 0.5) - player.z

    val targetYaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
    Rotation.setTarget(targetYaw)
  }

  private fun applyMovementKeys() {
    val options = mc.options

    options.keyUp.isDown = true
    options.keyDown.isDown = false
    options.keyLeft.isDown = false
    options.keyRight.isDown = false
    options.keySprint.isDown = true
  }

  private fun updateStuckState(player: LocalPlayer, deltaSeconds: Float) {
    val options = mc.options

    if ((System.nanoTime() - pathStartTimeNanos) / 1_000_000_000f < START_GRACE_PERIOD_SECONDS) {
      lastPlayerX = player.x
      lastPlayerZ = player.z
      return
    }

    if (player.horizontalCollision && player.onGround()) {
      options.keyJump.isDown = true
      stuckDurationSeconds = 0f
      lastPlayerX = player.x
      lastPlayerZ = player.z
      return
    }

    val movedDistance = sqrt((player.x - lastPlayerX) * (player.x - lastPlayerX) + (player.z - lastPlayerZ) * (player.z - lastPlayerZ))

    if (player.onGround() && movedDistance < MOVEMENT_STUCK_THRESHOLD) {
      stuckDurationSeconds += deltaSeconds
    } else {
      stuckDurationSeconds = 0f
    }

    options.keyJump.isDown = stuckDurationSeconds >= Random.nextFloat() * (maxStuckThresholdSeconds - minStuckThresholdSeconds) + minStuckThresholdSeconds

    lastPlayerX = player.x
    lastPlayerZ = player.z
  }

  private fun releaseKeys() {
    val options = mc.options
    options.keyUp.isDown = false
    options.keyDown.isDown = false
    options.keyLeft.isDown = false
    options.keyRight.isDown = false
    options.keySprint.isDown = false
    options.keyJump.isDown = false
  }
}
