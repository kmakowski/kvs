package kmakowski.kvs

import org.slf4j.LoggerFactory

class CachingKvs(
    private val cache: KeyValueStore,
    private val target: KeyValueStore,
    private val prefix: String? = null
) : KeyValueStore {
    private val log = LoggerFactory.getLogger(CachingKvs::class.java)
    
    override suspend fun putObjectAsJson(key: String, any: Any) {
        val prefixedKey = getPrefixedKey(key)
        target.putObjectAsJson(prefixedKey, any)
        try {
            cache.putObjectAsJson(prefixedKey, any)
        } catch (e: Exception) {
            log.error("Could not cache object for key $prefixedKey", e)
        }
    }

    private fun getPrefixedKey(key: String): String = if (prefix != null) {
        "$prefix/$key"
    } else {
        key
    }

    override suspend fun putStringValue(key: String, value: String) {
        val prefixedKey = getPrefixedKey(key)

        target.putStringValue(prefixedKey, value)
        try {
            cache.putStringValue(prefixedKey, value)
        } catch (e: Exception) {
            log.error("Could not cache object for key $prefixedKey", e)
        }
    }

    override suspend fun <T> getObject(key: String, clazz: Class<T>): T? {
        val prefixedKey = getPrefixedKey(key)

        val result: T? = try {
            cache.getObject(prefixedKey, clazz)
        } catch (e: Exception) {
            log.error("Could not get cache object for key $prefixedKey", e)
            null
        }
        if (result == null) {
            val targetResult = target.getObject(prefixedKey, clazz)
            if (targetResult != null) {
                try {
                    cache.putObjectAsJson(prefixedKey, targetResult)
                } catch (e: Exception) {
                    log.error("Could not cache object for key $prefixedKey", e)
                }
            }
            return targetResult
        }
        
        return result
    }

    override suspend fun getObjectAsString(key: String): String? {
        val prefixedKey = getPrefixedKey(key)
        val result : String? = try {
            cache.getObjectAsString(prefixedKey)
        } catch (e: Exception) {
            log.error("Could not get cache object for key $prefixedKey", e)
            null    
        }
        if (result == null) {
            val targetResult = target.getObjectAsString(prefixedKey)
            if (targetResult != null) {
                try {
                    cache.putStringValue(prefixedKey, targetResult)
                } catch (e: Exception) {
                    log.error("Could not cache object for key $prefixedKey", e)
                }
            }
            return targetResult
        }
        
        return result
    }

    override suspend fun listObjects(
        listPrefix: String,
        startAfterKey: String?,
        maxItems: Int
    ): List<KeyValue> {
        TODO("Not yet implemented")
    }

    override suspend fun listKeys(listPrefix: String): List<String> {
        TODO("Not yet implemented")
    }

    override suspend fun deleteObject(key: String) {
        val prefixedKey = getPrefixedKey(key)
        target.deleteObject(prefixedKey)
        try {
            cache.deleteObject(prefixedKey)
        } catch (e: Exception) {
            log.error("Could not delete cache object for key $prefixedKey", e)
        }
    }

    override suspend fun addListItem(key: String, listName: String, item: String) {
        val prefixedKey = getPrefixedKey(key)
        target.addListItem(prefixedKey, listName, item)
        try {
            cache.addListItem(prefixedKey, listName, item)
        } catch (e: Exception) {
            log.error("Could not add list item for key $prefixedKey listName $listName", e)
        }
    }

    override suspend fun getListItems(key: String, listName: String): List<String> {
        val prefixedKey = getPrefixedKey(key)
        val items = try {
            cache.getListItems(prefixedKey, listName)
        } catch (e: Exception) {
            log.error("Could not get cache list items for key $prefixedKey, listName $listName", e)
            emptyList()
        }
        if (items.isEmpty()) {
            val targetItems = target.getListItems(prefixedKey, listName)
            if (targetItems.isNotEmpty()) {
                targetItems.forEach { 
                    try {
                        cache.addListItem(prefixedKey, listName, it)
                    } catch (e: Exception) {
                        log.error("Could not cache list item for key $prefixedKey, listName $listName", e)
                    }
                }
            }
            return targetItems
        }
        return items
    }

    override suspend fun deleteListItem(key: String, listName: String, item: String) {
        val prefixedKey = getPrefixedKey(key)
        target.deleteListItem(prefixedKey, listName, item)
        cache.deleteListItem(prefixedKey, listName, item)
    }
}