import java.awt.*;
import java.net.URL;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * CineRate: duas áreas de navegação, sem abrir uma janela para cada função.
 * Acesso reúne login e cadastro; o espaço principal reúne catálogo, filme e perfil.
 */
public class avaliacao_filmes extends JFrame {
    final Supabase dados = new Supabase();
    List<Filme> filmes = new ArrayList<>();
    Map<Long, String> generos = new LinkedHashMap<>();

    private CardLayout cartoesAcesso;
    private JPanel acesso;
    private JPanel formulariosAcesso;
    private CardLayout cartoesPrincipal;
    private JPanel conteudoPrincipal;
    private JPanel painelPrincipal;
    private JPanel telaFilme;
    private JPanel telaPerfil;

    public avaliacao_filmes() {
        super("CineRate");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        URL logo = getClass().getResource("/assets/icone_cinerate.png");
        if (logo != null) setIconImage(new ImageIcon(logo).getImage());
        abrirLogin();
    }

    /** Login e cadastro permanecem juntos em um único espaço de acesso. */
    void abrirLogin() {
        if (acesso == null) {
            cartoesAcesso = new CardLayout();
            formulariosAcesso = Estilo.painel(cartoesAcesso);
            formulariosAcesso.add(new LoginView(this), "login");
            formulariosAcesso.add(new CadastroView(this), "cadastro");

            acesso = Estilo.painel(new BorderLayout());
            JPanel abas = Estilo.transparente(new FlowLayout(FlowLayout.CENTER, 12, 12));
            JButton entrar = Estilo.botao("Entrar", false);
            JButton cadastrar = Estilo.botao("Criar conta", false);
            entrar.addActionListener(e -> abrirLogin());
            cadastrar.addActionListener(e -> abrirCadastro());
            abas.add(entrar);
            abas.add(cadastrar);
            acesso.add(abas, BorderLayout.NORTH);
            acesso.add(formulariosAcesso, BorderLayout.CENTER);

            setMinimumSize(new Dimension(475, 630));
            setSize(540, 770);
            setContentPane(acesso);
            setLocationRelativeTo(null);
        }
        mostrarAcesso("login", "Entrar");
    }

    void abrirCadastro() {
        if (acesso == null) abrirLogin();
        mostrarAcesso("cadastro", "Criar conta");
    }

    private void mostrarAcesso(String pagina, String titulo) {
        cartoesAcesso.show(formulariosAcesso, pagina);
        setTitle("CineRate - " + titulo);
        acesso.revalidate();
        acesso.repaint();
    }

    /** Catálogo, detalhes e perfil são seções de um mesmo painel principal. */
    private void iniciarPainel() {
        cartoesPrincipal = new CardLayout();
        conteudoPrincipal = Estilo.painel(cartoesPrincipal);
        conteudoPrincipal.add(new CatalogoView(this), "catalogo");

        painelPrincipal = Estilo.painel(new BorderLayout());
        painelPrincipal.add(menuLateral(), BorderLayout.WEST);
        painelPrincipal.add(conteudoPrincipal, BorderLayout.CENTER);
        setMinimumSize(new Dimension(950, 650));
        if (getWidth() < 1120 || getHeight() < 720) setSize(1190, 780);
        setContentPane(painelPrincipal);
        setLocationRelativeTo(null);
    }

    void abrirCatalogo() {
        if (painelPrincipal == null) iniciarPainel();
        cartoesPrincipal.show(conteudoPrincipal, "catalogo");
        setTitle("CineRate - Catálogo");
    }

    void abrirFilme(Filme filme) {
        if (filme == null) return;
        if (painelPrincipal == null) iniciarPainel();
        if (telaFilme != null) conteudoPrincipal.remove(telaFilme);
        telaFilme = new FilmeView(this, filme);
        conteudoPrincipal.add(telaFilme, "filme");
        cartoesPrincipal.show(conteudoPrincipal, "filme");
        setTitle("CineRate - " + filme.titulo());
    }

    void abrirPerfil() {
        if (painelPrincipal == null) iniciarPainel();
        // Recria somente a seção de perfil, para atualizar as avaliações ao voltar.
        if (telaPerfil != null) conteudoPrincipal.remove(telaPerfil);
        telaPerfil = new PerfilView(this);
        conteudoPrincipal.add(telaPerfil, "perfil");
        cartoesPrincipal.show(conteudoPrincipal, "perfil");
        setTitle("CineRate - Meu perfil");
    }

