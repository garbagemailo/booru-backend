package ru.hwaarn.booru.routes

import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.jvm.javaio.toInputStream
import ru.hwaarn.booru.config.ServiceRegistry
import ru.hwaarn.booru.config.UserSession
import ru.hwaarn.booru.config.toUserSession
import ru.hwaarn.booru.model.ApiError
import ru.hwaarn.booru.model.AuthRequest
import ru.hwaarn.booru.model.CreateAliasRequest
import ru.hwaarn.booru.model.CreateArtistRequest
import ru.hwaarn.booru.model.CreateCommentRequest
import ru.hwaarn.booru.model.CreateImplicationRequest
import ru.hwaarn.booru.model.CreateNoteRequest
import ru.hwaarn.booru.model.CreatePoolRequest
import ru.hwaarn.booru.model.CreateTagSubscriptionRequest
import ru.hwaarn.booru.model.CreateUserBanRequest
import ru.hwaarn.booru.model.CreateWikiPageRequest
import ru.hwaarn.booru.model.ModerationRequest
import ru.hwaarn.booru.model.ModerationResolutionRequest
import ru.hwaarn.booru.model.PostRating
import ru.hwaarn.booru.model.PostStatus
import ru.hwaarn.booru.model.RefreshRequest
import ru.hwaarn.booru.model.RegisterRequest
import ru.hwaarn.booru.model.UpdateArtistRequest
import ru.hwaarn.booru.model.UpdateCommentRequest
import ru.hwaarn.booru.model.UpdateNoteRequest
import ru.hwaarn.booru.model.UpdatePoolRequest
import ru.hwaarn.booru.model.UpdatePostRequest
import ru.hwaarn.booru.model.UpdateUserRoleRequest
import ru.hwaarn.booru.model.UpdateWikiPageRequest
import ru.hwaarn.booru.model.UploadMetadata
import ru.hwaarn.booru.model.UserRole
import ru.hwaarn.booru.model.VoteRequest
import ru.hwaarn.booru.repository.AuditRepository
import ru.hwaarn.booru.repository.CommunityRepository
import ru.hwaarn.booru.repository.PostRepository
import ru.hwaarn.booru.repository.TaxonomyRepository
import ru.hwaarn.booru.repository.UserRepository
import ru.hwaarn.booru.service.AuthService
import ru.hwaarn.booru.service.PostService
import java.time.Instant

