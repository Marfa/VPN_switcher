package dev.themarfa.vpnswitcher.shizuku

import dev.themarfa.vpnswitcher.IUserService

class UserService : IUserService.Stub() {

    override fun destroy() = Unit

    override fun execCommand(command: Array<String>): Int {
        val process = Runtime.getRuntime().exec(command)
        return process.waitFor()
    }

    override fun execCommandWithOutput(command: Array<String>): String {
        val process = Runtime.getRuntime().exec(command)
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val code = process.waitFor()
        return "$code\n$stdout\n---STDERR---\n$stderr"
    }
}
