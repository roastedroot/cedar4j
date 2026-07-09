package io.roastedroot.cedar4j;

import java.nio.charset.StandardCharsets;
import run.endive.runtime.Instance;
import run.endive.runtime.Memory;

final class CedarWasm {
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
        Instance instance =
                Instance.builder(CedarModule.load())
                        .withMachineFactory(CedarModule::create)
                        .build();
        return new CedarWasm(new CedarExports_ModuleExports(instance));
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
