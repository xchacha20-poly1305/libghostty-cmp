@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.xchacha20_poly1305.libghostty

import kotlin.js.JsAny
import kotlin.js.Promise
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.khronos.webgl.toInt8Array
import kotlinx.coroutines.await

internal actual suspend fun initializeGhosttyWasm(bytes: ByteArray) {
    initializeWasm(bytes.toInt8Array()).await()
}

internal actual fun callWasm(op: Int, handle: Int, a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, input: ByteArray): WasmResult {
    val result = invokeWasm(op, handle, a, b, c, d, e, f, input.toInt8Array())
    return WasmResult(result.value, result.bytes.toByteArray())
}

@JsFun("""async (bytes) => {
    if (!globalThis.__libghosttyWasmLoading) {
        globalThis.__libghosttyWasmLoading = WebAssembly.instantiate(bytes, {}).then(({instance}) => {
            if (instance.exports.ghostty_abi_version() !== 1) throw new Error('Ghostty Wasm ABI mismatch');
            globalThis.__libghosttyWasm = instance.exports;
        }).catch(error => { delete globalThis.__libghosttyWasmLoading; throw error; });
    }
    await globalThis.__libghosttyWasmLoading;
}""")
private external fun initializeWasm(bytes: JsAny): Promise<JsAny?>

private external interface JsWasmResult : JsAny {
    val value: Int
    val bytes: Int8Array
}

@JsFun("""(op, handle, a, b, c, d, e, f, input) => {
    const wasm = globalThis.__libghosttyWasm;
    if (!wasm) throw new Error('Call GhosttyWebAssembly.load() before creating a terminal');
    const pointer = input.length ? wasm.ghostty_alloc(input.length) : 0;
    if (input.length && !pointer) throw new Error('Ghostty Wasm allocation failed');
    try {
        if (input.length) new Uint8Array(wasm.memory.buffer, pointer, input.length).set(input);
        const value = wasm.ghostty_call(op, handle, a, b, c, d, e, f, pointer, input.length);
        const length = wasm.ghostty_result_len();
        const bytes = new Int8Array(wasm.memory.buffer, wasm.ghostty_result_ptr(), length).slice();
        return {value, bytes};
    } finally {
        if (input.length) wasm.ghostty_free(pointer, input.length);
    }
}""")
private external fun invokeWasm(op: Int, handle: Int, a: Int, b: Int, c: Int, d: Int, e: Int, f: Int, input: JsAny): JsWasmResult
