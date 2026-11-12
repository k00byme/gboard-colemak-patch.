package org.k00byme.samsungkeyboard.runtime;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Conservative PT-BR accent-only correction hook. All failures leave input untouched. */
public final class AccentOnlyRuntime {
    private static final ThreadLocal<Boolean> INTERNAL = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final Map<String, String> WORDS;

    static {
        Map<String, String> words = new HashMap<>();
        add(words,
            "nao|não",
            "voce|você", "voces|vocês",
            "tambem|também",
            "cafe|café", "cafes|cafés",
            "facil|fácil", "dificil|difícil",
            "lapis|lápis", "onibus|ônibus",
            "agua|água", "aguas|águas",
            "saude|saúde",
            "familia|família", "familias|famílias",
            "mae|mãe", "maes|mães",
            "irma|irmã", "irmas|irmãs", "irmao|irmão", "irmaos|irmãos",
            "ja|já",
            "alguem|alguém", "ninguem|ninguém",
            "proximo|próximo", "proxima|próxima", "proximos|próximos", "proximas|próximas",
            "ultimo|último", "ultima|última", "ultimos|últimos", "ultimas|últimas",
            "unico|único", "unica|única", "unicos|únicos", "unicas|únicas",
            "possivel|possível", "impossivel|impossível",
            "incrivel|incrível", "horrivel|horrível", "fragil|frágil", "util|útil", "inutil|inútil",
            "video|vídeo", "videos|vídeos", "audio|áudio",
            "musica|música", "musicas|músicas",
            "historia|história", "historias|histórias",
            "serie|série", "series|séries",
            "periodo|período", "periodos|períodos", "inicio|início",
            "memoria|memória", "memorias|memórias",
            "maquina|máquina", "maquinas|máquinas",
            "camera|câmera", "cameras|câmeras",
            "usuario|usuário", "usuarios|usuários",
            "icone|ícone", "icones|ícones",
            "pagina|página", "paginas|páginas",
            "paragrafo|parágrafo", "paragrafos|parágrafos",
            "lingua|língua", "linguas|línguas",
            "area|área", "areas|áreas",
            "orgao|órgão", "orgaos|órgãos",
            "cabeca|cabeça", "cabecas|cabeças",
            "aviao|avião", "avioes|aviões",
            "feijao|feijão", "pao|pão", "paes|pães", "cha|chá", "gas|gás",
            "alcool|álcool", "alcoolico|alcoólico", "alcoolica|alcoólica",
            "acucar|açúcar", "sofa|sofá", "sofas|sofás",
            "matematica|matemática", "matematico|matemático",
            "fisica|física", "fisico|físico", "quimica|química", "quimico|químico",
            "tecnico|técnico", "tecnica|técnica", "tecnicos|técnicos", "tecnicas|técnicas",
            "basico|básico", "basica|básica", "basicos|básicos", "basicas|básicas",
            "eletronico|eletrônico", "eletronica|eletrônica", "eletronicos|eletrônicos", "eletronicas|eletrônicas",
            "academico|acadêmico", "academica|acadêmica",
            "logico|lógico", "logica|lógica",
            "classico|clássico", "classica|clássica",
            "historico|histórico", "historica|histórica",
            "juridico|jurídico", "juridica|jurídica",
            "economico|econômico", "economica|econômica",
            "teorico|teórico", "teorica|teórica",
            "hipotese|hipótese", "hipoteses|hipóteses",
            "sintese|síntese", "sinteses|sínteses",
            "proposito|propósito",
            "ingles|inglês", "japones|japonês", "frances|francês", "alemao|alemão",
            "album|álbum", "albuns|álbuns",
            "passaro|pássaro", "passaros|pássaros",
            "arvore|árvore", "arvores|árvores",
            "heroi|herói", "herois|heróis",
            "relogio|relógio", "relogios|relógios",
            "otimo|ótimo", "otima|ótima", "otimos|ótimos", "otimas|ótimas",
            "pessimo|péssimo", "pessima|péssima",
            "vitoria|vitória", "vitorias|vitórias",
            "nivel|nível", "niveis|níveis", "carater|caráter",
            "caracteristica|característica", "caracteristicas|características",
            "caracteristico|característico"
        );
        WORDS = Collections.unmodifiableMap(words);
    }

