package com.mighty

import com.mighty.command.MightyCommand
import com.mighty.module.MightyModule
import com.mighty.pathfinder.PathRenderer
import org.cobalt.api.addon.Addon
import org.cobalt.api.command.CommandManager
import org.cobalt.api.event.EventBus
import org.cobalt.api.module.Module

object MightyAddon : Addon() {

  // big shoutout to Nathan for help with the PathRenderer

  override fun onLoad() {
    CommandManager.register(MightyCommand)
    EventBus.register(PathRenderer)

    println("[AutoRat] Ratting retard...")
  }

  override fun onUnload() {
    println("[AutoRat] Unratting retard...")
  }

  override fun getModules(): List<Module> {
    return listOf(MightyModule)
  }
}
