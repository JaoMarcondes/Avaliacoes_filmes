import java.awt.*;
import java.net.URL;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

// Inicia o CineRate e controla a navegação entre as telas.

/** Ponto de entrada do CineRate: abre as telas e guarda os dados compartilhados. */
public class avaliacao_filmes extends JFrame {
    final Supabase dados = new Supabase();
    List<Filme> filmes = new ArrayList<>();
    Map<Long, String> generos = new LinkedHashMap<>();

    public avaliacao_filmes() {
        super("CineRate");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        URL logo = getClass().getResource("/assets/icone_cinerate.png");
        if (logo != null) setIconImage(new ImageIcon(logo).getImage());
        abrirLogin();
    }

    void abrirLogin() { abrir(new LoginView(this), "Login", false); }
    void abrirCadastro() { abrir(new CadastroView(this), "Criar conta", false); }
    void abrirCatalogo() { abrir(new CatalogoView(this), "Catálogo", true); }
    void abrirFilme(Filme filme) { abrir(new FilmeView(this, filme), filme.titulo(), true); }
    void abrirPerfil() { abrir(new PerfilView(this), "Meu Perfil", true); }
    void abrirCadastrarFilme() {
        if (!dados.administrador) {
            Estilo.aviso(this, "Esta área é exclusiva de administradores.");
            return;
        }
        abrir(new CadastrarFilmeView(this), "Cadastrar Filme", true);
    }
    void sair() {
        dados.sair();
        filmes = new ArrayList<>();
        generos = new LinkedHashMap<>();
        abrirLogin();
    }

    String nomeGenero(Filme filme) {
        return generos.getOrDefault(filme.generoId(), "Gênero não informado");
    }
    Filme encontrarFilme(long id) {
        for (Filme filme : filmes) if (filme.id() == id) return filme;
        return null;
    }

    private void abrir(JPanel tela, String titulo, boolean menu) {
        setTitle("CineRate - " + titulo);
        if (!menu) {
            setMinimumSize(new Dimension(475, 600));
            setSize(540, titulo.equals("Criar conta") ? 750 : 670);
            setContentPane(tela);
        } else {
            setMinimumSize(new Dimension(950, 650));
            if (getWidth() < 1120 || getHeight() < 720) setSize(1190, 780);
            JPanel base = Estilo.painel(new BorderLayout());
            base.add(menuLateral(titulo), BorderLayout.WEST);
            base.add(tela, BorderLayout.CENTER);
            setContentPane(base);
        }
        setLocationRelativeTo(null);
        revalidate();
        repaint();
    }

    private JPanel menuLateral(String pagina) {
        JPanel lateral = new JPanel(new BorderLayout());
        lateral.setBackground(Estilo.LATERAL);
        lateral.setPreferredSize(new Dimension(216, 720));
        JPanel itens = new JPanel();
        itens.setBackground(Estilo.LATERAL);
        itens.setLayout(new BoxLayout(itens, BoxLayout.Y_AXIS));
        itens.setBorder(new EmptyBorder(25, 12, 12, 12));
        JPanel marca = Estilo.transparente(new FlowLayout(FlowLayout.LEFT,6,0));
        URL urlLogo = getClass().getResource("/assets/icone_cinerate.png");
        if (urlLogo != null) {
            ImageIcon icon = new ImageIcon(new ImageIcon(urlLogo).getImage()
                    .getScaledInstance(28,28,Image.SCALE_SMOOTH));
            marca.add(new JLabel(icon));
        }
        marca.add(Estilo.texto("CineRate",20,true));
        marca.setMaximumSize(new Dimension(190,40));
        marca.setAlignmentX(Component.LEFT_ALIGNMENT);
        itens.add(marca);
        itens.add(Box.createVerticalStrut(32));
        adicionarMenu(itens, "⌂   Início", this::abrirCatalogo);
        adicionarMenu(itens, "♙   Meu Perfil", this::abrirPerfil);
        if (dados.administrador)
            adicionarMenu(itens, "+   Cadastrar Filme", this::abrirCadastrarFilme);
        itens.add(Box.createVerticalStrut(16));
        adicionarMenu(itens, "↪   Sair", this::sair);
        lateral.add(itens, BorderLayout.NORTH);
        JLabel rodape = Estilo.secundario("Java Swing  ·  Supabase");
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

