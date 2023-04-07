package com.github.hoshikurama.ticketmanager.commonse.ticket

import com.github.hoshikurama.ticketmanager.commonse.apiTesting.Creator
import java.util.*

object CreatorImpl {

    class UserImpl(override val uuid: UUID) : Creator.User {
        override fun hashCode(): Int = super.hashCode2()
        override fun equals(other: Any?): Boolean = (other is Creator.User) && (this equalTo other)
    }

    object ConsoleImpl : Creator.Console

    object Dummy : Creator.Other { //TODO IS IT POSSIBLE TO GET RID OF THIS????
        override fun asString(): String = throw Exception("Attempting to convert a dummy creator to a string!")
    }

    object InvalidUUID : Creator.Other {
        override fun asString(): String = "INVALID_UUID"
    }
}