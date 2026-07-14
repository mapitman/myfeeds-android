package io.pitman.myfeeds

import javax.inject.Inject

class Greeter @Inject constructor() {
    fun greet(name: String) = "Hello $name!"
}