    void sair() {
        dados.sair();
        filmes = new ArrayList<>();
        generos = new LinkedHashMap<>();
        // Elimina formulários e telas da sessão anterior antes de voltar ao acesso.
        cartoesAcesso = null;
        acesso = null;
        formulariosAcesso = null;
        cartoesPrincipal = null;
        conteudoPrincipal = null;
        painelPrincipal = null;
        telaFilme = null;
        telaPerfil = null;
        abrirLogin();
    }

    String nomeGenero(Filme filme) {
        return generos.getOrDefault(filme.generoId(), "Gênero não informado");
    }

    Filme encontrarFilme(long id) {
        for (Filme filme : filmes) if (filme.id() == id) return filme;
        return null;
    }

    private JPanel menuLateral() {
        JPanel lateral = new JPanel(new BorderLayout());
        lateral.setBackground(Estilo.LATERAL);
        lateral.setPreferredSize(new Dimension(216, 720));
        JPanel itens = new JPanel();
        itens.setBackground(Estilo.LATERAL);
        itens.setLayout(new BoxLayout(itens, BoxLayout.Y_AXIS));
        itens.setBorder(new EmptyBorder(25, 12, 12, 12));
        JPanel marca = Estilo.transparente(new FlowLayout(FlowLayout.LEFT, 6, 0));
        URL urlLogo = getClass().getResource("/assets/icone_cinerate.png");
        if (urlLogo != null) {
            ImageIcon icon = new ImageIcon(new ImageIcon(urlLogo).getImage()
                    .getScaledInstance(28, 28, Image.SCALE_SMOOTH));
            marca.add(new JLabel(icon));
        }
        marca.add(Estilo.texto("CineRate", 20, true));
        marca.setMaximumSize(new Dimension(190, 40));
        marca.setAlignmentX(Component.LEFT_ALIGNMENT);
        itens.add(marca);
        itens.add(Box.createVerticalStrut(32));
        adicionarMenu(itens, "⌂   Catálogo", this::abrirCatalogo);
        adicionarMenu(itens, "♙   Meu perfil", this::abrirPerfil);
        itens.add(Box.createVerticalStrut(16));
        adicionarMenu(itens, "↪   Sair", this::sair);
        lateral.add(itens, BorderLayout.NORTH);
        JLabel rodape = Estilo.secundario("Java Swing  ·  MySQL local");
        rodape.setBorder(new EmptyBorder(8, 12, 18, 0));
        lateral.add(rodape, BorderLayout.SOUTH);
        return lateral;
    }

    private void adicionarMenu(JPanel lateral, String titulo, Runnable aoClicar) {
        JButton botao = Estilo.botao(titulo, false);
        botao.setHorizontalAlignment(SwingConstants.LEFT);
        botao.setMaximumSize(new Dimension(190, 44));
        botao.setAlignmentX(Component.LEFT_ALIGNMENT);
        botao.setBorder(new EmptyBorder(10, 12, 10, 10));
        botao.addActionListener(e -> aoClicar.run());
        lateral.add(botao);
        lateral.add(Box.createVerticalStrut(7));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
            new avaliacao_filmes().setVisible(true);
        });
    }
}


// Telas de acesso e perfil, reunidas à navegação principal.
// Telas de usuário: Login, Cadastro e Perfil.

