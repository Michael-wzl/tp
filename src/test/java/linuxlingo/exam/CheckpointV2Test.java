package linuxlingo.exam;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import linuxlingo.shell.vfs.VirtualFileSystem;

/**
 * Tests for v2.0 Checkpoint enhancements:
 * NOT_EXISTS, CONTENT_EQUALS, PERM check types.
 */
public class CheckpointV2Test {

    @Test
    public void checkpoint_dirExists_passes() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        Checkpoint cp = new Checkpoint("/home", Checkpoint.NodeType.DIR);
        assertTrue(cp.matches(vfs));
    }

    @Test
    public void checkpoint_fileExists_passes() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        Checkpoint cp = new Checkpoint("/etc/hostname", Checkpoint.NodeType.FILE);
        assertTrue(cp.matches(vfs));
    }

    @Test
    public void checkpoint_dirMissing_fails() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        Checkpoint cp = new Checkpoint("/nonexistent", Checkpoint.NodeType.DIR);
        assertFalse(cp.matches(vfs));
    }

    // ─── NOT_EXISTS ─────────────────────────────────────────────

    @Test
    public void checkpointNotExists_pathMissing() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        Checkpoint cp = new Checkpoint("/nonexistent", Checkpoint.NodeType.NOT_EXISTS);
        assertTrue(cp.matches(vfs));
    }

    @Test
    public void checkpointNotExists_pathPresent() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        Checkpoint cp = new Checkpoint("/home", Checkpoint.NodeType.NOT_EXISTS);
        assertFalse(cp.matches(vfs));
    }

    // ─── CONTENT_EQUALS ─────────────────────────────────────────

    @Test
    public void checkpointContentEquals_matchingPasses() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        vfs.createFile("/home/user/test.txt", "/");
        vfs.writeFile("/home/user/test.txt", "/", "hello world", false);

        Checkpoint cp = new Checkpoint("/home/user/test.txt",
                Checkpoint.NodeType.CONTENT_EQUALS, "hello world", null);
        assertTrue(cp.matches(vfs));
    }

    @Test
    public void checkpointContentEquals_mismatchFails() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        vfs.createFile("/home/user/test.txt", "/");
        vfs.writeFile("/home/user/test.txt", "/", "hello world", false);

        Checkpoint cp = new Checkpoint("/home/user/test.txt",
                Checkpoint.NodeType.CONTENT_EQUALS, "different content", null);
        assertFalse(cp.matches(vfs));
    }

    @Test
    public void checkpointContentEquals_fileNotFound() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        Checkpoint cp = new Checkpoint("/nonexistent.txt",
                Checkpoint.NodeType.CONTENT_EQUALS, "any", null);
        assertFalse(cp.matches(vfs));
    }

    @Test
    public void checkpointContentEquals_directoryFails() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        Checkpoint cp = new Checkpoint("/home",
                Checkpoint.NodeType.CONTENT_EQUALS, "content", null);
        assertFalse(cp.matches(vfs));
    }

    @Test
    public void checkpointContentEquals_nullExpected() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        vfs.createFile("/home/user/test.txt", "/");
        Checkpoint cp = new Checkpoint("/home/user/test.txt",
                Checkpoint.NodeType.CONTENT_EQUALS, null, null);
        assertFalse(cp.matches(vfs));
    }

    // ─── PERM ───────────────────────────────────────────────────

    @Test
    public void checkpointPerm_matchingPermission() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        vfs.createFile("/home/user/test.txt", "/");
        // Default file permission is "rw-r--r--"
        Checkpoint cp = new Checkpoint("/home/user/test.txt",
                Checkpoint.NodeType.PERM, null, "rw-r--r--");
        assertTrue(cp.matches(vfs));
    }

    @Test
    public void checkpointPerm_wrongPermission() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        vfs.createFile("/home/user/test.txt", "/");
        Checkpoint cp = new Checkpoint("/home/user/test.txt",
                Checkpoint.NodeType.PERM, null, "rwxrwxrwx");
        assertFalse(cp.matches(vfs));
    }

    @Test
    public void checkpointPerm_fileNotFound() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        Checkpoint cp = new Checkpoint("/nonexistent",
                Checkpoint.NodeType.PERM, null, "rwxr-xr-x");
        assertFalse(cp.matches(vfs));
    }

    @Test
    public void checkpointPerm_nullExpected() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        Checkpoint cp = new Checkpoint("/home",
                Checkpoint.NodeType.PERM, null, null);
        assertFalse(cp.matches(vfs));
    }

    @Test
    public void checkpointPerm_directoryPermission() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        // Default directory permission is "rwxr-xr-x"
        Checkpoint cp = new Checkpoint("/home",
                Checkpoint.NodeType.PERM, null, "rwxr-xr-x");
        assertTrue(cp.matches(vfs));
    }
}
