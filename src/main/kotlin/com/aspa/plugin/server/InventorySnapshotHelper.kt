package com.aspa.plugin.server

import com.aspa.plugin.model.PlayerProfile
import com.aspa.plugin.model.PlayerSessionRecord
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import java.io.File
import java.io.InputStream
import java.util.ArrayList
import java.util.Calendar
import java.util.HashMap
import java.util.TimeZone
import java.util.UUID

object InventorySnapshotHelper {

    fun createActiveProfile(activeSession: PlayerSessionRecord): PlayerProfile {
        val profile = PlayerProfile()
        profile.uuid = activeSession.uuid
        profile.username = activeSession.username
        profile.firstLoginMs = activeSession.loginMs
        profile.lastLoginMs = activeSession.loginMs
        profile.totalPlaytimeMs = activeSession.playtimeMs
        profile.averagePing = activeSession.averagePing
        profile.countryCode = activeSession.countryCode

        val sessions = ArrayList<PlayerSessionRecord>()
        sessions.add(activeSession)
        profile.sessions = sessions
        profile.activityPunchcard = computePunchcardForProfile(sessions)
        return profile
    }

    fun enrichWithActiveSession(
        profile: PlayerProfile,
        activeSession: PlayerSessionRecord
    ): PlayerProfile {
        val sessions = ArrayList<PlayerSessionRecord>()
        sessions.add(activeSession)
        profile.sessions?.let { sessions.addAll(it) }
        profile.sessions = sessions

        profile.lastLoginMs = activeSession.loginMs

        var newTotalPlaytime = activeSession.playtimeMs
        if (profile.totalPlaytimeMs > 0) {
            newTotalPlaytime += profile.totalPlaytimeMs
        }
        profile.totalPlaytimeMs = newTotalPlaytime

        var sumPing = 0L
        var count = 0
        for (s in sessions) {
            sumPing += s.averagePing.toLong()
            count++
        }
        if (count > 0) {
            profile.averagePing = (sumPing / count).toInt()
        }

        profile.activityPunchcard = computePunchcardForProfile(sessions)
        return profile
    }

    fun computePunchcardForProfile(sessions: List<PlayerSessionRecord>): Array<IntArray> {
        val punchcard = Array(7) { IntArray(24) }
        for (s in sessions) {
            if (s.loginMs > 0) {
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                cal.timeInMillis = s.loginMs
                val day = cal.get(Calendar.DAY_OF_WEEK) - 1
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                if (day in 0..6 && hour in 0..23) {
                    punchcard[day][hour]++
                }
            }
        }
        return punchcard
    }

    fun buildEmptyInventorySnapshot(
        uuid: String,
        username: String,
        reason: String,
        now: Long
    ): Map<String, Any?> {
        val response = HashMap<String, Any?>()
        response["uuid"] = uuid
        response["username"] = username
        response["online"] = false
        response["fetchedAtMs"] = now
        response["unavailableReason"] = reason
        response["inventory"] = MutableList<Map<String, Any?>?>(36) { null }
        response["armor"] = MutableList<Map<String, Any?>?>(4) { null }
        response["offhand"] = null
        response["enderChest"] = MutableList<Map<String, Any?>?>(27) { null }
        return response
    }

    fun buildInventorySnapshot(player: org.bukkit.entity.Player, now: Long): Map<String, Any?> {
        val response = HashMap<String, Any?>()
        response["uuid"] = player.uniqueId.toString()
        response["username"] = player.name
        response["online"] = true
        response["fetchedAtMs"] = now

        val storage = player.inventory.storageContents
        val inventorySlots = ArrayList<Map<String, Any?>?>()
        for (i in 0 until 36) {
            val item = if (i < storage.size) storage[i] else null
            inventorySlots.add(serializeItem(item, i))
        }
        response["inventory"] = inventorySlots

        val armorSlots = ArrayList<Map<String, Any?>?>()
        armorSlots.add(serializeItem(player.inventory.helmet, 0))
        armorSlots.add(serializeItem(player.inventory.chestplate, 1))
        armorSlots.add(serializeItem(player.inventory.leggings, 2))
        armorSlots.add(serializeItem(player.inventory.boots, 3))
        response["armor"] = armorSlots

        response["offhand"] = serializeItem(player.inventory.itemInOffHand, 0)

        val ender = player.enderChest.contents
        val enderSlots = ArrayList<Map<String, Any?>?>()
        for (i in 0 until 27) {
            val item = if (i < ender.size) ender[i] else null
            enderSlots.add(serializeItem(item, i))
        }
        response["enderChest"] = enderSlots
        return response
    }

