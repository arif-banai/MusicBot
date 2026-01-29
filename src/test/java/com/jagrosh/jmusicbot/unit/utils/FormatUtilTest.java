package com.jagrosh.jmusicbot.unit.utils;

import com.jagrosh.jmusicbot.utils.FormatUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FormatUtilTest {

    @Test
    public void testFormatUsername() {
        assertEquals("User#1234", FormatUtil.formatUsername("User", "1234"));
        assertEquals("User", FormatUtil.formatUsername("User", "0000"));
        assertEquals("User", FormatUtil.formatUsername("User", null));
    }

    @Test
    public void testVolumeIcon() {
        assertEquals("\uD83D\uDD07", FormatUtil.volumeIcon(0));
        assertEquals("\uD83D\uDD08", FormatUtil.volumeIcon(20));
        assertEquals("\uD83D\uDD09", FormatUtil.volumeIcon(50));
        assertEquals("\uD83D\uDD0A", FormatUtil.volumeIcon(80));
    }

    @Test
    public void testFilter() {
        assertEquals("safe", FormatUtil.filter("safe"));
        assertEquals("@\u0435veryone", FormatUtil.filter("@everyone"));
        assertEquals("@h\u0435re", FormatUtil.filter("@here"));
    }
}
