package www.ontologyutils.repair.strategy;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Every strategy must be readable on its own.
 *
 * <p>
 * A strategy that imports another strategy, or a helper that lives beside the
 * strategies, cannot be understood or changed without following references out of
 * the file, and a mistake in one strategy can silently affect another. This test
 * keeps the package free of such references: the only thing a strategy may
 * depend on is the interface it implements.
 */
public class StrategyIndependenceTest {
    private static final Path STRATEGY_SOURCES = Path.of("src/main/java/www/ontologyutils/repair/strategy");
    private static final String STRATEGY_IMPORT = "import www.ontologyutils.repair.strategy.";

    @Test
    public void strategiesDoNotDependOnOtherStrategies() throws IOException {
        assertTrue(Files.isDirectory(STRATEGY_SOURCES),
                "expected to run from the project directory, but " + STRATEGY_SOURCES + " does not exist");

        List<String> offenders = new ArrayList<>();
        try (var sources = Files.walk(STRATEGY_SOURCES)) {
            sources.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    var lines = Files.readAllLines(path);
                    for (var line : lines) {
                        if (line.startsWith(STRATEGY_IMPORT)) {
                            offenders.add(STRATEGY_SOURCES.relativize(path) + ": " + line.strip());
                        }
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        }

        assertTrue(offenders.isEmpty(),
                "a strategy must not depend on another part of the strategy package: " + offenders);
    }

    @Test
    public void strategiesKeepNoStateOutsideThemselves() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (var sources = Files.walk(STRATEGY_SOURCES)) {
            sources.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    var text = Files.readString(path);
                    // A static field that is not final is mutable state shared by every
                    // strategy instance in the process, which is exactly what the
                    // strategies must not have.
                    text.lines()
                            .filter(line -> line.stripLeading().startsWith("private static")
                                    && !line.contains(" final ") && !line.contains("("))
                            .forEach(line -> offenders.add(STRATEGY_SOURCES.relativize(path) + ": " + line.strip()));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        }

        assertTrue(offenders.isEmpty(), "a strategy must not keep mutable static state: " + offenders);
    }
}