    fun buildInventorySnapshotFromOfflineData(
        uuid: UUID?,
        fallbackUuid: String,
        username: String,
        now: Long
    ): Map<String, Any?> {
        if (uuid == null) {
            return buildEmptyInventorySnapshot(fallbackUuid, username, "Player data unavailable", now)
        }

        val dataFile = findPlayerDataFile(uuid)
            ?: return buildEmptyInventorySnapshot(fallbackUuid, username, "Player data file not found", now)

        return try {
            val root = readPlayerDataRoot(dataFile)
                ?: return buildEmptyInventorySnapshot(fallbackUuid, username, "Failed to read player data", now)
            buildInventorySnapshotFromNbt(uuid, username, root, now)
        } catch (ex: Exception) {
            buildEmptyInventorySnapshot(fallbackUuid, username, "Failed to read player data: ${ex.message}", now)
        }
    }

    private fun findPlayerDataFile(uuid: UUID): File? {
        for (world in org.bukkit.Bukkit.getWorlds()) {
            val file = File(File(world.worldFolder, "playerdata"), "${uuid}.dat")
            if (file.exists()) return file
        }
        return null
    }

    private fun readPlayerDataRoot(file: File): Any? {
        return readPlayerDataRootReflective(file)
    }

    private fun buildInventorySnapshotFromNbt(
        uuid: UUID,
        username: String,
        root: Any,
        now: Long
    ): Map<String, Any?> {
        val response = HashMap<String, Any?>()
        response["uuid"] = uuid.toString()
        response["username"] = username
        response["online"] = false
        response["fetchedAtMs"] = now
        response["unavailableReason"] = "Player is offline. Showing last saved inventory snapshot."

        val inventorySlots = MutableList<Map<String, Any?>?>(36) { null }
        val armorSlots = MutableList<Map<String, Any?>?>(4) { null }
        var offhand: Map<String, Any?>? = null
        val enderSlots = MutableList<Map<String, Any?>?>(27) { null }

        val inventoryTag = getListTag(root, "Inventory", 10)
        val invSize = listSize(inventoryTag)
        for (i in 0 until invSize) {
            val item = getListCompound(inventoryTag, i) ?: continue
            val slot = readByte(item, "Slot")?.toInt() ?: continue
            val mapped = serializeItemFromNbt(item, slot)
            when {
                slot in 0..35 -> inventorySlots[slot] = mapped
                slot in 100..103 -> armorSlots[slot - 100] = mapped
                slot == -106 -> offhand = mapped
            }
        }

        val enderTag = getListTag(root, "EnderItems", 10)
        val enderSize = listSize(enderTag)
        for (i in 0 until enderSize) {
            val item = getListCompound(enderTag, i) ?: continue
            val slot = readByte(item, "Slot")?.toInt() ?: continue
            if (slot in 0..26) {
                enderSlots[slot] = serializeItemFromNbt(item, slot)
            }
        }

        response["inventory"] = inventorySlots
        response["armor"] = armorSlots
        response["offhand"] = offhand
        response["enderChest"] = enderSlots
        return response
    }

    private fun serializeItem(item: ItemStack?, slot: Int): Map<String, Any?>? {
        if (item == null || item.type == Material.AIR) return null
        val result = HashMap<String, Any?>()
        result["slot"] = slot
        result["material"] = item.type.name
        result["amount"] = item.amount

        val meta = item.itemMeta
        if (meta != null) {
            if (meta.hasDisplayName()) {
                result["displayName"] = meta.displayName
            }
            if (meta.hasLore()) {
                result["lore"] = meta.lore
            }
            if (meta.hasCustomModelData()) {
                result["customModelData"] = meta.customModelData
            }
            if (meta.hasEnchants()) {
                val enchantments = HashMap<String, Int>()
                for ((ench, level) in meta.enchants) {
                    enchantments[ench.key.key] = level
                }
                result["enchantments"] = enchantments
            }
            if (meta is Damageable) {
                result["durability"] = meta.damage
            }
        }

        return result
    }

    private fun serializeItemFromNbt(item: Any, slot: Int): Map<String, Any?> {
        val result = HashMap<String, Any?>()
        result["slot"] = slot

        val rawId = readString(item, "id") ?: "minecraft:air"
        val materialId = rawId.substringAfter(":", rawId).uppercase()
        result["material"] = materialId

        result["amount"] = readByte(item, "Count")?.toInt() ?: 1

        val damage = readInt(item, "Damage") ?: readShort(item, "Damage")?.toInt()
        if (damage != null) result["durability"] = damage

        return result
    }

