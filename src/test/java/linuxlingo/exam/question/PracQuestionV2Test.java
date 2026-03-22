package linuxlingo.exam.question;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import linuxlingo.exam.Checkpoint;
import linuxlingo.shell.vfs.VirtualFileSystem;

/**
 * Tests for v2.0 PracQuestion enhancements:
 * SETUP items, CONTENT_EQUALS checkpoints, PERM checkpoints, NOT_EXISTS.
 */
public class PracQuestionV2Test {

    // ─── Setup items ────────────────────────────────────────────

    @Test
    public void applySetup_mkdir_createsDirectory() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        List<PracQuestion.SetupItem> setupItems = List.of(
                new PracQuestion.SetupItem(PracQuestion.SetupItem.SetupType.MKDIR,
                        "/home/user/project", null)
        );
        PracQuestion q = new PracQuestion("Test", "Explanation",
                Question.Difficulty.EASY,
                List.of(new Checkpoint("/home/user/project", Checkpoint.NodeType.DIR)),
                setupItems);

        q.applySetup(vfs);
        assertTrue(vfs.exists("/home/user/project", "/"));
    }

    @Test
    public void applySetup_file_createsFileWithContent() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        List<PracQuestion.SetupItem> setupItems = List.of(
                new PracQuestion.SetupItem(PracQuestion.SetupItem.SetupType.FILE,
                        "/home/user/data.txt", "hello world")
        );
        PracQuestion q = new PracQuestion("Test", "Explanation",
                Question.Difficulty.EASY, List.of(), setupItems);

        q.applySetup(vfs);
        assertTrue(vfs.exists("/home/user/data.txt", "/"));
        assertEquals("hello world", vfs.readFile("/home/user/data.txt", "/"));
    }

    @Test
    public void applySetup_perm_setsPermission() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        vfs.createFile("/home/user/script.sh", "/");

        List<PracQuestion.SetupItem> setupItems = List.of(
                new PracQuestion.SetupItem(PracQuestion.SetupItem.SetupType.PERM,
                        "/home/user/script.sh", "rwxr-xr-x")
        );
        PracQuestion q = new PracQuestion("Test", "Explanation",
                Question.Difficulty.EASY, List.of(), setupItems);

        q.applySetup(vfs);
        Checkpoint cp = new Checkpoint("/home/user/script.sh",
                Checkpoint.NodeType.PERM, null, "rwxr-xr-x");
        assertTrue(cp.matches(vfs));
    }

    @Test
    public void applySetup_multipleItems_appliedInOrder() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        List<PracQuestion.SetupItem> setupItems = List.of(
                new PracQuestion.SetupItem(PracQuestion.SetupItem.SetupType.MKDIR,
                        "/home/user/project", null),
                new PracQuestion.SetupItem(PracQuestion.SetupItem.SetupType.FILE,
                        "/home/user/project/README.md", "# README")
        );
        PracQuestion q = new PracQuestion("Test", "Explanation",
                Question.Difficulty.MEDIUM, List.of(), setupItems);

        q.applySetup(vfs);
        assertTrue(vfs.exists("/home/user/project", "/"));
        assertTrue(vfs.exists("/home/user/project/README.md", "/"));
        assertEquals("# README", vfs.readFile("/home/user/project/README.md", "/"));
    }

    // ─── hasSetup ───────────────────────────────────────────────

    @Test
    public void hasSetup_withItems_returnsTrue() {
        List<PracQuestion.SetupItem> setupItems = List.of(
                new PracQuestion.SetupItem(PracQuestion.SetupItem.SetupType.MKDIR,
                        "/tmp/test", null)
        );
        PracQuestion q = new PracQuestion("Test", "Exp",
                Question.Difficulty.EASY, List.of(), setupItems);
        assertTrue(q.hasSetup());
    }

    @Test
    public void hasSetup_empty_returnsFalse() {
        PracQuestion q = new PracQuestion("Test", "Exp",
                Question.Difficulty.EASY, List.of());
        assertFalse(q.hasSetup());
    }

    // ─── checkVfs with new checkpoint types ─────────────────────

    @Test
    public void checkVfs_contentEquals_passes() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        vfs.createFile("/home/user/out.txt", "/");
        vfs.writeFile("/home/user/out.txt", "/", "expected", false);

        List<Checkpoint> checkpoints = List.of(
                new Checkpoint("/home/user/out.txt",
                        Checkpoint.NodeType.CONTENT_EQUALS, "expected", null)
        );
        PracQuestion q = new PracQuestion("Test", "Exp",
                Question.Difficulty.HARD, checkpoints);
        assertTrue(q.checkVfs(vfs));
    }

    @Test
    public void checkVfs_contentEquals_fails() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        vfs.createFile("/home/user/out.txt", "/");
        vfs.writeFile("/home/user/out.txt", "/", "wrong", false);

        List<Checkpoint> checkpoints = List.of(
                new Checkpoint("/home/user/out.txt",
                        Checkpoint.NodeType.CONTENT_EQUALS, "expected", null)
        );
        PracQuestion q = new PracQuestion("Test", "Exp",
                Question.Difficulty.HARD, checkpoints);
        assertFalse(q.checkVfs(vfs));
    }

    @Test
    public void checkVfs_notExists_passes() {
        VirtualFileSystem vfs = new VirtualFileSystem();

        List<Checkpoint> checkpoints = List.of(
                new Checkpoint("/home/user/deleted.txt", Checkpoint.NodeType.NOT_EXISTS)
        );
        PracQuestion q = new PracQuestion("Test", "Exp",
                Question.Difficulty.MEDIUM, checkpoints);
        assertTrue(q.checkVfs(vfs));
    }

    @Test
    public void checkVfsNotExists_failsIfPresent() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        vfs.createFile("/home/user/file.txt", "/");

        List<Checkpoint> checkpoints = List.of(
                new Checkpoint("/home/user/file.txt", Checkpoint.NodeType.NOT_EXISTS)
        );
        PracQuestion q = new PracQuestion("Test", "Exp",
                Question.Difficulty.MEDIUM, checkpoints);
        assertFalse(q.checkVfs(vfs));
    }

    @Test
    public void checkVfs_perm_passes() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        // /home default perm is rwxr-xr-x
        List<Checkpoint> checkpoints = List.of(
                new Checkpoint("/home", Checkpoint.NodeType.PERM, null, "rwxr-xr-x")
        );
        PracQuestion q = new PracQuestion("Test", "Exp",
                Question.Difficulty.MEDIUM, checkpoints);
        assertTrue(q.checkVfs(vfs));
    }

    @Test
    public void checkVfs_mixedCheckpoints_allPass() {
        VirtualFileSystem vfs = new VirtualFileSystem();
        vfs.createDirectory("/home/user/project", "/", true);
        vfs.createFile("/home/user/project/app.txt", "/");
        vfs.writeFile("/home/user/project/app.txt", "/", "content", false);

        List<Checkpoint> checkpoints = List.of(
                new Checkpoint("/home/user/project", Checkpoint.NodeType.DIR),
                new Checkpoint("/home/user/project/app.txt", Checkpoint.NodeType.FILE),
                new Checkpoint("/home/user/project/app.txt",
                        Checkpoint.NodeType.CONTENT_EQUALS, "content", null),
                new Checkpoint("/home/user/nonexistent", Checkpoint.NodeType.NOT_EXISTS)
        );
        PracQuestion q = new PracQuestion("Test", "Exp",
                Question.Difficulty.HARD, checkpoints);
        assertTrue(q.checkVfs(vfs));
    }
}
