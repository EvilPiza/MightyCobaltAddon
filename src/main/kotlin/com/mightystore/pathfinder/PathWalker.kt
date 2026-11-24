package com.mightystore.pathfinder

import com.mightystore.pathfinder.helper.JumpController
import com.mightystore.pathfinder.helper.Node
import com.mightystore.pathfinder.helper.PathRenderer
import com.mightystore.pathfinder.helper.PathRotation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.cobalt.event.annotation.SubscribeEvent
import org.cobalt.event.impl.TickEvent
import org.cobalt.event.impl.WorldEvent

object PathWalker {
  private val mc = Minecraft.getInstance()

  private var currentPath: List<Node> = emptyList()
  private var currentNodeIndex = 0
  private var isActive = false
  private var currentMovementYaw = Float.NaN
  private var activeLevel: Level? = null

  private var lastPlayerX = 0.0
  private var lastPlayerZ = 0.0
  private var lastFrameTimeNanos = 0L
  private var pathStartTimeNanos = 0L
  private var stuckDurationSeconds = 0f

  private var minStuckThresholdSeconds = 0.15f
  private var maxStuckThresholdSeconds = 0.3f
  private var jumpThresholdSeconds = randomJumpThresholdSeconds()

  private const val REACH_DISTANCE = 0.75f
  private const val FACING_TOLERANCE_DEGREES = 45f
  private const val MOVEMENT_INPUT_THRESHOLD = 0.35
  private const val SPRINT_INPUT_THRESHOLD = 0.6
  private const val MIN_MOVEMENT_SPEED_BLOCKS_PER_SECOND = 2.0
  private const val MAX_FRAME_DELTA_SECONDS = 0.05f
  private const val START_GRACE_PERIOD_SECONDS = 0.25f

  fun setPath(newPath: List<Node>?) {
    if (newPath.isNullOrEmpty()) {
      stop()
      return
    }

    val now = System.nanoTime()
    currentPath = newPath
    currentNodeIndex = 0
    currentMovementYaw = Float.NaN
    activeLevel = mc.level
    stuckDurationSeconds = 0f
    jumpThresholdSeconds = randomJumpThresholdSeconds()
    lastFrameTimeNanos = now
    pathStartTimeNanos = now

    mc.player?.let {
      lastPlayerX = it.x
      lastPlayerZ = it.z
    }

    isActive = true
  }

  fun stop() {
    isActive = false
    currentPath = emptyList()
    currentNodeIndex = 0
    currentMovementYaw = Float.NaN
    activeLevel = null
    stuckDurationSeconds = 0f
    jumpThresholdSeconds = randomJumpThresholdSeconds()
    releaseKeys()
    PathRotation.clearTarget()
    PathRenderer.setPath(null)
  }

  @SubscribeEvent
  fun onTick(event: TickEvent.End) {
    if (!isActive) return
    val player = mc.player ?: return

    if (shouldStop(player)) {
      stop()
      return
    }

    if (currentNodeIndex >= currentPath.size) {
      stop()
      return
    }

    var step = currentPath[currentNodeIndex].step

    while (isWithinReach(player, step.target)) {
      currentNodeIndex++
      stuckDurationSeconds = 0f

      if (currentNodeIndex >= currentPath.size) {
        stop()
        return
      }

      step = currentPath[currentNodeIndex].step
    }

    val nextTarget = currentPath.getOrNull(currentNodeIndex + 1)?.step ?: step

    updateRotation(player, step.target, nextTarget.target)
    currentMovementYaw = yawTowards(player, step.target)
    val facingTarget = PathRotation.isFacing(currentMovementYaw, FACING_TOLERANCE_DEGREES)

    JumpController.update(player, step, facingTarget)
    applyMovementTowards(player, step.target)
  }

  @SubscribeEvent
  fun onFrame(event: WorldEvent.BeforeGizmos) {
    if (!isActive) return
    val player = mc.player ?: return

    if (shouldStop(player)) {
      stop()
      return
    }

    val now = System.nanoTime()
    val deltaSeconds = ((now - lastFrameTimeNanos) / 1_000_000_000f).coerceIn(0f, MAX_FRAME_DELTA_SECONDS)
    lastFrameTimeNanos = now

    updateStuckState(player, deltaSeconds)
  }

  private fun shouldStop(player: LocalPlayer): Boolean =
    mc.gui.screen() != null || mc.level !== activeLevel || player.health <= 0f

  private fun isWithinReach(player: LocalPlayer, targetPos: BlockPos): Boolean {
    val targetCenter = Vec3(targetPos.x + 0.5, targetPos.y + 1.0, targetPos.z + 0.5)
    return player.position().distanceTo(targetCenter) < REACH_DISTANCE
  }

