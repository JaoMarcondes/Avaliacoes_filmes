import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

// Supabase: conexão, dados de filmes/avaliações e conversão de JSON.

// -------- Supabase --------
/** Acesso ao Supabase. Todas as telas usam esta mesma classe. */
class Supabase {
    static final String URL = "https://pfqubgfnjtxsocwtmzrm.supabase.co";
    static final String CHAVE = "sb_publishable_155R5aCPgq2Rv24Y_NHwEQ_YAeUgs2D";
    static final String BUCKET = "capas-filmes";
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12)).followRedirects(HttpClient.Redirect.NORMAL).build();

    String token, refresh, usuarioId, email, nome;
    boolean administrador;
    private long expiracao;

    void entrar(String email, String senha) throws Exception {
        Map<String, Object> dados = Map.of("email", email, "password", senha);
        Map<String, Object> resultado = mapa(enviar("POST", "/auth/v1/token?grant_type=password", dados, false));
        iniciarSessao(resultado);
    }

    /** Retorna false se o Supabase exigir confirmação de e-mail. */
    boolean cadastrar(String nome, String email, String senha) throws Exception {
        Map<String, Object> valores = Map.of("email", email, "password", senha,
                "data", Map.of("nome", nome));
        Map<String, Object> resposta = mapa(enviar("POST", "/auth/v1/signup", valores, false));
        if (mapa(resposta.get("user")).isEmpty())
            throw new IOException("O Supabase não confirmou a criação da conta.");
        if (texto(resposta.get("access_token")).isBlank()) return false;
        iniciarSessao(resposta);
        this.nome = nome;
        return true;
    }

    private void iniciarSessao(Map<String, Object> resultado) throws Exception {
        token = texto(resultado.get("access_token"));
        if (token.isBlank()) throw new IOException("Login sem token de acesso.");
        refresh = texto(resultado.get("refresh_token"));
        expiracao = System.currentTimeMillis() + Math.max(60, numero(resultado.get("expires_in")).longValue()) * 1000L;
        Map<String, Object> usuario = mapa(resultado.get("user"));
        usuarioId = texto(usuario.get("id"));
        email = texto(usuario.get("email"));
        nome = texto(mapa(usuario.get("user_metadata")).get("nome"));
        if (usuarioId.isBlank()) throw new IOException("Usuário não identificado.");
        try {
            String rota = "/rest/v1/cinerate_admins?select=user_id&user_id=eq." + usuarioId;
            administrador = !lista(api("GET", rota, null)).isEmpty();
        } catch (Exception e) {
            administrador = false; // Sem privilégios na dúvida.
        }
    }

    void sair() {
        token = refresh = usuarioId = email = nome = null;
        administrador = false;
    }

    void alterarNome(String nome) throws Exception {
        api("PUT", "/auth/v1/user", Map.of("data", Map.of("nome", nome)));
        this.nome = nome;
    }

    List<Filme> listarFilmes() throws Exception {
        List<Filme> filmes = new ArrayList<>();
        String rota = "/rest/v1/filme?select=id_filme,titulo,ano_lancamento,duracao,id_genero,imagem_url&order=titulo.asc";
        for (Object linha : lista(api("GET", rota, null))) {
            Map<String, Object> f = mapa(linha);
            filmes.add(new Filme(numero(f.get("id_filme")).longValue(), texto(f.get("titulo")),
                    numero(f.get("ano_lancamento")).intValue(), numero(f.get("duracao")).intValue(),
                    numero(f.get("id_genero")).longValue(), texto(f.get("imagem_url"))));
        }
        return filmes;
    }

    Map<Long, String> listarGeneros() throws Exception {
        Map<Long, String> generos = new LinkedHashMap<>();
        for (Object linha : lista(api("GET", "/rest/v1/genero?select=id_genero,nome_genero&order=nome_genero.asc", null))) {
            Map<String, Object> g = mapa(linha);
            generos.put(numero(g.get("id_genero")).longValue(), texto(g.get("nome_genero")));
        }
        return generos;
    }

    List<Avaliacao> listarAvaliacoes(long filmeId) throws Exception {
        return avaliacoes("&id_filme=eq." + filmeId);
    }
    List<Avaliacao> minhasAvaliacoes() throws Exception {
        return avaliacoes("&id_usuario=eq." + usuarioId);
    }
    private List<Avaliacao> avaliacoes(String filtro) throws Exception {
        String rota = "/rest/v1/avaliacao_web?select=id_avaliacao,id_filme,id_usuario,nota,comentario,criado_em,atualizado_em"
                + filtro + "&order=atualizado_em.desc";
        List<Avaliacao> resultado = new ArrayList<>();
        for (Object item : lista(api("GET", rota, null))) {
            Map<String, Object> a = mapa(item);
            resultado.add(new Avaliacao(numero(a.get("id_avaliacao")).longValue(),
                    numero(a.get("id_filme")).longValue(), texto(a.get("id_usuario")),
                    numero(a.get("nota")).doubleValue(), texto(a.get("comentario")),
                    texto(a.get("criado_em")), texto(a.get("atualizado_em"))));
        }
        return resultado;
    }

    void salvarAvaliacao(long filmeId, double nota, String comentario) throws Exception {
        if (nota < 0 || nota > 5 || Math.rint(nota * 2) != nota * 2)
            throw new IOException("A nota deve estar entre 0 e 5, de 0,5 em 0,5.");
        if (comentario.length() > 1000) throw new IOException("Comentário muito longo (limite de 1000 caracteres).");
        List<Avaliacao> minhas = listarAvaliacoes(filmeId);
        Avaliacao existente = minhas.stream().filter(a -> a.usuarioId().equals(usuarioId)).findFirst().orElse(null);
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("nota", nota);
        valores.put("comentario", comentario);
        if (existente == null) {
            valores.put("id_filme", filmeId);
            api("POST", "/rest/v1/avaliacao_web", valores);
        } else {
            api("PATCH", "/rest/v1/avaliacao_web?id_avaliacao=eq." + existente.id(), valores);
        }
    }

    void excluirAvaliacao(long id) throws Exception {
        api("DELETE", "/rest/v1/avaliacao_web?id_avaliacao=eq." + id + "&id_usuario=eq." + usuarioId, null);
    }

    Map<String, String> nomesPublicos(List<Avaliacao> avaliacoes) {
        Map<String, String> nomes = new HashMap<>();
        if (usuarioId != null && nome != null) nomes.put(usuarioId, nome);
        Set<String> ids = new LinkedHashSet<>();
        for (Avaliacao a : avaliacoes) if (!a.usuarioId().isBlank()) ids.add(a.usuarioId());
        if (ids.isEmpty()) return nomes;
        try {
            String filtro = String.join(",", ids);
            String rota = "/rest/v1/cinerate_perfis?select=id_usuario,nome&id_usuario=in.(" + filtro + ")";
            for (Object item : lista(api("GET", rota, null))) {
                Map<String, Object> p = mapa(item);
                nomes.put(texto(p.get("id_usuario")), texto(p.get("nome")));
            }
        } catch (Exception e) {
            // Se a tabela opcional de nomes ainda não existir, exibe "Usuário".
        }
        return nomes;
    }

    void cadastrarFilme(String titulo, int ano, int duracao, long genero, String capa) throws Exception {
        if (!administrador) throw new IOException("Apenas administradores podem cadastrar filmes.");
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("titulo", titulo);
        dados.put("ano_lancamento", ano);
        dados.put("duracao", duracao);
        dados.put("id_genero", genero);
        dados.put("imagem_url", capa);
        api("POST", "/rest/v1/filme", dados);
    }

    String enviarCapa(File arquivo) throws Exception {
        if (!administrador) throw new IOException("Somente administradores podem enviar capas.");
        String lower = arquivo.getName().toLowerCase(Locale.ROOT);
        String tipo;
        String ext;
        if (lower.endsWith(".png")) { tipo = "image/png"; ext = ".png"; }
        else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) { tipo = "image/jpeg"; ext = ".jpg"; }
        else if (lower.endsWith(".webp")) { tipo = "image/webp"; ext = ".webp"; }
        else throw new IOException("A imagem deve ser PNG, JPG ou WebP.");
        if (Files.size(arquivo.toPath()) > 5 * 1024 * 1024L)
            throw new IOException("A imagem deve ter até 5 MB.");
        String path = "filmes/" + UUID.randomUUID() + ext;
        renovar();
        HttpRequest req = HttpRequest.newBuilder(URI.create(URL + "/storage/v1/object/" + BUCKET + "/" + path))
                .timeout(Duration.ofSeconds(45)).header("apikey", CHAVE)
                .header("Authorization", "Bearer " + token).header("Content-Type", tipo)
                .POST(HttpRequest.BodyPublishers.ofFile(arquivo.toPath())).build();
        resposta(http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
        return URL + "/storage/v1/object/public/" + BUCKET + "/" + path;
    }

    private synchronized void renovar() throws Exception {
        if (token == null || token.isBlank()) throw new IOException("Faça login para continuar.");
        if (System.currentTimeMillis() < expiracao - 60000) return;
        Map<String, Object> dados = Map.of("refresh_token", refresh);
        Map<String, Object> resposta = mapa(enviar("POST", "/auth/v1/token?grant_type=refresh_token", dados, false));
        token = texto(resposta.get("access_token"));
        refresh = texto(resposta.get("refresh_token"));
        expiracao = System.currentTimeMillis() + numero(resposta.get("expires_in")).longValue() * 1000L;
    }

    Object api(String metodo, String rota, Object dados) throws Exception {
        renovar();
        return enviar(metodo, rota, dados, true);
    }

    private Object enviar(String metodo, String rota, Object dados, boolean autenticado) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(URL + rota))
                .timeout(Duration.ofSeconds(25)).header("apikey", CHAVE)
                .header("Accept", "application/json");
        if (autenticado) req.header("Authorization", "Bearer " + token);
        if (metodo.equals("GET")) req.GET();
        else {
            if (autenticado && rota.startsWith("/rest/v1/"))
                req.header("Prefer", "return=representation");
            if (dados == null) req.method(metodo, HttpRequest.BodyPublishers.noBody());
            else req.header("Content-Type", "application/json")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(Json.encode(dados), StandardCharsets.UTF_8));
        }
        return resposta(http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
    }

    private static Object resposta(HttpResponse<String> resp) throws IOException {
        String texto = resp.body() == null ? "" : resp.body();
        Object json;
        try { json = texto.isBlank() ? Map.of() : Json.parse(texto); }
        catch (Exception e) { json = Map.of("message", texto); }
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            Map<String, Object> erro = mapa(json);
            String mensagem = texto(erro.get("msg"));
            if (mensagem.isBlank()) mensagem = texto(erro.get("message"));
            if (mensagem.isBlank()) mensagem = texto(erro.get("error_description"));
            if (mensagem.isBlank()) mensagem = texto(erro.get("error"));
            if (mensagem.isBlank()) mensagem = "Falha na requisição ao Supabase.";
            if (mensagem.length() > 210) mensagem = mensagem.substring(0, 210);
            throw new IOException("Supabase HTTP " + resp.statusCode() + ": " + mensagem);
        }
        return json;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object obj) {
        return obj instanceof Map<?, ?> ? (Map<String, Object>) obj : Map.of();
    }
    @SuppressWarnings("unchecked")
    private static List<Object> lista(Object obj) {
        return obj instanceof List<?> ? (List<Object>) obj : List.of();
    }
    private static String texto(Object obj) { return obj == null ? "" : String.valueOf(obj); }
    private static Number numero(Object obj) {
        if (obj instanceof Number n) return n;
        try { return Double.valueOf(texto(obj)); } catch (Exception e) { return 0; }
    }
}


