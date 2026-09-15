package me.rique.smpcore.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.fail;

class EventPriorityContractTest {
    private static final Pattern EVENT_MUTATOR = Pattern.compile(
        "\\bevent\\.(setCancelled|setResult|setCurrentItem|setCursor|setDropItems|setExpToDrop|"
            + "setDamage|setFoodLevel|setKeepInventory|setKeepLevel|setDroppedExp|"
            + "setRespawnLocation|setJoinMessage|setQuitMessage)\\s*\\("
    );

    @Test
    void monitorHandlersNeverModifyTheirEvent() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java");
        List<Path> sources;
        try (var paths = Files.walk(sourceRoot)) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }

        for (Path source : sources) {
            String code = Files.readString(source);
            int searchFrom = 0;
            while ((searchFrom = code.indexOf("@EventHandler", searchFrom)) >= 0) {
                int annotationEnd = findAnnotationEnd(code, searchFrom);
                if (annotationEnd < 0) {
                    break;
                }
                String annotation = code.substring(searchFrom, annotationEnd + 1);
                int bodyStart = code.indexOf('{', annotationEnd + 1);
                if (bodyStart < 0) {
                    break;
                }
                int bodyEnd = findMatchingBrace(code, bodyStart);
                if (bodyEnd < 0) {
                    break;
                }

                if (annotation.contains("EventPriority.MONITOR")) {
                    Matcher mutator = EVENT_MUTATOR.matcher(code.substring(bodyStart + 1, bodyEnd));
                    if (mutator.find()) {
                        fail("MONITOR handler modifies its event in " + source + " via " + mutator.group());
                    }
                }
                searchFrom = bodyEnd + 1;
            }
        }
    }

    @Test
    void kickCleanupOnlyRunsAfterTheKickIsFinal() throws IOException {
        Path sourceRoot = Path.of("src", "main", "java");
        List<Path> sources;
        try (var paths = Files.walk(sourceRoot)) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }

        for (Path source : sources) {
            String code = Files.readString(source);
            int searchFrom = 0;
            while ((searchFrom = code.indexOf("@EventHandler", searchFrom)) >= 0) {
                int annotationEnd = findAnnotationEnd(code, searchFrom);
                if (annotationEnd < 0) {
                    break;
                }
                String annotation = code.substring(searchFrom, annotationEnd + 1);
                int bodyStart = code.indexOf('{', annotationEnd + 1);
                if (bodyStart < 0) {
                    break;
                }
                String declaration = code.substring(annotationEnd + 1, bodyStart);
                if (declaration.contains("PlayerKickEvent")
                    && (!annotation.contains("EventPriority.MONITOR")
                    || !annotation.matches("(?s).*ignoreCancelled\\s*=\\s*true.*"))) {
                    fail("PlayerKickEvent cleanup must run at MONITOR and ignore cancelled kicks in " + source);
                }
                int bodyEnd = findMatchingBrace(code, bodyStart);
                if (bodyEnd < 0) {
                    break;
                }
                searchFrom = bodyEnd + 1;
            }
        }
    }

    private static int findAnnotationEnd(String code, int annotationStart) {
        int open = code.indexOf('(', annotationStart);
        if (open < 0) {
            return annotationStart + "@EventHandler".length() - 1;
        }
        int depth = 0;
        for (int index = open; index < code.length(); index++) {
            char current = code.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')' && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static int findMatchingBrace(String code, int bodyStart) {
        int depth = 0;
        boolean inString = false;
        boolean inCharacter = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean escaped = false;

        for (int index = bodyStart; index < code.length(); index++) {
            char current = code.charAt(index);
            char next = index + 1 < code.length() ? code.charAt(index + 1) : '\0';

            if (inLineComment) {
                if (current == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    index++;
                }
                continue;
            }
            if (inString || inCharacter) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (inString && current == '"') {
                    inString = false;
                } else if (inCharacter && current == '\'') {
                    inCharacter = false;
                }
                continue;
            }
            if (current == '/' && next == '/') {
                inLineComment = true;
                index++;
                continue;
            }
            if (current == '/' && next == '*') {
                inBlockComment = true;
                index++;
                continue;
            }
            if (current == '"') {
                inString = true;
                continue;
            }
            if (current == '\'') {
                inCharacter = true;
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return index;
            }
        }
        return -1;
    }
}
