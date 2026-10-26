import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Persistência MySQL local. Mantém a interface usada pelas telas originais. */
class Supabase {
    String usuarioId, email, nome;
    boolean administrador;
    private Connection conectar() throws Exception {
        Properties p = new Properties();
        Path config = Path.of("database.properties");
        if (Files.exists(config)) try (Reader r = Files.newBufferedReader(config)) { p.load(r); }
        Class.forName("com.mysql.cj.jdbc.Driver");
        String host = p.getProperty("db.host", "localhost");
        String porta = p.getProperty("db.port", "3306");
        String banco = p.getProperty("db.name", "cinerate");
        return DriverManager.getConnection("jdbc:mysql://" + host + ":" + porta + "/" + banco
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=UTC&connectTimeout=5000&socketTimeout=15000",
                p.getProperty("db.user", "root"), p.getProperty("db.password", ""));
    }
    private void sessao() throws IOException {
        if (usuarioId == null) throw new IOException("Faça login para continuar.");
    }
    void entrar(String mail, String senha) throws Exception {
        sair();
        try (Connection c = conectar(); PreparedStatement s = c.prepareStatement("SELECT * FROM usuario WHERE email=?")) {
            s.setString(1, mail.trim().toLowerCase(Locale.ROOT));
            try (ResultSet r = s.executeQuery()) {
                if (!r.next() || !verificar(senha, r.getString("senha_hash"))) throw new IOException("E-mail ou senha incorretos.");
                usuarioId = r.getString("id_usuario"); email = r.getString("email");
                nome = r.getString("nome"); administrador = r.getBoolean("administrador");
            }
        }
    }
    boolean cadastrar(String nome, String mail, String senha) throws Exception {
        if (nome.trim().length()<2 || nome.length()>100 || mail.length()>254 || !mail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$") || senha.length()<6)
            throw new IOException("Confira nome, e-mail e senha (mínimo de 6 caracteres).");
        try (Connection c = conectar(); PreparedStatement s = c.prepareStatement("INSERT INTO usuario(id_usuario,nome,email,senha_hash) VALUES(?,?,?,?)")) {
            s.setString(1, UUID.randomUUID().toString()); s.setString(2,nome.trim());
            s.setString(3,mail.trim().toLowerCase(Locale.ROOT)); s.setString(4,hash(senha));
            try { s.executeUpdate(); } catch (SQLIntegrityConstraintViolationException e) { throw new IOException("E-mail já cadastrado."); }
        }
        entrar(mail,senha); return true;
    }
    void sair() { usuarioId=email=nome=null; administrador=false; }
    void alterarNome(String valor) throws Exception {
        sessao(); if(valor.trim().length()<2 || valor.length()>100) throw new IOException("Nome: entre 2 e 100 caracteres.");
        executar("UPDATE usuario SET nome=? WHERE id_usuario=?",valor.trim(),usuarioId); nome=valor.trim();
    }
    List<Filme> listarFilmes() throws Exception {
        sessao(); List<Filme> lista=new ArrayList<>();
        try(Connection c=conectar(); PreparedStatement s=c.prepareStatement("SELECT * FROM filme ORDER BY titulo"); ResultSet r=s.executeQuery()) {
            while(r.next()) lista.add(new Filme(r.getLong("id_filme"),r.getString("titulo"),r.getInt("ano_lancamento"),r.getInt("duracao"),r.getLong("id_genero"),r.getString("imagem_url")));
        } return lista;
    }
    Map<Long,String> listarGeneros() throws Exception {
        sessao(); Map<Long,String> lista=new LinkedHashMap<>();
        try(Connection c=conectar(); PreparedStatement s=c.prepareStatement("SELECT * FROM genero ORDER BY nome_genero"); ResultSet r=s.executeQuery()) {
            while(r.next()) lista.put(r.getLong("id_genero"),r.getString("nome_genero"));
        } return lista;
    }
    List<Avaliacao> listarAvaliacoes(long filmeId) throws Exception { return avaliacoes("id_filme",filmeId); }
    List<Avaliacao> minhasAvaliacoes() throws Exception { return avaliacoes("id_usuario",usuarioId); }
    private List<Avaliacao> avaliacoes(String campo,Object valor) throws Exception {
        sessao(); List<Avaliacao> lista=new ArrayList<>();
        try(Connection c=conectar(); PreparedStatement s=c.prepareStatement("SELECT * FROM avaliacao_web WHERE "+campo+"=? ORDER BY atualizado_em DESC")) {
            s.setObject(1,valor); try(ResultSet r=s.executeQuery()) {
                while(r.next()) lista.add(new Avaliacao(r.getLong("id_avaliacao"),r.getLong("id_filme"),r.getString("id_usuario"),r.getDouble("nota"),r.getString("comentario"),r.getString("criado_em"),r.getString("atualizado_em")));
            }
        } return lista;
    }
    void salvarAvaliacao(long filmeId,double nota,String comentario) throws Exception {
        sessao(); if(!Double.isFinite(nota) || nota<0 || nota>5 || Math.rint(nota*2)!=nota*2) throw new IOException("Nota: de 0 a 5 em intervalos de 0,5.");
        if(comentario==null || comentario.length()>1000) throw new IOException("Comentário: limite de 1000 caracteres.");
        executar("INSERT INTO avaliacao_web(id_filme,id_usuario,nota,comentario) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE nota=VALUES(nota),comentario=VALUES(comentario),atualizado_em=CURRENT_TIMESTAMP",filmeId,usuarioId,nota,comentario);
    }
    void excluirAvaliacao(long id) throws Exception { sessao(); executar("DELETE FROM avaliacao_web WHERE id_avaliacao=? AND id_usuario=?",id,usuarioId); }
    Map<String,String> nomesPublicos(List<Avaliacao> avaliacoes) {
        Map<String,String> nomes=new HashMap<>();
        try { sessao(); try(Connection c=conectar(); PreparedStatement s=c.prepareStatement("SELECT nome FROM usuario WHERE id_usuario=?")) {
            Set<String> ids=new HashSet<>(); for(Avaliacao a:avaliacoes) ids.add(a.usuarioId());
            for(String id:ids) { s.setString(1,id); try(ResultSet r=s.executeQuery()) { if(r.next()) nomes.put(id,r.getString(1)); } }
        }} catch(Exception ignored) {} return nomes;
    }
    private void executar(String sql,Object... valores) throws Exception {
        try(Connection c=conectar(); PreparedStatement s=c.prepareStatement(sql)) {
            for(int i=0;i<valores.length;i++) s.setObject(i+1,valores[i]); s.executeUpdate();
        }
    }
    static String hash(String senha) throws Exception {
        byte[] salt=new byte[16]; new SecureRandom().nextBytes(salt);
        return "210000:"+Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(derivar(senha,salt,210000));
    }
    private static byte[] derivar(String senha,byte[] salt,int iteracoes) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(senha.toCharArray(),salt,iteracoes,256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
    static boolean verificar(String senha,String hash) throws Exception {
        String[] p=hash.split(":"); return p.length==3 && MessageDigest.isEqual(Base64.getDecoder().decode(p[2]),derivar(senha,Base64.getDecoder().decode(p[1]),Integer.parseInt(p[0])));
    }
}
record Filme(long id,String titulo,int ano,int duracao,long generoId,String capa) {}
record Avaliacao(long id,long filmeId,String usuarioId,double nota,String comentario,String criadoEm,String atualizadoEm) {}

