package CoplenChristian.FileManagerGUI;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.List;
import CoplenChristian.FileManagerGUI.scan.TopKFinder;
import CoplenChristian.FileManagerGUI.scan.FolderScanner.Item;

public class TopKFinderTest {
    @Test void ancestorLogic() {
        Path root = Path.of("C:\\");
        Item a = new Item("Users", root.resolve("Users"), true, 100, false);
        Item b = new Item("AppData", root.resolve("Users\\me\\AppData"), true, 90, false);
        assertTrue(TopKFinder.isAncestor(a.path, b.path));
        assertFalse(TopKFinder.isAncestor(b.path, a.path));
    }
}