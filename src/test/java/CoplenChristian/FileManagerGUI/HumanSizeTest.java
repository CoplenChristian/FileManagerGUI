package CoplenChristian.FileManagerGUI;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import CoplenChristian.FileManagerGUI.util.HumanSize;

public class HumanSizeTest {
    @Test void bytesUnder1KiB() { assertEquals("512 B", HumanSize.format(512)); }
    @Test void kibBoundary()    { assertTrue(HumanSize.format(1024).contains("KiB")); }
    @Test void gibLooksRight()  { assertTrue(HumanSize.format(2L<<30).contains("GiB")); }
}