// -------- Login --------
/** Tela de entrada. */
class LoginView extends JPanel {
    LoginView(avaliacao_filmes app) {
        super(new GridBagLayout());
        setBackground(Estilo.FUNDO);
        JPanel caixa = new JPanel();
        caixa.setLayout(new BoxLayout(caixa, BoxLayout.Y_AXIS));
        caixa.setBackground(Estilo.CARD);
        caixa.setBorder(new EmptyBorder(30, 35, 30, 35));
        caixa.setPreferredSize(new Dimension(405, 535));

        JLabel logo = Estilo.texto("▶", 60, true);
        java.net.URL resource = getClass().getResource("/assets/icone_cinerate.png");
        if (resource != null) {
            logo.setIcon(new ImageIcon(new ImageIcon(resource).getImage().getScaledInstance(82,82,Image.SCALE_SMOOTH)));
            logo.setText("");
        }
        logo.setForeground(Estilo.ROSA);
        logo.setAlignmentX(CENTER_ALIGNMENT);
        caixa.add(logo);
        caixa.add(Box.createVerticalStrut(12));
        JLabel titulo = Estilo.texto("CineRate", 30, true);
        titulo.setAlignmentX(CENTER_ALIGNMENT);
        caixa.add(titulo);
        caixa.add(Box.createVerticalStrut(6));
        JLabel sub = Estilo.secundario("Avalie. Descubra. Compartilhe.");
        sub.setAlignmentX(CENTER_ALIGNMENT);
        caixa.add(sub);
        caixa.add(Box.createVerticalStrut(28));

        JTextField email = Estilo.campo();
        JPasswordField senha = new JPasswordField();
        Estilo.estiloCampo(senha);
        email.setHorizontalAlignment(SwingConstants.CENTER);
        senha.setHorizontalAlignment(SwingConstants.CENTER);
        adicionarCampo(caixa,"E-mail", email);
        adicionarCampo(caixa,"Senha", senha);

        JButton entrar = Estilo.botao("Entrar", true);
        entrar.setMaximumSize(new Dimension(332,46));
        entrar.setAlignmentX(CENTER_ALIGNMENT);
        caixa.add(entrar);
        caixa.add(Box.createVerticalStrut(12));
        JLabel aviso = Estilo.secundario(" ");
        aviso.setForeground(Estilo.ERRO);
        aviso.setAlignmentX(CENTER_ALIGNMENT);
        caixa.add(aviso);
        caixa.add(Box.createVerticalStrut(13));
        JButton cadastro = Estilo.botao("Criar uma conta", false);
        cadastro.setMaximumSize(new Dimension(332,43));
        cadastro.setAlignmentX(CENTER_ALIGNMENT);
        caixa.add(cadastro);
        cadastro.addActionListener(e -> app.abrirCadastro());

        entrar.addActionListener(e -> {
            String mail = email.getText().trim();
            char[] pwd = senha.getPassword();
            if (mail.isBlank() || pwd.length == 0) { aviso.setText("Informe e-mail e senha."); return; }
            entrar.setEnabled(false);
            aviso.setText("Entrando...");
            Estilo.executar(() -> {
                try { app.dados.entrar(mail, new String(pwd)); return true; }
                finally { java.util.Arrays.fill(pwd, '\0'); }
            }, ok -> { senha.setText(""); app.abrirCatalogo(); }, erro -> {
                entrar.setEnabled(true);
                aviso.setText("Confira seus dados e tente novamente.");
                Estilo.erro(this, erro);
            });
        });
        senha.addActionListener(e -> entrar.doClick());
        add(caixa);
    }

    static void adicionarCampo(JPanel caixa, String rotulo, JTextField campo) {
        JLabel titulo = Estilo.texto(rotulo, 13, false);
        titulo.setAlignmentX(CENTER_ALIGNMENT);
        caixa.add(titulo);
        caixa.add(Box.createVerticalStrut(7));
        campo.setMaximumSize(new Dimension(332,44));
        campo.setAlignmentX(CENTER_ALIGNMENT);
        caixa.add(campo);
        caixa.add(Box.createVerticalStrut(17));
    }
}


