package com.mighty.pathfinder

import com.mighty.pathfinder.helper.Node
import com.mighty.pathfinder.helper.PathRenderer
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.BlockPos
import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.client.TickEvent
import org.cobalt.api.event.impl.render.WorldRenderEvent
import kotlin.math.atan2
import kotlin.math.sqrt
import com.mighty.pathfinder.helper.Rotation

object PathWalker {
  private val mc = MinecraftClient.getInstance()

  private var path: List<Node> = emptyList()
  private var currentIndex = 0
  private var active = false

  private var lastX = 0.0
  private var lastZ = 0.0
  private var lastFrameNanos = 0L
  private var stuckSeconds = 0f

  private const val nodeReachDistance = 0.75
  private const val stuckMovementThreshold = 0.02
  private const val stuckSecondsBeforeJump = 0.05f
  private const val maxFrameDeltaSeconds = 0.05f

  private var pathStartNanos = 0L
  private const val graceSeconds = 0.25f

  fun setPath(newPath: List<Node>?) {
    if (newPath.isNullOrEmpty()) {
      stop()
      return
    }
    path = newPath
    currentIndex = 0
    stuckSeconds = 0f
    pathStartNanos = System.nanoTime()
    active = true
  }

  fun stop() {
    active = false
    stuckSeconds = 0f
    releaseKeys()
    Rotation.clearTarget()
  }

  @SubscribeEvent
  fun onTick(event: TickEvent.End) {
    if (!active) return
    val player = mc.player ?: return

    if (currentIndex >= path.size) {
      stop()
      PathRenderer.setPath(null)
      return
    }

    var targetPos = path[currentIndex].data.pos

    while (isWithinReach(player, targetPos)) {
      currentIndex++
      stuckSeconds = 0f
      if (currentIndex >= path.size) {
        stop()
        return
      }
      targetPos = path[currentIndex].data.pos
    }

    rotateTowards(player, targetPos)
    applyMovementKeys()
  }

  @SubscribeEvent
  fun onFrame(event: WorldRenderEvent) {
    if (!active) return
    val player = mc.player ?: return

    val now = System.nanoTime()
    val deltaSeconds = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, maxFrameDeltaSeconds)
    lastFrameNanos = now

    updateStuckState(player, deltaSeconds)
  }

  private fun isWithinReach(player: ClientPlayerEntity, targetPos: BlockPos): Boolean {
    val dx = (targetPos.x + 0.5) - player.x
    val dz = (targetPos.z + 0.5) - player.z
    return sqrt(dx * dx + dz * dz) < nodeReachDistance
  }

  private fun rotateTowards(player: ClientPlayerEntity, targetPos: BlockPos) {
    val dx = (targetPos.x + 0.5) - player.x
    val dz = (targetPos.z + 0.5) - player.z

    val targetYaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
    Rotation.setTarget(targetYaw)
  }

  private fun applyMovementKeys() {
    val options = mc.options

    options.forwardKey.isPressed = true
    options.backKey.isPressed = false
    options.leftKey.isPressed = false
    options.rightKey.isPressed = false
    options.sprintKey.isPressed = true
  }

  private fun updateStuckState(player: ClientPlayerEntity, deltaSeconds: Float) {
    val options = mc.options

    if ((System.nanoTime() - pathStartNanos) / 1_000_000_000f < graceSeconds) {
      lastX = player.x
      lastZ = player.z
      return
    }

    if (player.horizontalCollision && player.isOnGround) {
      options.jumpKey.isPressed = true
      stuckSeconds = 0f
      lastX = player.x
      lastZ = player.z
      return
    }

    val movedDistance = sqrt((player.x - lastX) * (player.x - lastX) + (player.z - lastZ) * (player.z - lastZ))

    if (player.isOnGround && movedDistance < stuckMovementThreshold) {
      stuckSeconds += deltaSeconds
    } else {
      stuckSeconds = 0f
    }

    options.jumpKey.isPressed = stuckSeconds >= stuckSecondsBeforeJump

    lastX = player.x
    lastZ = player.z
  }

  private fun releaseKeys() {
    val options = mc.options
    options.forwardKey.isPressed = false
    options.backKey.isPressed = false
    options.leftKey.isPressed = false
    options.rightKey.isPressed = false
    options.sprintKey.isPressed = false
    options.jumpKey.isPressed = false
  }
}