fun Route.apiRoutes(
    authService: AuthService,
    userRepository: UserRepository,
    postRepository: PostRepository,
    taxonomyRepository: TaxonomyRepository,
    communityRepository: CommunityRepository,
    postService: PostService,
    auditRepository: AuditRepository,
) {
    get("/health") {
        call.respond(mapOf("status" to "ok"))
    }

    route("/api/v1") {
        route("/auth") {
            post("/register") {
                val request = call.receive<RegisterRequest>()
                val result = authService.register(request.username, request.email, request.password)
                call.respond(HttpStatusCode.Created, result)
            }
            post("/login") {
                val request = call.receive<AuthRequest>()
                val result = authService.login(request)
                if (result == null) call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid credentials or account is disabled"))
                else call.respond(result)
            }
            post("/refresh") {
                val request = call.receive<RefreshRequest>()
                val result = authService.refresh(request.refreshToken)
                if (result == null) call.respond(HttpStatusCode.Unauthorized, ApiError("Invalid refresh token"))
                else call.respond(result)
            }
            post("/logout") {
                val request = call.receive<RefreshRequest>()
                authService.logout(request.refreshToken)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        route("/posts") {
            get {
                val tags = call.request.queryParameters["tags"]
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                call.respond(postRepository.searchPosts(tags, page, limit))
            }
            get("/count") {
                val tags = call.request.queryParameters["tags"]
                val total = postRepository.searchPosts(tags, 1, 1).total
                call.respond(mapOf("total" to total))
            }
            get("/{id}") {
                val id = call.parameters["id"]?.toLongOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                val post = postRepository.getPost(id)
                if (post == null) call.respond(HttpStatusCode.NotFound, ApiError("Post not found")) else call.respond(post)
            }
        }

        route("/tags") {
            get {
                val search = call.request.queryParameters["search"]
                val category = call.request.queryParameters["category"]
                    ?.uppercase()
                    ?.let { runCatching { ru.hwaarn.booru.model.TagCategory.valueOf(it) }.getOrNull() }
                call.respond(taxonomyRepository.listTags(search, category))
            }
        }

        route("/pools") {
            get { call.respond(communityRepository.listPools()) }
            get("/{id}") {
                val id = call.parameters["id"]?.toLongOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                val pool = communityRepository.getPool(id)
                if (pool == null) call.respond(HttpStatusCode.NotFound, ApiError("Pool not found")) else call.respond(pool)
            }
        }

        route("/wiki_pages") {
            get {
                val q = call.request.queryParameters["search"]
                call.respond(communityRepository.listWikiPages(q))
            }
            get("/{id}") {
                val id = call.parameters["id"]?.toLongOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                val page = communityRepository.getWikiPage(id)
                if (page == null) call.respond(HttpStatusCode.NotFound, ApiError("Wiki page not found")) else call.respond(page)
            }
            get("/{id}/versions") {
                val id = call.parameters["id"]?.toLongOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                call.respond(communityRepository.listWikiVersions(id))
            }
        }

        route("/comments") {
            get {
                val postId = call.request.queryParameters["postId"]?.toLongOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("postId required"))
                call.respond(communityRepository.listComments(postId))
            }
        }

        route("/notes") {
            get {
                val postId = call.request.queryParameters["postId"]?.toLongOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("postId required"))
                call.respond(communityRepository.listNotes(postId))
            }
        }

        route("/artists") {
            get {
                val q = call.request.queryParameters["search"]
                call.respond(communityRepository.listArtists(q))
            }
            get("/{id}") {
                val id = call.parameters["id"]?.toLongOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                val artist = communityRepository.getArtist(id)
                if (artist == null) call.respond(HttpStatusCode.NotFound, ApiError("Artist not found")) else call.respond(artist)
            }
        }

        authenticate("auth-jwt") {
            route("/users") {
                get("/me") {
                    val session = call.requireSession()
                    val user = userRepository.getUserById(session.userId)
                    if (user == null) call.respond(HttpStatusCode.NotFound, ApiError("User not found")) else call.respond(user)
                }
                get {
                    call.requireRole(UserRole.MODERATOR, UserRole.ADMIN)
                    call.respond(userRepository.listUsers())
                }
                get("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val user = userRepository.getUserById(id)
                    if (user == null) call.respond(HttpStatusCode.NotFound, ApiError("User not found")) else call.respond(user)
                }
                patch("/{id}/role") {
                    val session = call.requireSession()
                    call.requireRole(UserRole.ADMIN)
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@patch call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val request = call.receive<UpdateUserRoleRequest>()
                    val user = userRepository.updateRole(id, request.role)
                    if (user == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("User not found"))
                    } else {
                        auditRepository.log(session.userId, "user.role.update", "user", id, "role=${request.role.name}")
                        call.respond(user)
                    }
                }
            }

            route("/posts") {
                post("/upload") {
                    val session = call.requireSession()
                    var fileBytes: ByteArray? = null
                    var fileName: String? = null
                    var tags = ""
                    var rating = PostRating.SAFE
                    var source: String? = null
                    var parentId: Long? = null

                    val multipart = call.receiveMultipart()
                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FormItem -> when (part.name) {
                                "tags" -> tags = part.value
                                "rating" -> rating = runCatching { PostRating.valueOf(part.value.uppercase()) }.getOrElse { PostRating.SAFE }
                                "source" -> source = part.value.ifBlank { null }
                                "parentId" -> parentId = part.value.toLongOrNull()
                            }
                            is PartData.FileItem -> {
                                fileName = part.originalFileName
                                fileBytes = part.provider().toInputStream().use { it.readBytes() }
                            }
                            else -> Unit
                        }
                        part.dispose()
                    }

                    val post = postService.upload(
                        user = session,
                        metadata = UploadMetadata(tags = tags, rating = rating, source = source, parentId = parentId),
                        originalFileName = fileName,
                        bytes = fileBytes,
                    )
                    call.respond(HttpStatusCode.Created, post)
                }
                patch("/{id}") {
                    val session = call.requireSession()
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@patch call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val ownerId = postRepository.getUploaderId(id) ?: return@patch call.respond(HttpStatusCode.NotFound, ApiError("Post not found"))
                    call.requireOwnershipOrRole(ownerId, UserRole.MODERATOR, UserRole.ADMIN)
                    val request = call.receive<UpdatePostRequest>()
                    val post = postRepository.updatePost(id, session.userId, request)
                    if (post == null) call.respond(HttpStatusCode.NotFound, ApiError("Post not found")) else call.respond(post)
                }
                post("/{id}/favorite") {
                    val session = call.requireSession()
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    postRepository.addFavorite(id, session.userId)
                    call.respond(HttpStatusCode.NoContent)
                }
                delete("/{id}/favorite") {
                    val session = call.requireSession()
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    postRepository.removeFavorite(id, session.userId)
                    call.respond(HttpStatusCode.NoContent)
                }
                post("/{id}/vote") {
                    val session = call.requireSession()
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val request = call.receive<VoteRequest>()
                    postRepository.vote(id, session.userId, request.score)
                    call.respond(HttpStatusCode.NoContent)
                }
                delete("/{id}/vote") {
                    val session = call.requireSession()
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    postRepository.unvote(id, session.userId)
                    call.respond(HttpStatusCode.NoContent)
                }
                get("/{id}/versions") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    call.respond(postRepository.listVersions(id))
                }
            }

            get("/favorites") {
                val session = call.requireSession()
                val userId = call.request.queryParameters["userId"]?.toLongOrNull() ?: session.userId
                call.respond(postRepository.listFavorites(userId))
            }

            get("/uploads") {
                val session = call.requireSession()
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                call.respond(postRepository.listUploads(session.userId, limit.coerceIn(1, 100)))
            }

            route("/comments") {
                post {
                    val session = call.requireSession()
                    val request = call.receive<CreateCommentRequest>()
                    val comment = communityRepository.createComment(session.userId, request)
                    if (comment == null) call.respond(HttpStatusCode.NotFound, ApiError("Post not found")) else call.respond(HttpStatusCode.Created, comment)
                }
                patch("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@patch call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val ownerId = communityRepository.getCommentAuthorId(id) ?: return@patch call.respond(HttpStatusCode.NotFound, ApiError("Comment not found"))
                    call.requireOwnershipOrRole(ownerId, UserRole.MODERATOR, UserRole.ADMIN)
                    val request = call.receive<UpdateCommentRequest>()
                    val comment = communityRepository.updateComment(id, request)
                    if (comment == null) call.respond(HttpStatusCode.NotFound, ApiError("Comment not found")) else call.respond(comment)
                }
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val ownerId = communityRepository.getCommentAuthorId(id) ?: return@delete call.respond(HttpStatusCode.NotFound, ApiError("Comment not found"))
                    call.requireOwnershipOrRole(ownerId, UserRole.MODERATOR, UserRole.ADMIN)
                    if (communityRepository.deleteComment(id)) call.respond(HttpStatusCode.NoContent)
                    else call.respond(HttpStatusCode.NotFound, ApiError("Comment not found"))
                }
            }

            route("/notes") {
                post {
                    val session = call.requireSession()
                    val request = call.receive<CreateNoteRequest>()
                    val note = communityRepository.createNote(session.userId, request)
                    if (note == null) call.respond(HttpStatusCode.NotFound, ApiError("Post not found")) else call.respond(HttpStatusCode.Created, note)
                }
                patch("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@patch call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val ownerId = communityRepository.getNoteCreatorId(id) ?: return@patch call.respond(HttpStatusCode.NotFound, ApiError("Note not found"))
                    call.requireOwnershipOrRole(ownerId, UserRole.MODERATOR, UserRole.ADMIN)
                    val request = call.receive<UpdateNoteRequest>()
                    val note = communityRepository.updateNote(id, request)
                    if (note == null) call.respond(HttpStatusCode.NotFound, ApiError("Note not found")) else call.respond(note)
                }
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val ownerId = communityRepository.getNoteCreatorId(id) ?: return@delete call.respond(HttpStatusCode.NotFound, ApiError("Note not found"))
                    call.requireOwnershipOrRole(ownerId, UserRole.MODERATOR, UserRole.ADMIN)
                    if (communityRepository.deleteNote(id)) call.respond(HttpStatusCode.NoContent)
                    else call.respond(HttpStatusCode.NotFound, ApiError("Note not found"))
                }
            }

            route("/tags") {
                post("/aliases") {
                    val session = call.requireSession()
                    call.requireRole(UserRole.MODERATOR, UserRole.ADMIN)
                    val request = call.receive<CreateAliasRequest>()
                    val tag = taxonomyRepository.createAlias(request, session.userId)
                    auditRepository.log(session.userId, "tag.alias.create", "tag", tag.id, "antecedent=${request.antecedentName};consequent=${request.consequentName}")
                    call.respond(HttpStatusCode.Created, tag)
                }
                post("/implications") {
                    val session = call.requireSession()
                    call.requireRole(UserRole.MODERATOR, UserRole.ADMIN)
                    val request = call.receive<CreateImplicationRequest>()
                    taxonomyRepository.createImplication(request, session.userId)
                    auditRepository.log(session.userId, "tag.implication.create", "tag", null, "antecedent=${request.antecedentName};consequent=${request.consequentName}")
                    call.respond(HttpStatusCode.Created)
                }
            }

            route("/tag_subscriptions") {
                get {
                    val session = call.requireSession()
                    call.respond(communityRepository.listSubscriptions(session.userId))
                }
                post {
                    val session = call.requireSession()
                    val request = call.receive<CreateTagSubscriptionRequest>()
                    val subscription = communityRepository.createSubscription(session.userId, request)
                    call.respond(HttpStatusCode.Created, subscription)
                }
                get("/{id}/posts") {
                    val session = call.requireSession()
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val ownerId = communityRepository.getSubscriptionOwnerId(id) ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("Subscription not found"))
                    call.requireOwnershipOrRole(ownerId, UserRole.MODERATOR, UserRole.ADMIN)
                    val subscription = communityRepository.getSubscription(id) ?: return@get call.respond(HttpStatusCode.NotFound, ApiError("Subscription not found"))
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                    call.respond(postRepository.searchPosts(subscription.query, page, limit))
                }
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val ownerId = communityRepository.getSubscriptionOwnerId(id) ?: return@delete call.respond(HttpStatusCode.NotFound, ApiError("Subscription not found"))
                    call.requireOwnershipOrRole(ownerId, UserRole.MODERATOR, UserRole.ADMIN)
                    if (communityRepository.deleteSubscription(id)) call.respond(HttpStatusCode.NoContent)
                    else call.respond(HttpStatusCode.NotFound, ApiError("Subscription not found"))
                }
            }

            route("/pools") {
                post {
                    val session = call.requireSession()
                    val request = call.receive<CreatePoolRequest>()
                    val pool = communityRepository.createPool(session.userId, request)
                    call.respond(HttpStatusCode.Created, pool ?: ApiError("Failed to create pool"))
                }
                patch("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@patch call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val ownerId = communityRepository.getPoolCreatorId(id) ?: return@patch call.respond(HttpStatusCode.NotFound, ApiError("Pool not found"))
                    call.requireOwnershipOrRole(ownerId, UserRole.MODERATOR, UserRole.ADMIN)
                    val request = call.receive<UpdatePoolRequest>()
                    val pool = communityRepository.updatePool(id, request)
                    if (pool == null) call.respond(HttpStatusCode.NotFound, ApiError("Pool not found")) else call.respond(pool)
                }
                delete("/{id}") {
                    call.requireRole(UserRole.MODERATOR, UserRole.ADMIN)
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    if (communityRepository.deletePool(id)) call.respond(HttpStatusCode.NoContent)
                    else call.respond(HttpStatusCode.NotFound, ApiError("Pool not found"))
                }
            }

            route("/wiki_pages") {
                post {
                    val session = call.requireSession()
                    val request = call.receive<CreateWikiPageRequest>()
                    val page = communityRepository.createWikiPage(session.userId, request)
                    call.respond(HttpStatusCode.Created, page ?: ApiError("Failed to create wiki page"))
                }
                patch("/{id}") {
                    val session = call.requireSession()
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@patch call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val ownerId = communityRepository.getWikiCreatorId(id) ?: return@patch call.respond(HttpStatusCode.NotFound, ApiError("Wiki page not found"))
                    call.requireOwnershipOrRole(ownerId, UserRole.MODERATOR, UserRole.ADMIN)
                    val request = call.receive<UpdateWikiPageRequest>()
                    val page = communityRepository.updateWikiPage(id, session.userId, request)
                    if (page == null) call.respond(HttpStatusCode.NotFound, ApiError("Wiki page not found")) else call.respond(page)
                }
            }

            route("/artists") {
                post {
                    val session = call.requireSession()
                    val request = call.receive<CreateArtistRequest>()
                    val artist = communityRepository.createArtist(session.userId, request)
                    call.respond(HttpStatusCode.Created, artist ?: ApiError("Failed to create artist"))
                }
                patch("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@patch call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val ownerId = communityRepository.getArtistCreatorId(id) ?: return@patch call.respond(HttpStatusCode.NotFound, ApiError("Artist not found"))
                    call.requireOwnershipOrRole(ownerId, UserRole.MODERATOR, UserRole.ADMIN)
                    val request = call.receive<UpdateArtistRequest>()
                    val artist = communityRepository.updateArtist(id, request)
                    if (artist == null) call.respond(HttpStatusCode.NotFound, ApiError("Artist not found")) else call.respond(artist)
                }
                delete("/{id}") {
                    call.requireRole(UserRole.MODERATOR, UserRole.ADMIN)
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@delete call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    if (communityRepository.deleteArtist(id)) call.respond(HttpStatusCode.NoContent)
                    else call.respond(HttpStatusCode.NotFound, ApiError("Artist not found"))
                }
            }

            route("/moderation") {
                get("/queue") {
                    call.requireRole(UserRole.JANITOR, UserRole.MODERATOR, UserRole.ADMIN)
                    call.respond(postRepository.searchPosts("status:pending", 1, 100))
                }
                get("/flags") {
                    call.requireRole(UserRole.JANITOR, UserRole.MODERATOR, UserRole.ADMIN)
                    call.respond(communityRepository.listFlags())
                }
                get("/appeals") {
                    call.requireRole(UserRole.JANITOR, UserRole.MODERATOR, UserRole.ADMIN)
                    call.respond(communityRepository.listAppeals())
                }
                get("/bans") {
                    call.requireRole(UserRole.MODERATOR, UserRole.ADMIN)
                    val activeOnly = call.request.queryParameters["activeOnly"]?.toBooleanStrictOrNull() ?: false
                    call.respond(userRepository.listBans(activeOnly))
                }
                get("/audit_logs") {
                    call.requireRole(UserRole.MODERATOR, UserRole.ADMIN)
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 100
                    call.respond(auditRepository.list(limit.coerceIn(1, 500)))
                }
                post("/flags") {
                    val session = call.requireSession()
                    val request = call.receive<ModerationRequest>()
                    val flag = communityRepository.createFlag(session.userId, request)
                    if (flag == null) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("You can only report active posts uploaded by other users"))
                    } else {
                        call.respond(HttpStatusCode.Created, flag)
                    }
                }
                post("/appeals") {
                    val session = call.requireSession()
                    val request = call.receive<ModerationRequest>()
                    val appeal = communityRepository.createAppeal(session.userId, request)
                    if (appeal == null) {
                        call.respond(HttpStatusCode.BadRequest, ApiError("You can only appeal your own deleted or rejected posts"))
                    } else {
                        call.respond(HttpStatusCode.Created, appeal)
                    }
                }
                post("/flags/{id}/resolve") {
                    val session = call.requireSession()
                    call.requireRole(UserRole.JANITOR, UserRole.MODERATOR, UserRole.ADMIN)
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val request = call.receive<ModerationResolutionRequest>()
                    val record = communityRepository.resolveFlag(id, session.userId, request.status)
                    if (record == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("Flag not found"))
                    } else {
                        if (record.status.equals("APPROVED", ignoreCase = true)) {
                            postRepository.setStatus(record.postId, PostStatus.DELETED, session.userId)
                            auditRepository.log(session.userId, "post.delete.by_flag", "post", record.postId, "flag=$id")
                        }
                        auditRepository.log(session.userId, "post.flag.resolve", "post_flag", id, "status=${record.status}")
                        call.respond(record)
                    }
                }
                post("/appeals/{id}/resolve") {
                    val session = call.requireSession()
                    call.requireRole(UserRole.JANITOR, UserRole.MODERATOR, UserRole.ADMIN)
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val request = call.receive<ModerationResolutionRequest>()
                    val record = communityRepository.resolveAppeal(id, session.userId, request.status)
                    if (record == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("Appeal not found"))
                    } else {
                        if (record.status.equals("APPROVED", ignoreCase = true)) {
                            postRepository.setStatus(record.postId, PostStatus.ACTIVE, session.userId)
                            auditRepository.log(session.userId, "post.restore.by_appeal", "post", record.postId, "appeal=$id")
                        }
                        auditRepository.log(session.userId, "post.appeal.resolve", "post_appeal", id, "status=${record.status}")
                        call.respond(record)
                    }
                }
                post("/posts/{id}/approve") {
                    val session = call.requireSession()
                    call.requireRole(UserRole.JANITOR, UserRole.MODERATOR, UserRole.ADMIN)
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val post = postRepository.setStatus(id, PostStatus.ACTIVE, session.userId)
                    if (post == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("Post not found"))
                    } else {
                        auditRepository.log(session.userId, "post.approve", "post", id, null)
                        call.respond(post)
                    }
                }
                post("/posts/{id}/reject") {
                    val session = call.requireSession()
                    call.requireRole(UserRole.MODERATOR, UserRole.ADMIN)
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val post = postRepository.setStatus(id, PostStatus.REJECTED, session.userId)
                    if (post == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("Post not found"))
                    } else {
                        auditRepository.log(session.userId, "post.reject", "post", id, null)
                        call.respond(post)
                    }
                }
                post("/posts/{id}/delete") {
                    val session = call.requireSession()
                    call.requireRole(UserRole.MODERATOR, UserRole.ADMIN)
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val post = postRepository.setStatus(id, PostStatus.DELETED, session.userId)
                    if (post == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("Post not found"))
                    } else {
                        auditRepository.log(session.userId, "post.delete", "post", id, null)
                        call.respond(post)
                    }
                }
                post("/posts/{id}/restore") {
                    val session = call.requireSession()
                    call.requireRole(UserRole.MODERATOR, UserRole.ADMIN)
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val post = postRepository.setStatus(id, PostStatus.ACTIVE, session.userId)
                    if (post == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("Post not found"))
                    } else {
                        auditRepository.log(session.userId, "post.restore", "post", id, null)
                        call.respond(post)
                    }
                }
                post("/bans") {
                    val session = call.requireSession()
                    call.requireRole(UserRole.MODERATOR, UserRole.ADMIN)
                    val request = call.receive<CreateUserBanRequest>()
                    val expiresAt = request.expiresAt?.let(Instant::parse)
                    val ban = userRepository.createBan(request.userId, session.userId, request.reason, expiresAt)
                    auditRepository.log(session.userId, "user.ban.create", "user_ban", ban.id, "target=${request.userId}")
                    call.respond(HttpStatusCode.Created, ban)
                }
                post("/bans/{id}/revoke") {
                    val session = call.requireSession()
                    call.requireRole(UserRole.MODERATOR, UserRole.ADMIN)
                    val id = call.parameters["id"]?.toLongOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest, ApiError("Invalid id"))
                    val ban = userRepository.revokeBan(id, session.userId)
                    if (ban == null) {
                        call.respond(HttpStatusCode.NotFound, ApiError("Ban not found"))
                    } else {
                        auditRepository.log(session.userId, "user.ban.revoke", "user_ban", id, "target=${ban.userId}")
                        call.respond(ban)
                    }
                }
            }
        }
    }
}

private suspend fun ApplicationCall.requireSession(): UserSession {
    val session = principal<JWTPrincipal>()?.toUserSession() ?: throw UnauthorizedException("Unauthenticated")
    val user = ServiceRegistry.userRepository.getUserById(session.userId) ?: throw UnauthorizedException("Unauthenticated")
    if (!user.isActive) throw UnauthorizedException("Account is disabled")
    if (ServiceRegistry.userRepository.getActiveBan(session.userId) != null) throw UnauthorizedException("Account is banned")
    return session
}

class UnauthorizedException(message: String) : RuntimeException(message)
class AccessDeniedException(message: String) : RuntimeException(message)

private suspend fun ApplicationCall.requireRole(vararg allowed: UserRole) {
    val session = requireSession()
    if (session.role !in allowed) throw AccessDeniedException("Insufficient permissions")
}

private suspend fun ApplicationCall.requireOwnershipOrRole(ownerId: Long, vararg allowed: UserRole) {
    val session = requireSession()
    if (session.userId == ownerId) return
    if (session.role !in allowed) throw AccessDeniedException("Insufficient permissions")
}
