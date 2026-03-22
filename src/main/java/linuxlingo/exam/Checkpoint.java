package linuxlingo.exam;

import linuxlingo.shell.vfs.FileNode;
import linuxlingo.shell.vfs.Permission;
import linuxlingo.shell.vfs.RegularFile;
import linuxlingo.shell.vfs.VfsException;
import linuxlingo.shell.vfs.VirtualFileSystem;

/**
 * A checkpoint for PRAC questions: expected path + node type in VFS.
 *
 * <p>This is <b>infrastructure</b> — fully implemented.
 * Used by {@code PracQuestion} to verify VFS state after a practical exercise.</p>
 *
 * <p>Example: a checkpoint {@code ("/home/project", DIR)} passes if
 * the VFS contains a directory at that path.</p>
 *
 * <h3>v2.0 Enhancements</h3>
 * <ul>
 *   <li><b>CONTENT_EQUALS</b> — checks that a file has expected content.</li>
 *   <li><b>PERM</b> — checks that a file/dir has expected permission string (e.g. "rwxr-x---").</li>
 *   <li><b>NOT_EXISTS</b> — checks that a path does NOT exist in the VFS.</li>
 * </ul>
 */
public class Checkpoint {

    /** The expected node type at the checkpoint path. */
    public enum NodeType {
        DIR, FILE, NOT_EXISTS, CONTENT_EQUALS, PERM
    }

    private final String path;
    private final NodeType expectedType;
    private final String expectedContent;     // used by CONTENT_EQUALS
    private final String expectedPermission;  // used by PERM

    /** Original constructor — backward compatible for DIR / FILE / NOT_EXISTS. */
    public Checkpoint(String path, NodeType expectedType) {
        this(path, expectedType, null, null);
    }

    /** Full constructor for v2.0 types. */
    public Checkpoint(String path, NodeType expectedType,
                      String expectedContent, String expectedPermission) {
        this.path = path;
        this.expectedType = expectedType;
        this.expectedContent = expectedContent;
        this.expectedPermission = expectedPermission;
    }

    public String getPath() {
        return path;
    }

    public NodeType getExpectedType() {
        return expectedType;
    }

    public String getExpectedContent() {
        return expectedContent;
    }

    public String getExpectedPermission() {
        return expectedPermission;
    }

    /**
     * Check whether this checkpoint is satisfied in the given VFS.
     *
     * @param vfs the virtual file system to inspect
     * @return true if the checkpoint condition is met
     */
    public boolean matches(VirtualFileSystem vfs) {
        switch (expectedType) {
        case NOT_EXISTS:
            return matchesNotExists(vfs);
        case CONTENT_EQUALS:
            return matchesContentEquals(vfs);
        case PERM:
            return matchesPerm(vfs);
        default:
            return matchesDirOrFile(vfs);
        }
    }

    private boolean matchesDirOrFile(VirtualFileSystem vfs) {
        try {
            FileNode node = vfs.resolve(path, "/");
            if (expectedType == NodeType.DIR) {
                return node.isDirectory();
            } else {
                return !node.isDirectory();
            }
        } catch (VfsException e) {
            return false;
        }
    }

    private boolean matchesNotExists(VirtualFileSystem vfs) {
        try {
            vfs.resolve(path, "/");
            return false; // path exists, so NOT_EXISTS fails
        } catch (VfsException e) {
            return true;  // path does not exist — pass
        }
    }

    private boolean matchesContentEquals(VirtualFileSystem vfs) {
        try {
            FileNode node = vfs.resolve(path, "/");
            if (node.isDirectory() || expectedContent == null) {
                return false;
            }
            String content = ((RegularFile) node).getContent();
            return expectedContent.equals(content);
        } catch (VfsException e) {
            return false;
        }
    }

    private boolean matchesPerm(VirtualFileSystem vfs) {
        try {
            FileNode node = vfs.resolve(path, "/");
            if (expectedPermission == null) {
                return false;
            }
            Permission perm = node.getPermission();
            return expectedPermission.equals(perm.toString());
        } catch (VfsException e) {
            return false;
        }
    }
}
