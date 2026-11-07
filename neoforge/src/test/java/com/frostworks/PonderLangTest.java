package com.frostworks;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

/** Every Ponder text has its French and its English line (frostworks.ponder.*). */
class PonderLangTest {

    static Set<String> ponderKeys(String lang) throws IOException {
        Path file = Path.of(System.getProperty("frostworks.resourcesDir"))
                .resolve("assets/frostworks/lang/" + lang + ".json");
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Set<String> keys = new TreeSet<>();
            for (String key : JsonParser.parseReader(r).getAsJsonObject().keySet())
                if (key.startsWith("frostworks.ponder."))
                    keys.add(key);
            return keys;
        }
    }

    @Test
    void frenchAndEnglishHaveTheSamePonderKeys() throws IOException {
        Set<String> fr = ponderKeys("fr_fr"), en = ponderKeys("en_us");
        Set<String> onlyFr = new TreeSet<>(fr), onlyEn = new TreeSet<>(en);
        onlyFr.removeAll(en);
        onlyEn.removeAll(fr);
        assertTrue(!fr.isEmpty(), "no Ponder key");
        assertTrue(onlyFr.isEmpty(), "French only: " + onlyFr);
        assertTrue(onlyEn.isEmpty(), "English only: " + onlyEn);
        System.out.println("PONDER LANG " + fr.size() + " keys in both languages");
    }
}
