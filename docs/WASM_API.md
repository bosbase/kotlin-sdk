# WASM API - Kotlin SDK

BosBase uses Wasmtime to run WASI-enabled WebAssembly components.
For details on Wasmtime see https://github.com/bytecodealliance/wasmtime.

Example: compile a simple Rust program to WASM (WASI preview 2):

```
fn main() {
    println!("Hello, world!");
}

rustup target add wasm32-wasip2
rustc hello.rs --target wasm32-wasip2
wasmtime hello.wasm
```

Expected output:

```
Hello, world!
```

Kotlin usage (via the Scripts API):

```kotlin
import com.bosbase.sdk.BosBase

val pb = BosBase(baseUrl)
pb.collection("_superusers").authWithPassword(authEmail, authPassword)
println("[SUCCESS] Authenticated as superuser")

val cmd = pb.scripts.command("./wasmtime hello.wasm")
val output = cmd?.get("output")?.toString().orEmpty()
if (!output.contains("Hello, Bosbase")) {
    throw IllegalStateException("Command output missing expected text: $output")
}
println("[SUCCESS] Command output: $output")
```
