package ru.hwaarn.booru.repository

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

import ru.hwaarn.booru.db.AuditLogs
import ru.hwaarn.booru.db.DatabaseFactory.dbQuery
import ru.hwaarn.booru.model.AuditLogDto
import java.time.Instant

class AuditRepository {
    suspend fun log(
        actorId: Long?,
        action: String,
        entityType: String,
        entityId: Long? = null,
        details: String? = null,
    ) = dbQuery {
        AuditLogs.insert {
            it[AuditLogs.actorId] = actorId
            it[AuditLogs.action] = action
            it[AuditLogs.entityType] = entityType
            it[AuditLogs.entityId] = entityId
            it[AuditLogs.details] = details
            it[AuditLogs.createdAt] = Instant.now()
        }
    }

    suspend fun list(limit: Int): List<AuditLogDto> = dbQuery {
        AuditLogs.selectAll()
            .orderBy(AuditLogs.createdAt, SortOrder.DESC)
            .limit(limit)
            .map { row ->
                AuditLogDto(
                    id = row[AuditLogs.id].value,
                    actorId = row[AuditLogs.actorId]?.value,
                    action = row[AuditLogs.action],
                    entityType = row[AuditLogs.entityType],
                    entityId = row[AuditLogs.entityId],
                    details = row[AuditLogs.details],
                    createdAt = row[AuditLogs.createdAt].toString(),
                )
            }
    }
}
