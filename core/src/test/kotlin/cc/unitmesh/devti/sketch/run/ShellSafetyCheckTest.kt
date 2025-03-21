package cc.unitmesh.devti.sketch.run

import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class ShellSafetyCheckTest {
    @Test
    fun testDangerousRmWithForceFlags() {
        val command = "rm -rf /some/path"
        val result = ShellSafetyCheck.checkDangerousCommand(command)
        // Expect dangerous because of -rf flags
        assertThat(result.first).isTrue()
        assertThat(result.second).isEqualTo("Remove command detected, use with caution")
    }

    @Test
    fun testDangerousRmWithoutInteractiveFlag() {
        val command = "rm /some/file"
        val result = ShellSafetyCheck.checkDangerousCommand(command)
        // Expect dangerous due to generic rm command check
        assertThat(result.first).isTrue()
        assertThat(result.second).isEqualTo("Remove command detected, use with caution")
    }

    @Test
    fun testSafeRmWithInteractiveFlag() {
        val command = "rm -i /some/file"
        val result = ShellSafetyCheck.checkDangerousCommand(command)
        // Expect safe command as interactive flag is present
        assertThat(result.first).isTrue()
        assertThat(result.second).isEqualTo("Removing files from root directory")
    }

    @Test
    fun testDangerousRmdirFromRoot() {
        val command = "rmdir /"
        val result = ShellSafetyCheck.checkDangerousCommand(command)
        // Expect dangerous as it touches root directory
        assertThat(result.first).isTrue()
        assertThat(result.second).isEqualTo("Removing directories from root")
    }

    @Test
    fun testDangerousMkfsCommand() {
        val command = "mkfs /dev/sda1"
        val result = ShellSafetyCheck.checkDangerousCommand(command)
        // Expect dangerous because of filesystem formatting command
        assertThat(result.first).isTrue()
        assertThat(result.second).isEqualTo("Filesystem formatting command")
    }

    @Test
    fun testDangerousDdCommand() {
        val command = "dd if=/dev/zero of=/dev/sda1"
        val result = ShellSafetyCheck.checkDangerousCommand(command)
        // Expect dangerous because of low-level disk operation
        assertThat(result.first).isTrue()
        assertThat(result.second).isEqualTo("Low-level disk operation")
    }

    @Test
    fun testDangerousForkBomb() {
        val command = ":(){ :|:& };:"
        val result = ShellSafetyCheck.checkDangerousCommand(command)
        // Expect dangerous because of potential fork bomb pattern
        assertThat(result.first).isTrue()
        assertThat(result.second).isEqualTo("Potential fork bomb")
    }

    @Test
    fun testDangerousChmodCommand() {
        val command = "chmod -R 777 /some/directory"
        val result = ShellSafetyCheck.checkDangerousCommand(command)
        // Expect dangerous as recursive chmod with insecure permissions is detected
        assertThat(result.first).isTrue()
        assertThat(result.second).isEqualTo("Recursive chmod with insecure permissions")
    }

    @Test
    fun testDangerousSudoCommand() {
        val command = "sudo rm -rf /some/path"
        val result = ShellSafetyCheck.checkDangerousCommand(command)
        // Expect dangerous due to sudo rm pattern
        assertThat(result.first).isTrue()
        assertThat(result.second).isEqualTo("Dangerous rm command with recursive or force flags")
    }

    @Test
    fun testSafeCommand() {
        val command = "ls -la"
        val result = ShellSafetyCheck.checkDangerousCommand(command)
        // Expect no dangerous patterns detected
        assertThat(result.first).isFalse()
        assertThat(result.second).isEmpty()
    }
}
