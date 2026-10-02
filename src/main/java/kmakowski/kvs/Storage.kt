package kmakowski.kvs

import org.slf4j.LoggerFactory
import java.time.Instant

data class KeyValue(val key: String, val value: String, val timestamp: Instant)

private val log = LoggerFactory.getLogger("kmakowski.kvs.KeyValueStore")
interface KeyValueStore {
    suspend fun putObjectAsJson(key: String, any: Any)
    suspend fun putStringValue(key: String, value: String)
    suspend fun <T> getObject(key: String, clazz: Class<T>): T?
    suspend fun getObjectAsString(key: String): String?
    suspend fun getObjectAsStringOrThrow(key: String): String {
        return getObjectAsString(key) ?: throw IllegalArgumentException("Not found: $key")
    }
    suspend fun listObjects(
        listPrefix: String = "",
        startAfterKey: String? = null,
        maxItems: Int = 1000
    ): List<KeyValue>

    suspend fun listKeys(listPrefix: String): List<String>
    suspend fun deleteObject(key: String)

    /**
     * item should not contain slashes or commas
     */
    suspend fun addListItem(key: String, listName: String, item: String) {
        val newListItemsStr = currentListItemsStr(key, listName)?.plus(",$item") ?: item
        putStringValue(listItemsPath(key, listName), newListItemsStr)
    }
    
    suspend fun deleteListItem(key: String, listName: String, item: String) {
        val currentListItemsStr = currentListItemsStr(key, listName)
        val newListItemsStr = currentListItemsStr
            ?.replace("$key,", "")?.replace(",$item", "")?.replace(item, "") ?: ""

        putStringValue(listItemsPath(key, listName), newListItemsStr)
    }
    
    suspend fun getListItems(key: String, listName: String): List<String> {
        return currentListItemsStr(key, listName)?.trim()?.split(",")?.filter { it.isNotBlank() }?.toList() ?: emptyList()
    }
    
    private suspend fun currentListItemsStr(key: String, listName: String) =
        getObjectAsString(listItemsPath(key, listName))

    private fun listItemsPath(key: String, listName: String) = "$key/$listName"
    
    suspend fun exists(key: String): Boolean {
        return getObjectAsString(key) != null
    }
}

suspend inline fun <reified T> KeyValueStore.getOrCompute(key: String, block: () -> T?): T? {
    val currentValue = getObject(key, T::class.java)
    if (currentValue != null) return currentValue
    try {
        val newValue = block.invoke()
        if (newValue != null) {
            putObjectAsJson(key, newValue as Any)
        }
        return newValue
    } catch (e: Exception) {
        throw RuntimeException("Could not compute value for key $key", e)
    }
}

suspend inline fun <reified T> KeyValueStore.get(key: String): T? =
    getObject(key, T::class.java)


class PrefixedKeyValueStore(
    private val prefix: String, private val store: KeyValueStore
) : KeyValueStore {
    override suspend fun putObjectAsJson(key: String, any: Any) =
        store.putObjectAsJson(prefixedKey(key), any)

    override suspend fun putStringValue(key: String, value: String) =
        store.putStringValue(prefixedKey(key), value)

    override suspend fun <T> getObject(key: String, clazz: Class<T>): T? =
        store.getObject(prefixedKey(key), clazz)

    override suspend fun getObjectAsString(key: String): String? =
        store.getObjectAsString(prefixedKey(key))

    override suspend fun exists(key: String): Boolean =
        store.exists(prefixedKey(key))

    override suspend fun listObjects(
        listPrefix: String, startAfterKey: String?, maxItems: Int
    ): List<KeyValue> =
        store.listObjects(prefixedKey(listPrefix), startAfterKey?.let { prefixedKey(it) }, maxItems)
            .map { KeyValue(it.key.replaceFirst("$prefix/", ""), it.value, it.timestamp) }

    override suspend fun listKeys(listPrefix: String): List<String> =
        store.listKeys(prefixedKey(listPrefix)).map { it.replaceFirst("$prefix/", "") }

    override suspend fun deleteObject(key: String) = store.deleteObject(prefixedKey(key))

    override suspend fun addListItem(key: String, listName: String, item: String) =
        store.addListItem(prefixedKey(key), listName, item)

    override suspend fun deleteListItem(key: String, listName: String, item: String) =
        store.deleteListItem(prefixedKey(key), listName, item)

    override suspend fun getListItems(key: String, listName: String): List<String> =
        store.getListItems(prefixedKey(key), listName)
    
    private fun prefixedKey(key: String) = "$prefix/$key"
}