    private AccentOnlyRuntime() {}

    private static void add(Map<String, String> map, String... pairs) {
        for (String pair : pairs) {
            int split = pair.indexOf('|');
            map.put(pair.substring(0, split), pair.substring(split + 1));
        }
    }

    /** Called at the start of the Samsung input-connection wrapper's commitText. */
    public static CharSequence normalizeCommit(Object wrapper, CharSequence incoming) {
        if (incoming == null || Boolean.TRUE.equals(INTERNAL.get())) return incoming;
        try {
            String value = incoming.toString();
            int separatorStart = trailingSeparatorStart(value);
            if (separatorStart < 0) return incoming;

            // Case A: the IME commits the word and its delimiter in the same call.
            if (separatorStart > 0) {
                String prefix = value.substring(0, separatorStart);
                int wordStart = trailingWordStart(prefix);
                if (wordStart >= 0) {
                    String word = prefix.substring(wordStart);
                    String corrected = correctCase(word);
                    if (corrected != null) {
                        return value.substring(0, wordStart) + corrected + value.substring(separatorStart);
                    }
                }
                return incoming;
            }

            // Case B: the IME commits a delimiter after a composing word already exists.
            if (wrapper == null) return incoming;
            Object before = invoke(wrapper, "getTextBeforeCursor", new Class<?>[]{int.class, int.class}, 64, 0);
            if (before == null) return incoming;
            String context = before.toString();
            int wordStart = trailingWordStart(context);
            if (wordStart < 0) return incoming;
            String oldWord = context.substring(wordStart);
            String corrected = correctCase(oldWord);
            if (corrected == null || corrected.equals(oldWord)) return incoming;

            INTERNAL.set(Boolean.TRUE);
            try {
                Object deleted = invoke(wrapper, "deleteSurroundingText", new Class<?>[]{int.class, int.class}, oldWord.length(), 0);
                if (!Boolean.TRUE.equals(deleted)) return incoming;
                Object committed = invoke(wrapper, "commitText", new Class<?>[]{CharSequence.class, int.class}, corrected, 1);
                if (!Boolean.TRUE.equals(committed)) {
                    // Best-effort rollback if a host-side commit rejects the replacement.
                    invoke(wrapper, "commitText", new Class<?>[]{CharSequence.class, int.class}, oldWord, 1);
                }
            } finally {
                INTERNAL.set(Boolean.FALSE);
            }
        } catch (Throwable ignored) {
            // Do not interfere with text entry if the host implementation differs.
        }
        return incoming;
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static int trailingSeparatorStart(String text) {
        if (text.isEmpty()) return -1;
        int i = text.length();
        while (i > 0) {
            int cp = text.codePointBefore(i);
            if (Character.isWhitespace(cp) || isSentencePunctuation(cp)) i -= Character.charCount(cp);
            else break;
        }
        return i == text.length() ? -1 : i;
    }

    private static boolean isSentencePunctuation(int cp) {
        return cp == '.' || cp == ',' || cp == '!' || cp == '?' || cp == ';' || cp == ':'
                || cp == ')' || cp == ']' || cp == '}' || cp == '…';
    }

    private static int trailingWordStart(String text) {
        int i = text.length();
        while (i > 0) {
            int cp = text.codePointBefore(i);
            if (Character.isLetter(cp)) i -= Character.charCount(cp);
            else break;
        }
        if (i == text.length()) return -1;
        if (i > 0) {
            int previous = text.codePointBefore(i);
            if (Character.isLetterOrDigit(previous) || previous == '_' || previous == '\'') return -1;
        }
        return i;
    }

    private static String correctCase(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        String corrected = WORDS.get(lower);
        if (corrected == null) return null;
        if (word.equals(word.toUpperCase(Locale.ROOT))) return corrected.toUpperCase(Locale.ROOT);
        if (!word.isEmpty() && Character.isUpperCase(word.codePointAt(0))) {
            return corrected.substring(0, 1).toUpperCase(Locale.ROOT) + corrected.substring(1);
        }
        return corrected;
    }
}
