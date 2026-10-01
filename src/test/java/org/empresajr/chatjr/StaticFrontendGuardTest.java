package org.empresajr.chatjr;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda da interface estática. A política de segurança do servidor só aceita scripts do próprio site; se alguém
 * reintroduzir um onclick, um script em linha ou um recurso externo, a tela quebra em silêncio no navegador.
 * Estes testes pegam isso no CI, sem precisar de navegador.
 */
class StaticFrontendGuardTest {

    private static final Path STATIC = Path.of("src/main/resources/static");

    private static List<Path> files(String extension) throws IOException {
        try (Stream<Path> walk = Files.walk(STATIC)) {
            return walk.filter(p -> p.toString().endsWith(extension)).sorted().toList();
        }
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static List<Path> codeFiles() throws IOException {
        List<Path> all = new ArrayList<>(files(".html"));
        all.addAll(files(".js"));
        return all;
    }

    @Test
    void thereIsAnInterfaceToServe() throws IOException {
        assertTrue(Files.isRegularFile(STATIC.resolve("index.html")), "falta static/index.html");
        assertTrue(Files.isRegularFile(STATIC.resolve("js/main.js")), "falta static/js/main.js");
        assertTrue(files(".js").size() >= 10, "esperava os módulos da interface");
    }

    @Test
    void noInlineEventHandlersOrJavascriptUrls() throws IOException {
        Pattern attribute = Pattern.compile("\\son[a-zA-Z]+\\s*=\\s*[\"']");
        for (Path file : codeFiles()) {
            String text = read(file);
            assertTrue(!attribute.matcher(text).find(), file + " tem manipulador de evento em linha (onclick=...): use data-action");
            assertTrue(!text.contains("javascript:"), file + " usa javascript: em URL");
        }
    }

    @Test
    void indexLoadsOnlyItsOwnModuleScript() throws IOException {
        String html = read(STATIC.resolve("index.html"));
        Matcher scripts = Pattern.compile("<script\\b[^>]*>").matcher(html);
        int count = 0;
        while (scripts.find()) {
            count++;
            String tag = scripts.group();
            assertTrue(tag.contains("type=\"module\"") && tag.contains("src=\"js/"), "script inesperado no HTML: " + tag);
        }
        assertEquals(1, count, "o HTML deve carregar um único script, o módulo principal");
    }

    @Test
    void noExternalResourcesAndNoDynamicCodeExecution() throws IOException {
        Pattern external = Pattern.compile("(?:src|href)=\"(?:https?:)?//");
        for (Path file : files(".html")) {
            assertTrue(!external.matcher(read(file)).find(), file + " carrega recurso externo");
        }
        for (Path file : files(".js")) {
            String text = read(file);
            for (String forbidden : List.of("eval(", "new Function(", "document.write(")) {
                assertTrue(!text.contains(forbidden), file + " usa " + forbidden);
            }
        }
        for (Path file : files(".css")) {
            String text = read(file);
            assertTrue(!text.contains("@import"), file + " usa @import");
            Matcher urls = Pattern.compile("url\\(['\"]?([^)'\"]+)").matcher(text);
            while (urls.find()) {
                String url = urls.group(1);
                assertTrue(!url.startsWith("http") && !url.startsWith("//"), file + " referencia URL externa: " + url);
                if (!url.startsWith("data:")) {
                    assertTrue(Files.isRegularFile(file.getParent().resolve(url).normalize()), file + " referencia arquivo que não existe: " + url);
                }
            }
        }
    }

    @Test
    void browserStorageIsUsedOnlyForTheThemePreference() throws IOException {
        for (Path file : files(".js")) {
            if (file.getFileName().toString().equals("ui.js")) {
                continue;
            }
            String text = read(file);
            assertTrue(!text.contains("localStorage") && !text.contains("sessionStorage"),
                    file + " guarda dados no navegador; dados do plano ficam só no servidor");
        }
    }

    @Test
    void everyRelativeImportPointsToAnExistingFile() throws IOException {
        Pattern from = Pattern.compile("from '([^']+)'");
        for (Path file : files(".js")) {
            Matcher m = from.matcher(read(file));
            while (m.find()) {
                String target = m.group(1);
                assertTrue(target.startsWith("."), file + " importa de fora: " + target);
                assertTrue(Files.isRegularFile(file.getParent().resolve(target).normalize()), file + " importa arquivo inexistente: " + target);
            }
        }
    }

    @Test
    void everyDataAttributeUsedHasARegisteredHandler() throws IOException {
        Map<String, String> registrars = Map.of("onClick", "action", "onSubmit", "form", "onInput", "input", "onChange", "change");
        Map<String, Set<String>> registered = Map.of("action", new TreeSet<>(), "form", new TreeSet<>(), "input", new TreeSet<>(), "change", new TreeSet<>());
        Map<String, Set<String>> used = Map.of("action", new TreeSet<>(), "form", new TreeSet<>(), "input", new TreeSet<>(), "change", new TreeSet<>());

        for (Path file : files(".js")) {
            String text = read(file);
            for (Map.Entry<String, String> r : registrars.entrySet()) {
                Matcher m = Pattern.compile(r.getKey() + "\\('([\\w-]+)'").matcher(text);
                while (m.find()) {
                    registered.get(r.getValue()).add(m.group(1));
                }
            }
        }
        for (Path file : codeFiles()) {
            String text = read(file);
            for (String kind : used.keySet()) {
                Matcher m = Pattern.compile("[\\s\"'`]data-" + kind + "=\"([^\"]*)\"").matcher(text);
                while (m.find()) {
                    String name = m.group(1);
                    assertTrue(!name.contains("${"), file + " monta data-" + kind + " dinamicamente; o guarda não consegue conferir: " + name);
                    used.get(kind).add(name);
                }
            }
        }
        for (String kind : used.keySet()) {
            Set<String> missing = new TreeSet<>(used.get(kind));
            missing.removeAll(registered.get(kind));
            assertTrue(missing.isEmpty(), "data-" + kind + " sem manipulador registrado (o clique não faria nada): " + missing);
        }
    }
}
