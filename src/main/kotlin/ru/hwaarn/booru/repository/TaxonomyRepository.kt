package ru.hwaarn.booru.repository

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*

import ru.hwaarn.booru.db.PostTags
import ru.hwaarn.booru.db.TagAliases
import ru.hwaarn.booru.db.TagImplications
import ru.hwaarn.booru.db.Tags
import ru.hwaarn.booru.db.DatabaseFactory.dbQuery
import ru.hwaarn.booru.model.CreateAliasRequest
import ru.hwaarn.booru.model.CreateImplicationRequest
import ru.hwaarn.booru.model.TagCategory
import ru.hwaarn.booru.model.TagDto
import java.time.Instant

class TaxonomyRepository {
    suspend fun listTags(search: String?, category: TagCategory?): List<TagDto> = dbQuery {
        var op: Op<Boolean> = Op.TRUE
        if (!search.isNullOrBlank()) {
            op = op and (Tags.name like "%${normalizeTag(search)}%")
        }
        if (category != null) {
            op = op and (Tags.category eq category.name)
        }
        Tags.selectAll().where { op }
            .orderBy(Tags.postCount, SortOrder.DESC)
            .limit(100)
            .map { it.toTagDto() }
    }

    suspend fun createAlias(request: CreateAliasRequest, creatorId: Long): TagDto = dbQuery {
        val consequent = getOrCreateTagInternal(normalizeTag(request.consequentName), TagCategory.GENERAL)
        TagAliases.insertIgnore {
            it[TagAliases.antecedentName] = normalizeTag(request.antecedentName)
            it[TagAliases.consequentTagId] = consequent.first
            it[TagAliases.creatorId] = creatorId
            it[TagAliases.createdAt] = Instant.now()
        }
        consequent.second
    }

    suspend fun createImplication(request: CreateImplicationRequest, creatorId: Long) = dbQuery {
        val antecedent = getOrCreateTagInternal(normalizeTag(request.antecedentName), TagCategory.GENERAL)
        val consequent = getOrCreateTagInternal(normalizeTag(request.consequentName), TagCategory.GENERAL)
        TagImplications.insertIgnore {
            it[TagImplications.antecedentTagId] = antecedent.first
            it[TagImplications.consequentTagId] = consequent.first
            it[TagImplications.creatorId] = creatorId
            it[TagImplications.createdAt] = Instant.now()
        }
    }

    suspend fun ensureTags(rawNames: List<String>): List<TagDto> = dbQuery {
        normalizeAndExpand(rawNames).map { getOrCreateTagInternal(it, detectCategory(it)).second }
    }

    suspend fun normalizeForSearch(rawNames: List<String>): List<String> = dbQuery {
        normalizeAndExpand(rawNames)
    }

    suspend fun findTagIdByName(name: String): Long? = dbQuery {
        Tags.selectAll().where { Tags.name eq normalizeTag(name) }.singleOrNull()?.get(Tags.id)?.value
    }

    suspend fun findTagNameById(id: Long): String? = dbQuery {
        Tags.selectAll().where { Tags.id eq id }.singleOrNull()?.get(Tags.name)
    }

    suspend fun getTagNamesByIds(ids: List<Long>): Map<Long, String> = dbQuery {
        if (ids.isEmpty()) emptyMap()
        else Tags.selectAll().where { Tags.id inList ids }.associate { it[Tags.id].value to it[Tags.name] }
    }

    suspend fun getTagIdsByNames(names: List<String>): Map<String, Long> = dbQuery {
        if (names.isEmpty()) emptyMap()
        else Tags.selectAll().where { Tags.name inList names.map(::normalizeTag) }.associate { it[Tags.name] to it[Tags.id].value }
    }

    suspend fun recountPosts(tagIds: Iterable<Long>) = dbQuery {
        tagIds.distinct().forEach { tagId ->
            val count = PostTags.selectAll().where { PostTags.tagId eq tagId }.count().toInt()
            Tags.update({ Tags.id eq tagId }) {
                it[Tags.postCount] = count
                it[Tags.updatedAt] = Instant.now()
            }
        }
    }

    private fun normalizeAndExpand(rawNames: List<String>): List<String> {
        val base = rawNames.map(::normalizeTag).filter { it.isNotBlank() }.toMutableSet()
        if (base.isEmpty()) return emptyList()

        val aliases = TagAliases.selectAll().where { TagAliases.antecedentName inList base.toList() }
            .associate { it[TagAliases.antecedentName] to it[TagAliases.consequentTagId].value }
        val aliasTargets = if (aliases.isNotEmpty()) {
            Tags.selectAll().where { Tags.id inList aliases.values.toList() }.associate { row -> row[Tags.id].value to row[Tags.name] }
        } else emptyMap()

        val normalized = base.map { aliases[it]?.let(aliasTargets::get) ?: it }.toMutableSet()
        var added = true
        while (added) {
            added = false
            val ids = if (normalized.isEmpty()) emptyList() else {
                Tags.selectAll().where { Tags.name inList normalized.toList() }.map { it[Tags.id].value }
            }
            if (ids.isEmpty()) break
            val impliedIds = TagImplications.selectAll().where { TagImplications.antecedentTagId inList ids }
                .map { it[TagImplications.consequentTagId].value }
            if (impliedIds.isNotEmpty()) {
                val impliedNames = Tags.selectAll().where { Tags.id inList impliedIds }.map { it[Tags.name] }
                impliedNames.forEach { if (normalized.add(it)) added = true }
            }
        }
        return normalized.sorted()
    }

    private fun getOrCreateTagInternal(name: String, category: TagCategory): Pair<Long, TagDto> {
        val existing = Tags.selectAll().where { Tags.name eq name }.singleOrNull()
        if (existing != null) return existing[Tags.id].value to existing.toTagDto()

        val now = Instant.now()
        val id = Tags.insertAndGetId {
            it[Tags.name] = name
            it[Tags.category] = category.name
            it[Tags.postCount] = 0
            it[Tags.isLocked] = false
            it[Tags.isDeprecated] = false
            it[Tags.createdAt] = now
            it[Tags.updatedAt] = now
        }.value
        val row = Tags.selectAll().where { Tags.id eq id }.single()
        return id to row.toTagDto()
    }

    private fun detectCategory(tag: String): TagCategory = when {
        tag.startsWith("artist_") -> TagCategory.ARTIST
        tag.startsWith("character_") -> TagCategory.CHARACTER
        tag.startsWith("copyright_") -> TagCategory.COPYRIGHT
        tag.startsWith("meta_") || tag.startsWith("rating:") -> TagCategory.META
        else -> TagCategory.GENERAL
    }

    private fun normalizeTag(value: String): String = value.trim().lowercase().replace(' ', '_')

    private fun ResultRow.toTagDto(): TagDto = TagDto(
        id = this[Tags.id].value,
        name = this[Tags.name],
        category = TagCategory.valueOf(this[Tags.category]),
        postCount = this[Tags.postCount],
        isLocked = this[Tags.isLocked],
        isDeprecated = this[Tags.isDeprecated],
    )
}