// -------- Cadastro --------
/** Cadastro local com hash de senha PBKDF2. */
class CadastroView extends JPanel {
    CadastroView(avaliacao_filmes app) {
        super(new GridBagLayout());
        setBackground(Estilo.FUNDO);
        JPanel caixa = new JPanel();
        caixa.setLayout(new BoxLayout(caixa, BoxLayout.Y_AXIS));
        caixa.setBackground(Estilo.CARD);
        caixa.setBorder(new EmptyBorder(24, 33, 24, 33));
        caixa.setPreferredSize(new Dimension(410, 650));
        JLabel titulo = Estilo.texto("Criar sua conta", 28, true);
        titulo.setAlignmentX(CENTER_ALIGNMENT);
        caixa.add(titulo);
        caixa.add(Box.createVerticalStrut(5));
        JLabel subtitulo = Estilo.secundario("Junte-se à comunidade CineRate");
        subtitulo.setAlignmentX(CENTER_ALIGNMENT);
        caixa.add(subtitulo);
        caixa.add(Box.createVerticalStrut(28));

        JTextField nome = Estilo.campo();
        JTextField email = Estilo.campo();
        JPasswordField senha = new JPasswordField();
        JPasswordField confirmar = new JPasswordField();
        Estilo.estiloCampo(senha);
        Estilo.estiloCampo(confirmar);
        for (JTextField c : new JTextField[]{nome,email,senha,confirmar})
            c.setHorizontalAlignment(SwingConstants.CENTER);
        LoginView.adicionarCampo(caixa, "Nome completo", nome);
        LoginView.adicionarCampo(caixa, "E-mail", email);
        LoginView.adicionarCampo(caixa, "Senha", senha);
        LoginView.adicionarCampo(caixa, "Confirmar senha", confirmar);

        JCheckBox mostrar = new JCheckBox("Mostrar senhas");
        mostrar.setBackground(Estilo.CARD);
        mostrar.setForeground(Estilo.CINZA);
        mostrar.setAlignmentX(CENTER_ALIGNMENT);
        char eco = senha.getEchoChar();
        mostrar.addActionListener(e -> {
            senha.setEchoChar(mostrar.isSelected() ? (char)0 : eco);
            confirmar.setEchoChar(mostrar.isSelected() ? (char)0 : eco);
        });
        caixa.add(mostrar);
        caixa.add(Box.createVerticalStrut(13));
        JButton criar = Estilo.botao("Criar conta e entrar", true);
        criar.setMaximumSize(new Dimension(332,46));
        criar.setAlignmentX(CENTER_ALIGNMENT);
        caixa.add(criar);
        caixa.add(Box.createVerticalStrut(13));
        JLabel aviso = Estilo.secundario(" ");
        aviso.setForeground(Estilo.ERRO);
        aviso.setAlignmentX(CENTER_ALIGNMENT);
        caixa.add(aviso);
        caixa.add(Box.createVerticalStrut(12));
        JButton voltar = Estilo.botao("Já tenho uma conta — Entrar", false);
        voltar.addActionListener(e -> app.abrirLogin());
        voltar.setMaximumSize(new Dimension(332,40));
        voltar.setAlignmentX(CENTER_ALIGNMENT);
        caixa.add(voltar);

        criar.addActionListener(e -> {
            String fullName = nome.getText().trim();
            String mail = email.getText().trim();
            char[] pwd = senha.getPassword();
            char[] pwd2 = confirmar.getPassword();
            if (fullName.length() < 2 || fullName.length() > 100) {
                aviso.setText("Nome: entre 2 e 100 caracteres."); return;
            }
            if (!mail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                aviso.setText("Informe um e-mail válido."); return;
            }
            if (pwd.length < 6 || !java.util.Arrays.equals(pwd,pwd2)) {
                aviso.setText("Senha de 6+ caracteres; repita a mesma senha."); return;
            }
            criar.setEnabled(false);
            aviso.setText("Criando conta...");
            Estilo.executar(() -> {
                try { return app.dados.cadastrar(fullName,mail,new String(pwd)); }
                finally {
                    java.util.Arrays.fill(pwd,'\0');
                    java.util.Arrays.fill(pwd2,'\0');
                }
            }, entrou -> {
                app.abrirCatalogo();
            }, erro -> {
                criar.setEnabled(true);
                aviso.setText("Cadastro não concluído. Veja a mensagem.");
                Estilo.erro(this, erro);
            });
        });
        add(caixa);
    }
}


// -------- Perfil --------
/** Nome do usuário e histórico de filmes avaliados. */
class PerfilView extends JPanel {
    private final avaliacao_filmes app;
    private final JPanel historico = Estilo.vertical();
    private final JLabel situacao = Estilo.secundario("Carregando suas avaliações...");

    PerfilView(avaliacao_filmes app) {
        super(new BorderLayout(0,19));
        this.app = app;
        setBackground(Estilo.FUNDO);
        setBorder(new EmptyBorder(25,29,25,29));
        JPanel topo = Estilo.transparente(new BorderLayout(0,15));
        topo.add(Estilo.texto("Meu perfil",27,true),BorderLayout.NORTH);

        JPanel conta = new JPanel(new BorderLayout(15,0));
        conta.setBackground(Estilo.CARD);
        conta.setBorder(new EmptyBorder(17,18,17,18));
        JPanel campos = Estilo.transparente(new GridLayout(2,2,12,6));
        campos.add(Estilo.secundario("Seu nome"));
        campos.add(Estilo.secundario("E-mail da conta"));
        JTextField nome = Estilo.campo();
        nome.setText(app.dados.nome == null ? "" : app.dados.nome);
        JTextField email = Estilo.campo();
        email.setText(app.dados.email == null ? "" : app.dados.email);
        email.setEditable(false);
        campos.add(nome);
        campos.add(email);
        conta.add(campos,BorderLayout.CENTER);
        JButton salvarNome = Estilo.botao("Salvar nome",true);
        salvarNome.setPreferredSize(new Dimension(125,42));
        JPanel botaoNome = Estilo.transparente(new GridBagLayout());
        botaoNome.add(salvarNome);
        conta.add(botaoNome,BorderLayout.EAST);
        topo.add(conta,BorderLayout.CENTER);
        salvarNome.addActionListener(e -> {
            String valor = nome.getText().trim();
            if (valor.length() < 2 || valor.length() > 100) {
                Estilo.aviso(this,"O nome deve ter entre 2 e 100 caracteres."); return;
            }
            salvarNome.setEnabled(false);
            Estilo.executar(() -> { app.dados.alterarNome(valor); return true; },
                    ok -> { salvarNome.setEnabled(true); Estilo.aviso(this,"Nome atualizado!"); },
                    erro -> { salvarNome.setEnabled(true); Estilo.erro(this,erro); });
        });
        add(topo,BorderLayout.NORTH);

        JPanel centro = Estilo.transparente(new BorderLayout(0,12));
        JPanel titulo = Estilo.transparente(new BorderLayout());
        titulo.add(Estilo.texto("Minhas avaliações",21,true),BorderLayout.WEST);
        titulo.add(situacao,BorderLayout.EAST);
        centro.add(titulo,BorderLayout.NORTH);
        centro.add(Estilo.rolagem(historico),BorderLayout.CENTER);
        add(centro,BorderLayout.CENTER);
        carregar();
    }

