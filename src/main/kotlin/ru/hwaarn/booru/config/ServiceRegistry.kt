package ru.hwaarn.booru.config

import ru.hwaarn.booru.repository.UserRepository

object ServiceRegistry {
    lateinit var userRepository: UserRepository
}
