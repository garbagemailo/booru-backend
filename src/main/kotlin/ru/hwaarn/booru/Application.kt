package ru.hwaarn.booru

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.http.content.staticFiles
import io.ktor.server.plugins.autohead.AutoHeadResponse
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import ru.hwaarn.booru.config.AppConfig
import ru.hwaarn.booru.config.JwtProvider
import ru.hwaarn.booru.config.ServiceRegistry
import ru.hwaarn.booru.db.DatabaseFactory
import ru.hwaarn.booru.model.ApiError
import ru.hwaarn.booru.repository.AuditRepository
import ru.hwaarn.booru.repository.CommunityRepository
import ru.hwaarn.booru.repository.PostRepository
import ru.hwaarn.booru.repository.TaxonomyRepository
import ru.hwaarn.booru.repository.UserRepository
import ru.hwaarn.booru.routes.AccessDeniedException
import ru.hwaarn.booru.routes.UnauthorizedException
import ru.hwaarn.booru.routes.apiRoutes
import ru.hwaarn.booru.service.AuthService
import ru.hwaarn.booru.service.PostService
import ru.hwaarn.booru.util.StorageService
import java.io.File

fun main(args: Array<String>) = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    val appEnvironment = environment
    val appConfig = AppConfig.from(appEnvironment)
    DatabaseFactory.init(appConfig.database)

    val jwtProvider = JwtProvider(appConfig.security)
    val userRepository = UserRepository()
    val taxonomyRepository = TaxonomyRepository()
    val storageService = StorageService(appConfig.storage)
    val postRepository = PostRepository(taxonomyRepository, storageService)
    runBlocking { postRepository.purgeExpiredDeletedPosts() }
    val communityRepository = CommunityRepository(postRepository, taxonomyRepository)
    val auditRepository = AuditRepository()
    ServiceRegistry.userRepository = userRepository
    val authService = AuthService(userRepository, jwtProvider, appConfig.security)
    val postService = PostService(postRepository, storageService, appConfig.moderation)

    install(DefaultHeaders)
    install(AutoHeadResponse)
    install(Compression)
    install(PartialContent)
    install(ConditionalHeaders)
    install(CallLogging) { level = Level.INFO }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            explicitNulls = true
        })
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(io.ktor.http.HttpStatusCode.BadRequest, ApiError(cause.message ?: "Bad request"))
        }
        exception<UnauthorizedException> { call, cause ->
            call.respond(io.ktor.http.HttpStatusCode.Unauthorized, ApiError(cause.message ?: "Unauthorized"))
        }
        exception<AccessDeniedException> { call, cause ->
            call.respond(io.ktor.http.HttpStatusCode.Forbidden, ApiError(cause.message ?: "Forbidden"))
        }
        exception<Throwable> { call, cause ->
            appEnvironment.log.error("Unhandled error", cause)
            call.respond(io.ktor.http.HttpStatusCode.InternalServerError, ApiError(cause.message ?: "Internal server error"))
        }
    }
    install(Authentication) {
        jwt("auth-jwt") {
            realm = appConfig.security.realm
            verifier(jwtProvider.verifier)
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asLong()
                val username = credential.payload.getClaim("username").asString()
                if (userId != null && !username.isNullOrBlank()) JWTPrincipal(credential.payload) else null
            }
        }
    }

    routing {
        val uploadRoot = File(appConfig.storage.uploadRoot).apply { mkdirs() }
        staticFiles("/media", uploadRoot)
        openAPI(path = "openapi", swaggerFile = "openapi/documentation.yaml")
        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
        apiRoutes(
            authService = authService,
            userRepository = userRepository,
            postRepository = postRepository,
            taxonomyRepository = taxonomyRepository,
            communityRepository = communityRepository,
            postService = postService,
            auditRepository = auditRepository,
        )
    }
}
