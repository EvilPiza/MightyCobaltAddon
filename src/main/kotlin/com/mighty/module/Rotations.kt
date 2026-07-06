package com.mighty.module

import org.cobalt.api.module.Module
import org.cobalt.api.module.setting.impl.SliderSetting
import org.cobalt.api.module.setting.impl.RangeSetting

object Rotations : Module(
  name = "Rotations",
) {
  val stiffness by SliderSetting(
    name = "Stiffness",
    description = "How strongly rotation snaps toward the target",
    defaultValue = 190.0,
    min = 50.0,
    max = 400.0
  )

  val dampingRatio by SliderSetting(
    name = "Damping Ratio",
    description = "How much the rotation resists overshooting",
    defaultValue = 0.58,
    min = 0.1,
    max = 1.5
  )

  val retargetThreshold by SliderSetting(
    name = "Retarget Threshold",
    description = "Yaw difference needed to treat a target as new",
    defaultValue = 2.5,
    min = 0.5,
    max = 10.0
  )

  val arrivedVelocityThreshold by SliderSetting(
    name = "Arrived Velocity Threshold",
    description = "Rotation speed considered close enough to stopped",
    defaultValue = 1.5,
    min = 0.1,
    max = 5.0
  )

  val randomPitchRange by RangeSetting(
    name = "Random Pitch Range",
    description = "Pitch range used when no explicit pitch is given",
    defaultValue = Pair(5.0, 15.0),
    min = 0.0,
    max = 45.0
  )
}