    private fun readPlayerDataRootReflective(file: File): Any? {
        val readerClasses = listOf(
            "net.minecraft.nbt.NbtIo",
            "net.minecraft.nbt.NBTCompressedStreamTools"
        )

        for (className in readerClasses) {
            try {
                val nbtIo = Class.forName(className)
                val methods = nbtIo.methods.filter { it.name == "readCompressed" }

                file.inputStream().use { input ->
                    val singleArg = methods.firstOrNull {
                        it.parameterTypes.size == 1 && InputStream::class.java.isAssignableFrom(it.parameterTypes[0])
                    }
                    if (singleArg != null) {
                        return singleArg.invoke(null, input)
                    }

                    val twoArg = methods.firstOrNull {
                        it.parameterTypes.size == 2 && InputStream::class.java.isAssignableFrom(it.parameterTypes[0])
                    }
                    if (twoArg != null) {
                        val accounter = createNbtAccounter(twoArg.parameterTypes[1])
                        if (accounter != null) {
                            return twoArg.invoke(null, input, accounter)
                        }
                    }
                }

                file.inputStream().use { input ->
                    val dataInput = java.io.DataInputStream(input)
                    val dataArg = methods.firstOrNull {
                        it.parameterTypes.size == 1 && java.io.DataInput::class.java.isAssignableFrom(it.parameterTypes[0])
                    }
                    if (dataArg != null) {
                        return dataArg.invoke(null, dataInput)
                    }

                    val dataTwoArg = methods.firstOrNull {
                        it.parameterTypes.size == 2 && java.io.DataInput::class.java.isAssignableFrom(it.parameterTypes[0])
                    }
                    if (dataTwoArg != null) {
                        val accounter = createNbtAccounter(dataTwoArg.parameterTypes[1])
                        if (accounter != null) {
                            return dataTwoArg.invoke(null, dataInput, accounter)
                        }
                    }
                }
            } catch (_: Exception) {
                // Try next class signature.
            }
        }

        return null
    }

    private fun createNbtAccounter(accounterClass: Class<*>): Any? {
        return try {
            val unlimitedField = accounterClass.fields.firstOrNull {
                java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                    it.type == accounterClass &&
                    it.name.lowercase().contains("unlimited")
            }
            if (unlimitedField != null) {
                return unlimitedField.get(null)
            }

            val unlimited = accounterClass.methods.firstOrNull {
                it.name == "unlimited" && it.parameterTypes.isEmpty()
            }
            if (unlimited != null) {
                return unlimited.invoke(null)
            }

            val ofMethod = accounterClass.methods.firstOrNull {
                (it.name == "of" || it.name == "create") &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Long::class.javaPrimitiveType
            }
            if (ofMethod != null) {
                return ofMethod.invoke(null, Long.MAX_VALUE)
            }

            val ctor = accounterClass.getDeclaredConstructor(Long::class.javaPrimitiveType)
            ctor.isAccessible = true
            ctor.newInstance(Long.MAX_VALUE)
        } catch (_: Exception) {
            null
        }
    }

    private fun getListTag(compound: Any, key: String, elementType: Int): Any? {
        return try {
            val method = compound.javaClass.getMethod("getList", String::class.java, Int::class.javaPrimitiveType)
            method.invoke(compound, key, elementType)
        } catch (ex: Exception) {
            null
        }
    }

    private fun listSize(list: Any?): Int {
        if (list == null) return 0
        return try {
            val method = list.javaClass.getMethod("size")
            (method.invoke(list) as? Int) ?: 0
        } catch (ex: Exception) {
            0
        }
    }

    private fun getListCompound(list: Any?, index: Int): Any? {
        if (list == null) return null
        return try {
            val method = list.javaClass.getMethod("getCompound", Int::class.javaPrimitiveType)
            method.invoke(list, index)
        } catch (_: Exception) {
            try {
                val method = list.javaClass.getMethod("get", Int::class.javaPrimitiveType)
                method.invoke(list, index)
            } catch (ex: Exception) {
                null
            }
        }
    }

    private fun readString(compound: Any, key: String): String? {
        return try {
            val method = compound.javaClass.getMethod("getString", String::class.java)
            method.invoke(compound, key) as? String
        } catch (ex: Exception) {
            null
        }
    }

    private fun readByte(compound: Any, key: String): Byte? {
        return try {
            val method = compound.javaClass.getMethod("getByte", String::class.java)
            method.invoke(compound, key) as? Byte
        } catch (ex: Exception) {
            null
        }
    }

    private fun readShort(compound: Any, key: String): Short? {
        return try {
            val method = compound.javaClass.getMethod("getShort", String::class.java)
            method.invoke(compound, key) as? Short
        } catch (ex: Exception) {
            null
        }
    }

    private fun readInt(compound: Any, key: String): Int? {
        return try {
            val method = compound.javaClass.getMethod("getInt", String::class.java)
            method.invoke(compound, key) as? Int
        } catch (ex: Exception) {
            null
        }
    }
}
