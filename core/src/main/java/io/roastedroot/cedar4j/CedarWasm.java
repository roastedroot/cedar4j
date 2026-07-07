package io.roastedroot.cedar4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import run.endive.runtime.Instance;
import run.endive.runtime.Memory;

class CedarWasm {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CedarExports_ModuleExports exports;
    private final Memory memory;

    private static final int BUF_SIZE = 256 * 1024;
    private int inputBufPtr;
    private int inputBufCap;
    private int outputBufPtr;
    private int outputBufCap;

    private CedarWasm(CedarExports_ModuleExports exports) {
        this.exports = exports;
        this.memory = exports.memory();
        this.inputBufCap = BUF_SIZE;
        this.inputBufPtr = exports.alloc(inputBufCap);
        this.outputBufCap = BUF_SIZE;
        this.outputBufPtr = exports.alloc(outputBufCap);
    }

    static CedarWasm create() {
        return create(Collections.emptyMap());
    }

    static CedarWasm create(Map<String, ExtensionFunction> extensions) {
        AtomicReference<CedarWasm> wasmRef = new AtomicReference<>();

        CedarExports_ModuleImports imports =
                () -> {
                    return (int namePtr, int nameLen, int argsPtr, int argsLen) -> {
                        CedarWasm w = wasmRef.get();
                        if (w == null) {
                            throw new CedarException("host_extension_call: not initialized");
                        }
                        return w.handleExtensionCall(
                                extensions, namePtr, nameLen, argsPtr, argsLen);
                    };
                };

        Instance inst =
                Instance.builder(CedarModule.load())
                        .withMachineFactory(CedarModule::create)
                        .withImportValues(imports.toImportValues())
                        .build();

        CedarWasm wasm = new CedarWasm(new CedarExports_ModuleExports(inst));
        wasmRef.set(wasm);

        if (!extensions.isEmpty()) {
            String initResult = wasm.readWidePtr(wasm.exports().cedarInitHostExtensions());
            if (!"OK".equals(initResult)) {
                throw new CedarException("Host extension init failed: " + initResult);
            }
        }

        return wasm;
    }

    private long handleExtensionCall(
            Map<String, ExtensionFunction> extensions,
            int namePtr,
            int nameLen,
            int argsPtr,
            int argsLen) {
        String funcName = new String(memory.readBytes(namePtr, nameLen), StandardCharsets.UTF_8);
        String argsJson = new String(memory.readBytes(argsPtr, argsLen), StandardCharsets.UTF_8);

        ExtensionFunction extFn = extensions.get(funcName);
        if (extFn == null) {
            throw new CedarException("Unknown extension function: " + funcName);
        }

        try {
            JsonNode args = MAPPER.readTree(argsJson);
            JsonNode result = extFn.invoke(args);
            return writeResultToWasm(MAPPER.writeValueAsString(result));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "extension error";
            try {
                ObjectNode errorNode = MAPPER.createObjectNode();
                errorNode.put("error", msg);
                return writeResultToWasm(MAPPER.writeValueAsString(errorNode));
            } catch (Exception ex) {
                return writeResultToWasm("{\"error\":\"extension error\"}");
            }
        }
    }

    private long writeResultToWasm(String json) {
        byte[] resultBytes = json.getBytes(StandardCharsets.UTF_8);
        int len = resultBytes.length;
        int ptr = exports.alloc(len);
        memory.write(ptr, resultBytes);
        return ((long) len << 32) | (ptr & 0xFFFFFFFFL);
    }

    CedarExports_ModuleExports exports() {
        return exports;
    }

    String authorize(String input) {
        return callBuf(exports::cedarAuthorizeBuf, input);
    }

    String statefulAuthorize(String input) {
        return callBuf(exports::cedarStatefulAuthorizeBuf, input);
    }

    String call(String operation, String input) {
        byte[] opBytes = operation.getBytes(StandardCharsets.UTF_8);
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        int opPtr = allocAndWrite(opBytes);
        int inputPtr = allocAndWrite(inputBytes);
        try {
            int widePtr = exports.cedarCall(opPtr, opBytes.length, inputPtr, inputBytes.length);
            return readWidePtr(widePtr);
        } finally {
            exports.dealloc(opPtr, opBytes.length);
            exports.dealloc(inputPtr, inputBytes.length);
        }
    }

    String callExport(ExportFn fn, String input) {
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        int inputPtr = allocAndWrite(inputBytes);
        try {
            int widePtr = fn.apply(inputPtr, inputBytes.length);
            return readWidePtr(widePtr);
        } finally {
            exports.dealloc(inputPtr, inputBytes.length);
        }
    }

    String callExport(ExportFn2 fn, String arg1, String arg2) {
        byte[] a1 = arg1.getBytes(StandardCharsets.UTF_8);
        byte[] a2 = arg2.getBytes(StandardCharsets.UTF_8);
        int p1 = allocAndWrite(a1);
        int p2 = allocAndWrite(a2);
        try {
            int widePtr = fn.apply(p1, a1.length, p2, a2.length);
            return readWidePtr(widePtr);
        } finally {
            exports.dealloc(p1, a1.length);
            exports.dealloc(p2, a2.length);
        }
    }

    private String callBuf(BufFn fn, String input) {
        byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
        ensureBuf(inputBytes.length);
        memory.write(inputBufPtr, inputBytes);
        int written = fn.apply(inputBufPtr, inputBytes.length, outputBufPtr, outputBufCap);
        if (written < 0) {
            growOutputBuf(-written);
            memory.write(inputBufPtr, inputBytes);
            written = fn.apply(inputBufPtr, inputBytes.length, outputBufPtr, outputBufCap);
            if (written < 0) {
                throw new CedarException(
                        "Output buffer still too small after growth (needed "
                                + (-written)
                                + " bytes)");
            }
        }
        return new String(memory.readBytes(outputBufPtr, written), StandardCharsets.UTF_8);
    }

    private void ensureBuf(int needed) {
        if (needed <= inputBufCap) {
            return;
        }
        exports.dealloc(inputBufPtr, inputBufCap);
        inputBufCap = needed * 2;
        inputBufPtr = exports.alloc(inputBufCap);
    }

    private void growOutputBuf(int needed) {
        exports.dealloc(outputBufPtr, outputBufCap);
        outputBufCap = needed * 2;
        outputBufPtr = exports.alloc(outputBufCap);
    }

    private int allocAndWrite(byte[] data) {
        int ptr = exports.alloc(data.length);
        memory.write(ptr, data);
        return ptr;
    }

    String readWidePtr(int widePtr) {
        int dataPtr = memory.readInt(widePtr);
        int dataLen = memory.readInt(widePtr + 4);
        byte[] bytes = memory.readBytes(dataPtr, dataLen);
        exports.dealloc(dataPtr, dataLen);
        exports.dealloc(widePtr, 8);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    interface ExportFn {
        int apply(int ptr, int len);
    }

    @FunctionalInterface
    interface ExportFn2 {
        int apply(int p1, int l1, int p2, int l2);
    }

    @FunctionalInterface
    private interface BufFn {
        int apply(int inPtr, int inLen, int outPtr, int outCap);
    }
}