  private fun updateRotation(player: LocalPlayer, targetPos: BlockPos, nextTargetPos: BlockPos) {
    val lookaheadX = (targetPos.x + nextTargetPos.x) / 2.0 + 0.5
    val lookaheadZ = (targetPos.z + nextTargetPos.z) / 2.0 + 0.5
    PathRotation.setTarget(yawTowards(player, lookaheadX, lookaheadZ))
  }

  private fun yawTowards(player: LocalPlayer, targetPos: BlockPos): Float =
    yawTowards(player, targetPos.x + 0.5, targetPos.z + 0.5)

  private fun yawTowards(player: LocalPlayer, x: Double, z: Double): Float {
    val dx = x - player.x
    val dz = z - player.z
    return Math.toDegrees(atan2(-dx, dz)).toFloat()
  }

  private fun applyMovementTowards(player: LocalPlayer, targetPos: BlockPos) {
    val dx = (targetPos.x + 0.5) - player.x
    val dz = (targetPos.z + 0.5) - player.z
    val distance = sqrt(dx * dx + dz * dz)

    if (distance < 1e-6) {
      releaseKeys()
      return
    }

    val desiredX = dx / distance
    val desiredZ = dz / distance
    val yaw = Math.toRadians(player.yRot.toDouble())

    val forwardX = -sin(yaw)
    val forwardZ = cos(yaw)
    val rightX = -cos(yaw)
    val rightZ = -sin(yaw)

    val forwardInput = desiredX * forwardX + desiredZ * forwardZ
    val rightInput = desiredX * rightX + desiredZ * rightZ

    val options = mc.options
    options.keyUp.isDown = forwardInput > MOVEMENT_INPUT_THRESHOLD
    options.keyDown.isDown = forwardInput < -MOVEMENT_INPUT_THRESHOLD
    options.keyRight.isDown = rightInput > MOVEMENT_INPUT_THRESHOLD
    options.keyLeft.isDown = rightInput < -MOVEMENT_INPUT_THRESHOLD
    options.keySprint.isDown = forwardInput > SPRINT_INPUT_THRESHOLD
  }

  private fun updateStuckState(player: LocalPlayer, deltaSeconds: Float) {
    val options = mc.options

    if (JumpController.isActive) {
      stuckDurationSeconds = 0f
      lastPlayerX = player.x
      lastPlayerZ = player.z
      return
    }

    if (!currentMovementYaw.isNaN() && !PathRotation.isFacing(currentMovementYaw, FACING_TOLERANCE_DEGREES)) {
      stuckDurationSeconds = 0f
      options.keyJump.isDown = false
      lastPlayerX = player.x
      lastPlayerZ = player.z
      return
    }

    if ((System.nanoTime() - pathStartTimeNanos) / 1_000_000_000f < START_GRACE_PERIOD_SECONDS) {
      lastPlayerX = player.x
      lastPlayerZ = player.z
      return
    }

    val movedDistance = sqrt((player.x - lastPlayerX) * (player.x - lastPlayerX) + (player.z - lastPlayerZ) * (player.z - lastPlayerZ))
    val movementSpeed = if (deltaSeconds > 0f) movedDistance / deltaSeconds.toDouble() else Double.MAX_VALUE
    val notMakingProgress = player.onGround() && movementSpeed < MIN_MOVEMENT_SPEED_BLOCKS_PER_SECOND
    val blocked = player.onGround() && player.horizontalCollision

    if (notMakingProgress || blocked) {
      stuckDurationSeconds += deltaSeconds
    } else {
      stuckDurationSeconds = 0f
    }

    options.keyJump.isDown = stuckDurationSeconds >= jumpThresholdSeconds

    if (stuckDurationSeconds >= jumpThresholdSeconds) {
      stuckDurationSeconds = 0f
      jumpThresholdSeconds = randomJumpThresholdSeconds()
    }

    lastPlayerX = player.x
    lastPlayerZ = player.z
  }

  private fun randomJumpThresholdSeconds(): Float =
    Random.nextFloat() * (maxStuckThresholdSeconds - minStuckThresholdSeconds) + minStuckThresholdSeconds

  private fun releaseKeys() {
    JumpController.stop()

    val options = mc.options
    options.keyUp.isDown = false
    options.keyDown.isDown = false
    options.keyLeft.isDown = false
    options.keyRight.isDown = false
    options.keySprint.isDown = false
    options.keyJump.isDown = false
  }
}
