package linuxlingo.shell;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import linuxlingo.cli.Ui;
import linuxlingo.shell.vfs.VirtualFileSystem;

/**
 * Tests for v2.0 shell experience features:
 * aliases, "did you mean?", glob expansion, || operator, < input redirect.
 */
public class ShellSessionV2Test {
    private ShellSession session;
    private VirtualFileSystem vfs;
    private ByteArrayOutputStream outStream;

    @BeforeEach
    public void setUp() {
        vfs = new VirtualFileSystem();
        outStream = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outStream);
        Ui ui = new Ui(new ByteArrayInputStream(new byte[0]), out);
        session = new ShellSession(vfs, ui);
        session.setWorkingDir("/home/user");
    }

    // ─── Alias resolution in shell ──────────────────────────────

    @Nested
    class AliasResolution {
        @Test
        public void alias_resolvedDuringExecution() {
            // Set up an alias and verify it resolves
            session.getAliases().put("ll", "ls");
            CommandResult result = session.executeOnce("ll");
            // Should resolve to 'ls' and succeed
            assertTrue(result.isSuccess());
        }

        @Test
        public void alias_notSetUp_commandNotFound() {
            CommandResult result = session.executeOnce("ll");
            // 'll' is not a registered command
            assertFalse(result.isSuccess());
        }
    }

    // ─── "Did you mean?" suggestion ──────────────────────────────

    @Nested
    class DidYouMean {
        @Test
        public void suggestCommand_typoClose_suggestsCorrect() {
            String suggestion = session.suggestCommand("lss");
            assertNotNull(suggestion);
            assertTrue(suggestion.contains("ls"));
        }

        @Test
        public void suggestCommand_typoClose2_suggestsCorrect() {
            String suggestion = session.suggestCommand("gre");
            assertNotNull(suggestion);
            assertTrue(suggestion.contains("grep"));
        }

        @Test
        public void suggestCommand_tooFar_returnsNull() {
            String suggestion = session.suggestCommand("xyzabc");
            assertNull(suggestion);
        }

        @Test
        public void suggestCommand_exactMatch_returnsNull() {
            String suggestion = session.suggestCommand("ls");
            assertNull(suggestion);
        }

        @Test
        public void suggestCommand_emptyInput_returnsNull() {
            String suggestion = session.suggestCommand("");
            assertNull(suggestion);
        }

        @Test
        public void suggestCommand_nullInput_returnsNull() {
            String suggestion = session.suggestCommand(null);
            assertNull(suggestion);
        }
    }

    // ─── Edit distance ──────────────────────────────────────────

    @Nested
    class EditDistance {
        @Test
        public void editDistance_sameStrings_returnsZero() {
            assertEquals(0, ShellSession.editDistance("abc", "abc"));
        }

        @Test
        public void editDistance_oneInsertion_returnsOne() {
            assertEquals(1, ShellSession.editDistance("abc", "ab"));
        }

        @Test
        public void editDistance_oneDeletion_returnsOne() {
            assertEquals(1, ShellSession.editDistance("ab", "abc"));
        }

        @Test
        public void editDistance_oneSubstitution_returnsOne() {
            assertEquals(1, ShellSession.editDistance("abc", "axc"));
        }

        @Test
        public void editDistance_emptyStrings() {
            assertEquals(0, ShellSession.editDistance("", ""));
            assertEquals(3, ShellSession.editDistance("abc", ""));
            assertEquals(3, ShellSession.editDistance("", "abc"));
        }
    }

    // ─── Glob expansion ─────────────────────────────────────────

    @Nested
    class GlobExpansion {
        @Test
        public void expandGlobs_noWildcard_returnsUnchanged() {
            String[] result = session.expandGlobs(new String[]{"hello", "world"});
            assertEquals(2, result.length);
            assertEquals("hello", result[0]);
            assertEquals("world", result[1]);
        }

        @Test
        public void expandGlobs_wildcardNoMatch_keepsLiteral() {
            String[] result = session.expandGlobs(new String[]{"*.nonexistent"});
            assertEquals(1, result.length);
            assertEquals("*.nonexistent", result[0]);
        }
    }

    // ─── || (OR) operator ───────────────────────────────────────

    @Nested
    class OrOperator {
        @Test
        public void orOperator_firstSucceeds_secondSkipped() {
            // 'echo hello || echo world' → only first runs
            vfs.createFile("/home/user/test.txt", "/");
            // Use a simulated approach
            CommandResult result = session.executeOnce("echo hello || echo world");
            // echo hello should succeed, echo world should be skipped
            assertTrue(result.isSuccess());
            assertEquals("hello", result.getStdout());
        }

        @Test
        public void orOperator_firstFails_secondRuns() {
            // 'cat nonexistent.txt || echo fallback'
            CommandResult result = session.executeOnce("cat nonexistent.txt || echo fallback");
            assertTrue(result.isSuccess());
            assertEquals("fallback", result.getStdout());
        }
    }

    // ─── < input redirect ──────────────────────────────────────

    @Nested
    class InputRedirect {
        @Test
        public void inputRedirect_readsFileAsStdin() {
            vfs.createFile("/home/user/input.txt", "/");
            vfs.writeFile("/home/user/input.txt", "/", "hello\nworld\nhello", false);

            // grep hello < input.txt
            CommandResult result = session.executeOnce("grep hello < input.txt");
            assertTrue(result.isSuccess());
            assertTrue(result.getStdout().contains("hello"));
        }
    }

    // ─── ShellParser token tests ────────────────────────────────

    @Nested
    class ParserTokens {
        @Test
        public void parser_orToken_parsed() {
            ShellParser.ParsedPlan plan = new ShellParser().parse("echo a || echo b");
            assertEquals(2, plan.segments.size());
            assertEquals(1, plan.operators.size());
            assertEquals(ShellParser.TokenType.OR, plan.operators.get(0));
        }

        @Test
        public void parser_inputRedirect_parsed() {
            ShellParser.ParsedPlan plan = new ShellParser().parse("grep hello < input.txt");
            assertEquals(1, plan.segments.size());
            assertEquals("input.txt", plan.segments.get(0).inputRedirect);
        }

        @Test
        public void parser_pipeAndOr_mixed() {
            ShellParser.ParsedPlan plan = new ShellParser().parse("cat file.txt | grep hello || echo not found");
            assertEquals(3, plan.segments.size());
            assertEquals(ShellParser.TokenType.PIPE, plan.operators.get(0));
            assertEquals(ShellParser.TokenType.OR, plan.operators.get(1));
        }
    }
}
