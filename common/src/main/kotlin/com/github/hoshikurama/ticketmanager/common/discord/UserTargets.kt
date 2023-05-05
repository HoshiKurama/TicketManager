package com.github.hoshikurama.ticketmanager.common.discord

/*
import com.github.hoshikurama.ticketmanager.common.CommonKeywords

sealed interface DiscordTarget {
    val name: String
    enum class Type {
        PLAYERORSTR, CONSOLE, NO_ONE
    }
}


class DiscordPlayerOrStr(
    override val name: String,
) : DiscordTarget {
    override fun toString() = "PLAYERORSTR.$name"
}

class DiscordConsole(commonKeywords: CommonKeywords) : DiscordTarget {
    override val name = commonKeywords.consoleName
    override fun toString() = "CONSOLE"
}

class DiscordNoOne(commonKeywords: CommonKeywords) : DiscordTarget {
    override val name = commonKeywords.miscNobody
    override fun toString() = "NO_ONE"
}


fun decodeToDiscordTarget(keywords: CommonKeywords, str: String): DiscordTarget {
    val split = str.split(".", limit = 2)

    return when (DiscordTarget.Type.valueOf(split[0])) {
        DiscordTarget.Type.PLAYERORSTR -> DiscordPlayerOrStr(split[1])
        DiscordTarget.Type.CONSOLE -> DiscordConsole(keywords)
        DiscordTarget.Type.NO_ONE -> DiscordNoOne(keywords)
    }
}

 */