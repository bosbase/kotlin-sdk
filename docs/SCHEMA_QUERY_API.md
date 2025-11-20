# Schema Query API - Kotlin SDK Documentation

## Overview

The Schema Query API provides lightweight interfaces to retrieve collection field information without fetching full collection definitions. This is particularly useful for AI systems that need to understand the structure of collections and the overall system architecture.

**Key Features:**
- Get schema for a single collection by name or ID
- Get schemas for all collections in the system
- Lightweight response with only essential field information
- Support for all collection types (base, auth, view)
- Fast and efficient queries

**Backend Endpoints:**
- `GET /api/collections/{collection}/schema` - Get single collection schema
- `GET /api/collections/schemas` - Get all collection schemas

**Note**: All Schema Query API operations require superuser authentication.

> 📖 **Reference**: For detailed schema query concepts, see the [JavaScript SDK Schema Query documentation](../js-sdk/docs/SCHEMA_QUERY_API.md).

## Authentication

All Schema Query API operations require superuser authentication:

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase("http://127.0.0.1:8090")

// Authenticate as superuser
pb.admins.authWithPassword("admin@example.com", "password")
```

## Get Single Collection Schema

Retrieves the schema (fields and types) for a single collection by name or ID.

### Basic Usage

```kotlin
// Get schema for a collection by name
val schema = pb.collections.getSchema("demo1")

val name = schema?.get("name")?.jsonPrimitive?.contentOrNull
val type = schema?.get("type")?.jsonPrimitive?.contentOrNull
val fields = schema?.get("fields")?.jsonArray

println("Collection: $name, Type: $type")

// Iterate through fields
fields?.forEach { field ->
    val fieldObj = field.jsonObject
    val fieldName = fieldObj["name"]?.jsonPrimitive?.contentOrNull
    val fieldType = fieldObj["type"]?.jsonPrimitive?.contentOrNull
    val required = fieldObj["required"]?.jsonPrimitive?.booleanOrNull ?: false
    println("  $fieldName: $fieldType${if (required) " (required)" else ""}")
}
```

### Using Collection ID

```kotlin
// Get schema for a collection by ID
val schema = pb.collections.getSchema("_pbc_base_123")

val name = schema?.get("name")?.jsonPrimitive?.contentOrNull
println("Collection: $name")
```

### Handling Different Collection Types

```kotlin
// Base collection schema
val baseSchema = pb.collections.getSchema("posts")
val type = baseSchema?.get("type")?.jsonPrimitive?.contentOrNull
// type = "base"

// Auth collection schema
val authSchema = pb.collections.getSchema("users")
// type = "auth"

// View collection schema
val viewSchema = pb.collections.getSchema("post_stats")
// type = "view"
```

## Get All Collection Schemas

Retrieves schemas for all collections in the system.

### Basic Usage

```kotlin
// Get all collection schemas
val schemas = pb.collections.getAllSchemas()

schemas?.let { schemaObj ->
    schemaObj.forEach { (collectionName, schema) ->
        val schemaData = schema.jsonObject
        val name = schemaData["name"]?.jsonPrimitive?.contentOrNull
        val type = schemaData["type"]?.jsonPrimitive?.contentOrNull
        val fields = schemaData["fields"]?.jsonArray
        
        println("Collection: $name ($type)")
        println("  Fields: ${fields?.size ?: 0}")
    }
}
```

### Filter by Collection Type

```kotlin
val schemas = pb.collections.getAllSchemas()

// Filter base collections
val baseCollections = schemas?.let { schemaObj ->
    schemaObj.filter { (_, schema) ->
        schema.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "base"
    }
} ?: emptyMap()

// Filter auth collections
val authCollections = schemas?.let { schemaObj ->
    schemaObj.filter { (_, schema) ->
        schema.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "auth"
    }
} ?: emptyMap()
```

## Examples

### Collection Schema Explorer

```kotlin
fun exploreSchemas(pb: BosBase) {
    pb.admins.authWithPassword("admin@example.com", "password")
    
    val schemas = pb.collections.getAllSchemas()
    
    schemas?.let { schemaObj ->
        schemaObj.forEach { (collectionName, schema) ->
            val schemaData = schema.jsonObject
            val name = schemaData["name"]?.jsonPrimitive?.contentOrNull ?: collectionName
            val type = schemaData["type"]?.jsonPrimitive?.contentOrNull ?: ""
            val fields = schemaData["fields"]?.jsonArray ?: emptyList()
            
            println("Collection: $name ($type)")
            fields.forEach { field ->
                val fieldObj = field.jsonObject
                val fieldName = fieldObj["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val fieldType = fieldObj["type"]?.jsonPrimitive?.contentOrNull ?: ""
                val required = fieldObj["required"]?.jsonPrimitive?.booleanOrNull ?: false
                val system = fieldObj["system"]?.jsonPrimitive?.booleanOrNull ?: false
                
                println("  - $fieldName: $fieldType${if (required) " (required)" else ""}${if (system) " [system]" else ""}")
            }
            println()
        }
    }
}
```

### Find Collections with Specific Field

```kotlin
fun findCollectionsWithField(pb: BosBase, fieldName: String): List<String> {
    pb.admins.authWithPassword("admin@example.com", "password")
    
    val schemas = pb.collections.getSchemas()
    
    return schemas.filter { schema ->
        val fields = schema["fields"]?.jsonArray ?: emptyList()
        fields.any { field ->
            field.jsonObject["name"]?.jsonPrimitive?.contentOrNull == fieldName
        }
    }.mapNotNull { schema ->
        schema["name"]?.jsonPrimitive?.contentOrNull
    }
}

// Usage
val collectionsWithEmail = findCollectionsWithField(pb, "email")
println("Collections with 'email' field: $collectionsWithEmail")
```

