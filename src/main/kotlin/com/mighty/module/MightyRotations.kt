package com.mighty.module

import org.cobalt.module.Module
import org.cobalt.module.ModuleCategory
import org.cobalt.ui.component.setting.impl.RangeSetting
import org.cobalt.ui.component.setting.impl.SliderSetting
import org.cobalt.ui.component.setting.impl.CheckboxSetting

object MightyRotations : Module(
  name = "Mighty Rotations",
  category = ModuleCategory.MISC,
  toggleable = false,
  startValue = true
) {
  val useCobaltRots by CheckboxSetting(
    name = "Use Default",
    description = "Enabling uses Cobalt's built in Rots",
    defaultValue = false
  )

  val stiffness by SliderSetting(
    name = "Stiffness",
    description = "How strongly rotation snaps toward the target",
    defaultValue = 190,
    min = 50,
    max = 400
  )

  val dampingRatio by SliderSetting(
    name = "Damping Ratio",
    description = "How much the rotation resists overshooting, (value is divided by 100)",
    defaultValue = 58,
    min = 10,
    max = 150
  )

  val retargetThreshold by SliderSetting(
    name = "Retarget Threshold",
    description = "Yaw difference needed to treat a target as new, (value is divided by 100)",
    defaultValue = 250,
    min = 50,
    max = 1000
  )

  val arrivedVelocityThreshold by SliderSetting(
    name = "Arrived Velocity Threshold",
    description = "Rotation speed considered close enough to stopped (value is divided by 100)",
    defaultValue = 150,
    min = 10,
    max = 500
  )

  val randomPitchRange by RangeSetting(
    name = "Random Pitch Range",
    description = "Pitch range used when no explicit pitch is given",
    defaultValue = Pair(5, 15),
    min = -15,
    max = 30
  )
}