    private void carregar() {
        Estilo.executar(() -> app.dados.minhasAvaliacoes(), lista -> {
            historico.removeAll();
            situacao.setText(lista.size() + " filme(s) avaliado(s)");
            if (lista.isEmpty()) historico.add(Estilo.secundario("Você ainda não avaliou nenhum filme."));
            for (Avaliacao avaliacao : lista) {
                historico.add(cartao(avaliacao));
                historico.add(Box.createVerticalStrut(12));
            }
            historico.revalidate(); historico.repaint();
        }, erro -> { situacao.setText("Falha ao carregar"); Estilo.erro(this,erro); });
    }

    private JPanel cartao(Avaliacao a) {
        Filme filme = app.encontrarFilme(a.filmeId());
        JPanel item = new JPanel(new BorderLayout(15,0));
        item.setBackground(Estilo.CARD);
        item.setBorder(new EmptyBorder(13,14,13,14));
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE,142));
        if (filme != null) item.add(Imagens.capa(filme,74,107),BorderLayout.WEST);

        JPanel meio = Estilo.vertical();
        meio.add(Estilo.texto(filme == null ? "Filme #" + a.filmeId() : filme.titulo(),16,true));
        meio.add(Box.createVerticalStrut(6));
        JPanel notas = Estilo.transparente(new FlowLayout(FlowLayout.LEFT,7,0));
        Estrelas stars = new Estrelas(false);
        stars.setValor(a.nota());
        notas.add(stars);
        JLabel score = Estilo.secundario(String.format(Locale.forLanguageTag("pt-BR"),"%.1f/5",a.nota()));
        score.setForeground(Estilo.OURO);
        notas.add(score);
        meio.add(notas);
        meio.add(Box.createVerticalStrut(7));
        JTextArea comentario = Estilo.area(2);
        comentario.setOpaque(false);
        comentario.setEditable(false);
        comentario.setFocusable(false);
        comentario.setText(a.comentario().isBlank() ? "Sem comentário" : a.comentario());
        meio.add(comentario);
        for (Component componente : meio.getComponents()) {
            if (componente instanceof JComponent c) c.setAlignmentX(LEFT_ALIGNMENT);
        }
        item.add(meio,BorderLayout.CENTER);

        JPanel opcoes = Estilo.vertical();
        JButton editar = Estilo.botao("Editar",false);
        editar.setMaximumSize(new Dimension(90,34));
        editar.setEnabled(filme != null);
        editar.addActionListener(e -> app.abrirFilme(filme));
        JButton excluir = Estilo.botao("Excluir",false);
        excluir.setMaximumSize(new Dimension(90,34));
        excluir.addActionListener(e -> excluir(a));
        opcoes.add(editar);
        opcoes.add(Box.createVerticalStrut(8));
        opcoes.add(excluir);
        item.add(opcoes,BorderLayout.EAST);
        return item;
    }

    private void excluir(Avaliacao a) {
        if (JOptionPane.showConfirmDialog(this,"Excluir a avaliação deste filme?",
                "CineRate",JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        Estilo.executar(() -> { app.dados.excluirAvaliacao(a.id()); return true; },
                ok -> carregar(), erro -> Estilo.erro(this,erro));
    }
}


