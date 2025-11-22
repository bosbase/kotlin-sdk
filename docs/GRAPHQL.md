# GraphQL queries with the Kotlin SDK

Use `pb.graphql.query()` to call `/api/graphql` with your current auth token. It returns a `JsonObject` with `data`, `errors`, and `extensions` keys.

> Authentication: the GraphQL endpoint is **superuser-only**. Authenticate as a superuser before calling GraphQL, e.g. `pb.collection("_superusers").authWithPassword(email, password)`.

## Single-table query

```kotlin
val query = """
  query ActiveUsers(${'$'}limit: Int!) {
    records(collection: "users", perPage: ${'$'}limit, filter: "status = true") {
      items { id data }
    }
  }
""".trimIndent()

val response = pb.graphql.query(query, variables = mapOf("limit" to 5))
val data = response?.get("data")
```

## Multi-table join via expands

```kotlin
val query = """
  query PostsWithAuthors {
    records(
      collection: "posts",
      expand: ["author", "author.profile"],
      sort: "-created"
    ) {
      items {
        id
        data  // expanded relations live under data.expand
      }
    }
  }
""".trimIndent()

val response = pb.graphql.query(query)
```

## Conditional query with variables

```kotlin
val query = """
  query FilteredOrders(${'$'}minTotal: Float!, ${'$'}state: String!) {
    records(
      collection: "orders",
      filter: "total >= ${'$'}minTotal && status = ${'$'}state",
      sort: "created"
    ) {
      items { id data }
    }
  }
""".trimIndent()

val response = pb.graphql.query(
  query,
  variables = mapOf(
    "minTotal" to 100,
    "state" to "paid",
  ),
)
```

Use the `filter`, `sort`, `page`, `perPage`, and `expand` arguments to mirror REST list behavior while keeping query logic in GraphQL.

## Create a record

```kotlin
val mutation = """
  mutation CreatePost(${'$'}data: JSON!) {
    createRecord(collection: "posts", data: ${'$'}data, expand: ["author"]) {
      id
      data
    }
  }
""".trimIndent()

val payload = mapOf("title" to "Hello", "author" to "USER_ID")
val result = pb.graphql.query(mutation, variables = mapOf("data" to payload))
```

## Update a record

```kotlin
val mutation = """
  mutation UpdatePost(${'$'}id: ID!, ${'$'}data: JSON!) {
    updateRecord(collection: "posts", id: ${'$'}id, data: ${'$'}data) {
      id
      data
    }
  }
""".trimIndent()

pb.graphql.query(
  mutation,
  variables = mapOf(
    "id" to "POST_ID",
    "data" to mapOf("title" to "Updated title"),
  ),
)
```

## Delete a record

```kotlin
val mutation = """
  mutation DeletePost(${'$'}id: ID!) {
    deleteRecord(collection: "posts", id: ${'$'}id)
  }
""".trimIndent()

pb.graphql.query(mutation, variables = mapOf("id" to "POST_ID"))
```
