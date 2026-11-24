package com.mightystore.pathfinder

import com.mightystore.pathfinder.helper.MovementType
import com.mightystore.pathfinder.helper.Node
import com.mightystore.pathfinder.helper.PathStep
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.FenceBlock
import net.minecraft.world.level.block.FenceGateBlock
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.RailBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.Shapes

class Pathfinder(
  private val startPos: BlockPos,
  private val goalPos: BlockPos
) {

  sealed interface Result {
    data object Searching : Result
    data object NotFound : Result
    data class Found(val path: List<Node>) : Result
  }

  companion object {
    private const val SEARCH_BATCH_SIZE = 250
    private const val MAX_EXPANSIONS = 20_000
    private const val SEARCH_TIMEOUT_MILLIS = 3_000L

    private const val MAX_DROP = 6
    private const val MAX_STEP_UP = 1.2
    private const val REQUIRED_HEADROOM = 1.8
    private const val FULL_BLOCK_HEIGHT = 0.6
    private const val SMOOTH_LOOK_AHEAD = 20

    // Kept from the original pathfinder; these are legacy solid-block ids.
    private const val LEGACY_SOLID_BLOCK_ID = 78
    private const val LEGACY_AVOID_BLOCK_ID = 80
  }

  private val mc = Minecraft.getInstance()

  private val start = Node(PathStep(startPos, MovementType.WALK, 0.0), 0.0, 0.0, null)
  private val goal = Node(PathStep(goalPos, MovementType.WALK, 0.0), 0.0, 0.0, null)

  private val openQueue: PriorityQueue<Node> = PriorityQueue(
    compareBy<Node> { it.fCost }
      .thenBy { it.hCost }
  )
  private val openSet: HashMap<BlockPos, Node> = HashMap()
  private val closedSet: HashMap<BlockPos, Node> = HashMap()
  private val cachedHeuristics: HashMap<BlockPos, Double> = HashMap()
  private val cachedPenalties: HashMap<BlockPos, Double> = HashMap()
  private val stateCache: HashMap<BlockPos, BlockState> = HashMap()

  private val directions = listOf(
    BlockPos(1, 0, 0),
    BlockPos(-1, 0, 0),
    BlockPos(0, 0, 1),
    BlockPos(0, 0, -1)
  )

  private var initialized = false
  private var expandedNodes = 0
  private var startedAtMillis = 0L

  fun search(maxExpansions: Int = SEARCH_BATCH_SIZE): Result {
    if (!initialized) initialize()

    val elapsed = System.currentTimeMillis() - startedAtMillis
    if (expandedNodes >= MAX_EXPANSIONS || elapsed > SEARCH_TIMEOUT_MILLIS) {
      return Result.NotFound
    }

    var processed = 0

    while (processed < maxExpansions) {
      val current = openQueue.poll() ?: return Result.NotFound
      processed++

      val currentPos = current.pos
      if (openSet[currentPos] !== current) continue
      openSet.remove(currentPos)

      if (currentPos == goalPos) {
        goal.parent = current
        return Result.Found(smoothPath(reconstructPath(goal)))
      }

      closedSet[currentPos] = current
      expandedNodes++

      for (step in getNeighbours(currentPos)) {
        if (closedSet.containsKey(step.target)) continue

        val newGCost = current.gCost + step.cost + getPenalty(step.target)
        val existing = openSet[step.target]

        if (existing == null) {
          val node = Node(step, newGCost, getHeuristic(step.target), current)
          openQueue.add(node)
          openSet[step.target] = node
        } else if (newGCost < existing.gCost) {
          val node = Node(step, newGCost, existing.hCost, current)
          openQueue.add(node)
          openSet[step.target] = node
        }
      }
    }

    return Result.Searching
  }

  private fun initialize() {
    start.hCost = getHeuristic(startPos)
    start.fCost = start.gCost + start.hCost

    openQueue.add(start)
    openSet[startPos] = start

    startedAtMillis = System.currentTimeMillis()
    initialized = true
  }

  private fun getCachedState(pos: BlockPos): BlockState =
    stateCache.getOrPut(pos) {
      mc.level?.getBlockState(pos) ?: Blocks.AIR.defaultBlockState()
    }

  private fun isWalkable(pos: BlockPos): Boolean {
    var state = getCachedState(pos)
    var block = state.block

    if (block is RailBlock) {
      state = getCachedState(pos.below())
      block = state.block
    }

    if (block is FenceBlock ||
      block is FenceGateBlock ||
      block.descriptionId.contains("fence")
    ) {
      return false
    }

    state = getCachedState(pos)
    block = state.block

    val isSolid = state.blocksMotion() || BuiltInRegistries.BLOCK.getId(block) == LEGACY_SOLID_BLOCK_ID
    return block !is LiquidBlock && isSolid && hasEnoughHeadroom(pos)
  }

  private fun hasEnoughHeadroom(pos: BlockPos): Boolean {
    val above1 = pos.above()
    val above2 = pos.above(2)

    val state1 = getCachedState(above1)
    val state2 = getCachedState(above2)
    val block1 = state1.block
    val block2 = state2.block

    if (block1 is FenceBlock || block1 is FenceGateBlock ||
      block2 is FenceBlock || block2 is FenceGateBlock
    ) {
      return false
    }

    var clearance = 0.0

    val shape1 = state1.getCollisionShape(mc.level!!, above1)
    if (block1 == Blocks.AIR) {
      clearance += 1.0
    } else if (!shape1.isEmpty) {
      clearance += 1.0 - shape1.bounds().maxY
    }

    val shape2 = state2.getCollisionShape(mc.level!!, above2)
    if (block2 == Blocks.AIR) {
      clearance += 1.0
    } else if (!shape2.isEmpty) {
      clearance += 1.0 - shape2.bounds().maxY
    }

    return clearance >= REQUIRED_HEADROOM
  }

  private fun getNeighbours(from: BlockPos): List<PathStep> {
    val neighbours = ArrayList<PathStep>(directions.size)

    val originShape = getCachedState(from).getCollisionShape(mc.level!!, from)
    val originTopY = if (originShape.isEmpty) 0.0 else originShape.bounds().maxY

    for (offset in directions) {
      val column = from.offset(offset)
      var airStreak = 0
      var totalChecked = 0

      for (y in 1 downTo -MAX_DROP) {
        val candidatePos = column.offset(0, y, 0)
        val candidateState = getCachedState(candidatePos)
        val candidateBlock = candidateState.block
        val candidateShape = candidateState.getCollisionShape(mc.level!!, candidatePos)

        val localTopY = if (candidateShape.isEmpty) 0.0 else candidateShape.bounds().maxY
        val worldTopY = if (candidateShape.isEmpty) y.toDouble() else localTopY + y

        if (worldTopY - originTopY > MAX_STEP_UP) continue

        if (isWalkable(candidatePos)) {
          if (y < -1 && airStreak != totalChecked) break

          var cost = if (y == 1 && localTopY <= FULL_BLOCK_HEIGHT) 2.0 else if (y == 1) 4.0 else 2.0
          if (y < -1) cost *= abs(y).toDouble()

          val landing = if (candidateBlock is RailBlock) candidatePos.below() else candidatePos
          val movement = when {
            y > 0 && localTopY > FULL_BLOCK_HEIGHT -> MovementType.STEP_UP
            y < 0 -> MovementType.DROP
            else -> MovementType.WALK
          }

          neighbours.add(PathStep(landing, movement, cost, takeoff = from))
          break
        }

        totalChecked++
        if (candidateBlock == Blocks.AIR) airStreak++
      }
    }

    return neighbours
  }

  private fun getPenalty(pos: BlockPos): Double {
    cachedPenalties[pos]?.let { return it }

    var penalty = 0.0
    val belowBlock = getCachedState(pos).block

    for (x in -1..1) {
      for (z in -1..1) {
        val checkPos = pos.offset(x, 1, z)
        val checkBlock = getCachedState(checkPos).block
        val id = BuiltInRegistries.BLOCK.getId(checkBlock)

        if (id != 0 && id != LEGACY_SOLID_BLOCK_ID && id != LEGACY_AVOID_BLOCK_ID) {
          penalty += 2.0
        }

        if (belowBlock == Blocks.AIR && checkBlock == Blocks.AIR) {
          penalty += 2.0
        }
      }
    }

    cachedPenalties[pos] = penalty
    return penalty
  }

  private fun getHeuristic(pos: BlockPos): Double =
    cachedHeuristics.getOrPut(pos) {
      val dx = (pos.x - goalPos.x).toDouble()
      val dy = (pos.y - goalPos.y).toDouble()
      val dz = (pos.z - goalPos.z).toDouble()
      sqrt(dx * dx + dy * dy + dz * dz)
    }

  private fun reconstructPath(node: Node): List<Node> {
    val path = ArrayList<Node>()
    var current: Node? = node

    while (current != null) {
      path.add(0, current)
      current = current.parent
    }

    return path
  }

  private fun smoothPath(nodes: List<Node>): List<Node> {
    if (nodes.size <= 2) return nodes

    val smoothed = ArrayList<Node>()
    smoothed.add(nodes[0])

    var current = 0

    while (current < nodes.size - 1) {
      if (nodes[current + 1].step.movement != MovementType.WALK) {
        current++
        smoothed.add(nodes[current])
        continue
      }

      var nextIndex = current + 1
      val maxLookAhead = minOf(SMOOTH_LOOK_AHEAD, nodes.size - current - 1)

      for (lookAhead in maxLookAhead downTo 1) {
        val candidateIndex = current + lookAhead
        if (containsSpecialMove(nodes, current + 1, candidateIndex)) continue

        val from = nodes[current].pos
        val to = nodes[candidateIndex].pos

        val yDiff = to.y - from.y
        var canSkip = true

        if (yDiff < -1) {
          val fromHeight = getCachedState(from).getCollisionShape(mc.level!!, from).bounds().maxY
          val toHeight = getCachedState(to).getCollisionShape(mc.level!!, to).bounds().maxY
          val hasSlabs = abs(fromHeight - 0.5) < 0.01 || abs(toHeight - 0.5) < 0.01

          if (hasSlabs && lookAhead > 3) canSkip = false
        }

        if (canSkip && hasLineOfSight(from, to)) {
          nextIndex = candidateIndex
          break
        }
      }

      current = nextIndex
      smoothed.add(nodes[current])
    }

    return smoothed
  }

  private fun containsSpecialMove(nodes: List<Node>, fromIndex: Int, toIndex: Int): Boolean =
    (fromIndex..toIndex).any { nodes[it].step.movement != MovementType.WALK }

  private fun hasLineOfSight(from: BlockPos, to: BlockPos): Boolean {
    val dx = (to.x - from.x).toDouble()
    val dy = (to.y - from.y).toDouble()
    val dz = (to.z - from.z).toDouble()
    val distance = sqrt(dx * dx + dy * dy + dz * dz)
    val steps = maxOf(1, ceil(distance * 2).toInt())

    for (i in 0..steps) {
      val t = i / steps.toDouble()
      val x = floor(from.x + dx * t).toInt()
      val y = floor(from.y + dy * t).toInt()
      val z = floor(from.z + dz * t).toInt()

      val bodyPos = BlockPos(x, y + 1, z)
      val bodyState = getCachedState(bodyPos)
      val bodyBlock = bodyState.block

      if (bodyBlock != Blocks.AIR &&
        bodyState.blocksMotion() &&
        bodyState.getCollisionShape(mc.level!!, bodyPos) == Shapes.block() &&
        bodyBlock !is LiquidBlock
      ) {
        return false
      }

      if (!isWalkable(BlockPos(x, y, z))) return false
    }

    return true
  }
}
