package com.example

import org.cobalt.api.addon.Addon

class ExampleAddon : Addon() {

  override fun onInitialize() {
    println("ExampleAddon initialized!")
  }

}
