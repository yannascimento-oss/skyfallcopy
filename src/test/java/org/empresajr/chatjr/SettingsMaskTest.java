package org.empresajr.chatjr;

import org.empresajr.chatjr.service.SettingsService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SettingsMaskTest {

    @Test
    void keepsOnlyPrefixAndLastFourCharacters() {
        String key = "sk-ant-api03-abcdefghijklmnop4f2a";
        String masked = SettingsService.mask(key);
        assertEquals("sk-ant-…4f2a", masked);
        assertFalse(masked.contains("abcdefghij"));
    }

    @Test
    void shortOrMissingKeysAreFullyHidden() {
        assertEquals("••••", SettingsService.mask("curta"));
        assertEquals("••••", SettingsService.mask(null));
    }
}
