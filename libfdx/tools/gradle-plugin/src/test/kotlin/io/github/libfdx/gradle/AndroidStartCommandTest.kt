package io.github.libfdx.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AndroidStartCommandTest {
    @Test
    fun `builds activity launch command with typed intent extras`() {
        val command = androidStartCommand(
            "adb",
            "com.example.game",
            "com.example.game.GameActivity",
            linkedMapOf(
                "libfdx.sample.playerName" to "Player One",
                "libfdx.sample.hostRoomId" to "room-7"
            ),
            linkedMapOf("libfdx.sample.autoHost" to "true")
        )

        assertEquals(
            listOf(
                "adb",
                "shell",
                "am",
                "start",
                "-n",
                "com.example.game/com.example.game.GameActivity",
                "--es",
                "libfdx.sample.playerName",
                "Player One",
                "--es",
                "libfdx.sample.hostRoomId",
                "room-7",
                "--ez",
                "libfdx.sample.autoHost",
                "true"
            ),
            command
        )
    }
}
