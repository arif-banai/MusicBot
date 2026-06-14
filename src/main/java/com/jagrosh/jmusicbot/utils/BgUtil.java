package com.jagrosh.jmusicbot.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * JNA binding for <a href="https://github.com/jim60105/bgutil-ytdlp-pot-provider-rs/blob/master/docs/ffi-guide.md">BgUtils POT Provider</a>.
 */
public interface BgUtil extends Library {
    Pointer ffi_generate(String content_binding, String proxy, boolean bypass_cache, String source_address, boolean disable_tls);
    void ffi_free_string(Pointer ptr);

    class Factory {
        static BgUtil INSTANCE;
        static Boolean isPresent;

        private static boolean tryInstantiate(String path) {
            try {
                if (Files.exists(Paths.get(path))) {
                    INSTANCE = Native.load(path, BgUtil.class);
                    isPresent = true;
                    return false;
                }
            } catch (Exception _) {}
            return true;
        }

        public static BgUtil getInstance() {
            if (INSTANCE == null && isPresent == null) {
                isPresent = false;

                String libbase = "./libbgutil_ytdlp_pot_provider";
                String libext = "-linux-.so";
                String osname = System.getProperty("os.name").toLowerCase();
                if (osname.contains("win")) {
                    libbase = "./bgutil_ytdlp_pot_provider";
                    libext = "-windows-.dll";
                } else if (osname.contains("mac")) {
                    libext = "-macos-.dylib";
                }

                String libpath = libbase + libext.replace(".", System.getProperty("os.arch") + ".");
                if (tryInstantiate(libpath)) {
                    // x86_64 fallback
                    libpath = libbase + libext.replace(".", "x86_64.");
                    if (tryInstantiate(libpath)) {
                        // generic fallback
                        libpath = libbase + libext.substring(libext.indexOf("."));
                        tryInstantiate(libpath);
                    }
                }
            }
            return INSTANCE;
        }
    }

    static boolean isPresent() {
        Factory.getInstance();
        return Boolean.TRUE.equals(Factory.isPresent);
    }

    static String generateJson(String content_binder) {
        Pointer ptr = Factory.getInstance().ffi_generate(content_binder, null, false, null, false);
        String result = ptr.getString(0);
        Factory.getInstance().ffi_free_string(ptr);
        return result;
    }

    static PotResult parsePOT(String json) {
        PotResult result = PotResult.NULL;
        try {
            JsonNode node = PotResult.mapper.readTree(json);
            if (!node.isEmpty()) {
                result = new PotResult(node.get("poToken").asText(), node.get("contentBinding").asText(), node.get("expiresAt").asText());
            }
        } catch (JsonProcessingException _) {}
        return result;
    }

    record PotResult(String poToken, String contentBinding, String expiresAt) {
        public static final PotResult NULL = new PotResult(null, null, "1970-01-01T00:00:00.000000000Z");
        public static final ObjectMapper mapper = new ObjectMapper();
    }
}