// -------- Filme --------
/** Supabase de um filme da tabela filme. */
record Filme(long id, String titulo, int ano, int duracao, long generoId, String capa) { }


// -------- Avaliacao --------
/** Uma avaliação salva na tabela avaliacao_web. */
record Avaliacao(long id, long filmeId, String usuarioId, double nota,
                 String comentario, String criadoEm, String atualizadoEm) { }


// -------- Json --------
/** Converte respostas da API entre texto JSON e objetos Java. */
class Json {
        static Object parse(String json) { return new Parser(json).parse(); }
        static String encode(Object value) {
            if (value == null) return "null";
            if (value instanceof String s) {
                StringBuilder b = new StringBuilder("\"");
                for (char c : s.toCharArray()) {
                    switch (c) {
                        case '"' -> b.append("\\\"");
                        case '\\' -> b.append("\\\\");
                        case '\n' -> b.append("\\n");
                        case '\r' -> b.append("\\r");
                        case '\t' -> b.append("\\t");
                        default -> { if (c < 32) b.append(String.format("\\u%04x", (int)c)); else b.append(c); }
                    }
                }
                return b.append('"').toString();
            }
            if (value instanceof Boolean || value instanceof Number) return value.toString();
            if (value instanceof Map<?, ?> map) {
                StringJoiner joined = new StringJoiner(",", "{", "}");
                map.forEach((k, v) -> joined.add(encode(k.toString()) + ":" + encode(v)));
                return joined.toString();
            }
            if (value instanceof Iterable<?> list) {
                StringJoiner joined = new StringJoiner(",", "[", "]");
                list.forEach(v -> joined.add(encode(v)));
                return joined.toString();
            }
            throw new IllegalArgumentException("Tipo não suportado em JSON");
        }
        private static class Parser {
            private final String s;
            private int p = 0;
            Parser(String source) { s = source; }
            Object parse() {
                Object result = value();
                whitespace();
                if (p != s.length()) throw new IllegalArgumentException("JSON extra");
                return result;
            }
            private void whitespace() { while (p < s.length() && Character.isWhitespace(s.charAt(p))) p++; }
            private char peek() { whitespace(); return p == s.length() ? '\0' : s.charAt(p); }
            private void expect(char c) {
                if (peek() != c) throw new IllegalArgumentException("JSON esperado: " + c);
                p++;
            }
            private Object value() {
                char c = peek();
                if (c == '{') return object();
                if (c == '[') return array();
                if (c == '"') return string();
                if (s.startsWith("true", p)) { p += 4; return true; }
                if (s.startsWith("false", p)) { p += 5; return false; }
                if (s.startsWith("null", p)) { p += 4; return null; }
                if (c == '-' || (c >= '0' && c <= '9')) return numeric();
                throw new IllegalArgumentException("JSON inválido posição " + p);
            }
            private Map<String, Object> object() {
                Map<String, Object> map = new LinkedHashMap<>();
                expect('{');
                if (peek() == '}') { p++; return map; }
                while (true) {
                    String key = string();
                    expect(':');
                    map.put(key, value());
                    if (peek() == '}') { p++; return map; }
                    expect(',');
                }
            }
            private List<Object> array() {
                List<Object> result = new ArrayList<>();
                expect('[');
                if (peek() == ']') { p++; return result; }
                while (true) {
                    result.add(value());
                    if (peek() == ']') { p++; return result; }
                    expect(',');
                }
            }
            private String string() {
                expect('"');
                StringBuilder result = new StringBuilder();
                while (p < s.length()) {
                    char c = s.charAt(p++);
                    if (c == '"') return result.toString();
                    if (c != '\\') { result.append(c); continue; }
                    if (p >= s.length()) throw new IllegalArgumentException("Escape inválido");
                    char escape = s.charAt(p++);
                    switch (escape) {
                        case '"' -> result.append('"');
                        case '\\' -> result.append('\\');
                        case '/' -> result.append('/');
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> {
                            if (p + 4 > s.length()) throw new IllegalArgumentException("Unicode inválido");
                            result.append((char) Integer.parseInt(s.substring(p, p + 4), 16));
                            p += 4;
                        }
                        default -> throw new IllegalArgumentException("Escape inválido");
                    }
                }
                throw new IllegalArgumentException("String JSON incompleta");
            }
            private Number numeric() {
                int start = p;
                if (s.charAt(p) == '-') p++;
                while (p < s.length() && Character.isDigit(s.charAt(p))) p++;
                if (p < s.length() && s.charAt(p) == '.') {
                    p++; while (p < s.length() && Character.isDigit(s.charAt(p))) p++;
                }
                if (p < s.length() && (s.charAt(p) == 'e' || s.charAt(p) == 'E')) {
                    p++;
                    if (p < s.length() && (s.charAt(p) == '+' || s.charAt(p) == '-')) p++;
                    while (p < s.length() && Character.isDigit(s.charAt(p))) p++;
                }
                String n = s.substring(start, p);
                if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.valueOf(n);
                return Long.valueOf(n);
            }
        }
    }

