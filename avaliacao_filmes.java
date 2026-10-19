import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CineRate - telas 1 e 3: login e catálogo de filmes.
 * Java 17+ / Swing / Supabase Auth + REST / Storage público.
 *
 * Usa filmes e imagens JÁ CADASTRADOS no Supabase:
 *   public.filme(id_filme,titulo,ano_lancamento,duracao,id_genero,imagem_url)
 *   public.genero(id_genero,nome_genero)
 *   Storage bucket capas-filmes (se imagem_url guardar apenas nome/caminho).
 *
 * A chave publishable é pública; NUNCA inclua secret/service_role em um app.
 */
public class avaliacao_filmes extends JFrame {
    private static final String SUPABASE_URL = "https://pfqubgfnjtxsocwtmzrm.supabase.co";
    private static final String SUPABASE_KEY = "sb_publishable_155R5aCPgq2Rv24Y_NHwEQ_YAeUgs2D";
    private static final String BUCKET = "capas-filmes";

    private static final Color BG = new Color(13, 18, 29);
    private static final Color SIDEBAR = new Color(19, 27, 41);
    private static final Color CARD = new Color(24, 32, 47);
    private static final Color INPUT = new Color(17, 25, 39);
    private static final Color HOVER = new Color(37, 49, 71);
    private static final Color ACCENT = new Color(236, 43, 83);
    private static final Color WHITE = new Color(245, 247, 252);
    private static final Color MUTED = new Color(169, 183, 203);
    private static final Color GOLD = new Color(255, 194, 77);
    private static final int POSTER_W = 174;
    private static final int POSTER_H = 235;
    private static final int CARD_W = 205;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<String, ImageIcon> posterCache = new ConcurrentHashMap<>();
    private final Set<String> failedPosters = ConcurrentHashMap.newKeySet();
    private final Map<String, List<JLabel>> pendingPosters = new HashMap<>(); // somente EDT
    private List<Filme> filmes = new ArrayList<>();
    private Map<Long, String> generos = new LinkedHashMap<>();
    private String accessToken;
    private String userEmail;
    private long catalogRequestVersion = 0;
    private boolean catalogLoaded = false;
    private JLabel catalogStatus;
    private JButton reloadButton;

    public avaliacao_filmes() {
        super("CineRate - Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(510, 625);
        setMinimumSize(new Dimension(470, 590));
        setLocationRelativeTo(null);
        ImageIcon logo = brandIcon(32, 32);
        if (logo != null) setIconImage(logo.getImage());
        showLogin();
    }

    private static Font font(int size, boolean bold) {
        return new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size);
    }

    private static JPanel plain(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    private static JLabel text(String value, int size, boolean bold, Color color) {
        JLabel l = new JLabel(value);
        l.setFont(font(size, bold));
        l.setForeground(color);
        return l;
    }

    private static JButton button(String label, boolean primary) {
        JButton b = new JButton(label);
        b.setFont(font(13, true));
        b.setForeground(WHITE);
        b.setBackground(primary ? ACCENT : HOVER);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private ImageIcon brandIcon(int width, int height) {
        java.net.URL resource = getClass().getResource("/assets/icone_cinerate.png");
        if (resource == null) return null;
        return new ImageIcon(new ImageIcon(resource).getImage()
                .getScaledInstance(width, height, Image.SCALE_SMOOTH));
    }

    private void replaceContent(JComponent next) {
        setContentPane(next);
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    // =============== TELA 1: LOGIN (mesma identidade do login anterior) ===============
    private void showLogin() {
        setTitle("CineRate - Login");
        setMinimumSize(new Dimension(470, 590));
        setSize(510, 625);
        setLocationRelativeTo(null);

        JPanel screen = new JPanel(new GridBagLayout());
        screen.setBackground(BG);
        JPanel card = new JPanel();
        card.setBackground(CARD);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(24, 38, 26, 38));
        card.setPreferredSize(new Dimension(405, 530));

        JLabel logo = new JLabel("▶", SwingConstants.CENTER);
        ImageIcon icon = brandIcon(90, 90);
        if (icon != null) { logo.setText(""); logo.setIcon(icon); }
        else { logo.setForeground(ACCENT); logo.setFont(font(50, true)); }
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        logo.setMaximumSize(new Dimension(320, 96));
        card.add(logo);
        card.add(Box.createVerticalStrut(7));
        card.add(centered("CineRate", 33, true, WHITE, 43));
        card.add(Box.createVerticalStrut(5));
        card.add(centered("Avalie. Descubra. Compartilhe.", 12, false, MUTED, 19));
        card.add(Box.createVerticalStrut(27));

        JTextField email = loginInput();
        email.setText("teste@admin.com");
        JPasswordField senha = new JPasswordField();
        styleLoginInput(senha);
        card.add(centered("E-mail", 13, false, WHITE, 21));
        card.add(Box.createVerticalStrut(7));
        card.add(email);
        card.add(Box.createVerticalStrut(18));
        card.add(centered("Senha", 13, false, WHITE, 21));
        card.add(Box.createVerticalStrut(7));
        card.add(senha);
        card.add(Box.createVerticalStrut(24));

        JButton entrar = button("Entrar", true);
        sizeForm(entrar, 44);
        card.add(entrar);
        card.add(Box.createVerticalStrut(12));

        JLabel message = centered(" ", 11, false, new Color(255, 174, 182), 33);
        card.add(message);
        card.add(Box.createVerticalStrut(8));
        JButton cadastrar = button("Criar uma conta", false);
        sizeForm(cadastrar, 40);
        cadastrar.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Vamos desenvolver a tela de cadastro na próxima etapa.",
                "CineRate", JOptionPane.INFORMATION_MESSAGE));
        card.add(cadastrar);

        entrar.addActionListener(e -> attemptLogin(email.getText().trim(), senha, entrar, message));
        senha.addActionListener(e -> attemptLogin(email.getText().trim(), senha, entrar, message));
        screen.add(card);
        replaceContent(screen);
        SwingUtilities.invokeLater(senha::requestFocusInWindow);
    }

    private JLabel centered(String value, int size, boolean bold, Color color, int height) {
        JLabel l = text(value, size, bold, color);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        l.setMaximumSize(new Dimension(320, height));
        l.setPreferredSize(new Dimension(320, height));
        return l;
    }

    private void sizeForm(JComponent c, int height) {
        c.setPreferredSize(new Dimension(320, height));
        c.setMaximumSize(new Dimension(320, height));
        c.setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    private JTextField loginInput() {
        JTextField t = new JTextField();
        styleLoginInput(t);
        return t;
    }

    private void styleLoginInput(JTextField t) {
        sizeForm(t, 43);
        t.setHorizontalAlignment(SwingConstants.CENTER);
        t.setBackground(INPUT);
        t.setForeground(WHITE);
        t.setCaretColor(WHITE);
        t.setFont(font(14, false));
        t.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(65, 78, 101)),
                new EmptyBorder(8, 12, 8, 12)));
    }

    private void attemptLogin(String email, JPasswordField passwordField,
                              JButton trigger, JLabel message) {
        char[] password = passwordField.getPassword();
        if (email.isBlank() || password.length == 0) {
            Arrays.fill(password, '\0');
            message.setText("Preencha e-mail e senha.");
            return;
        }
        trigger.setEnabled(false);
        trigger.setText("Entrando...");
        message.setText(" ");

        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception {
                try {
                    String payload = Json.encode(Map.of("email", email, "password", new String(password)));
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(SUPABASE_URL + "/auth/v1/token?grant_type=password"))
                            .timeout(Duration.ofSeconds(22))
                            .header("apikey", SUPABASE_KEY)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                            .build();
                    HttpResponse<String> response = http.send(req,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (response.statusCode() / 100 != 2) {
                        String raw = response.body();
                        if (raw.contains("email_not_confirmed") || raw.contains("Email not confirmed"))
                            throw new IOException("E-mail não confirmado no Supabase Auth.");
                        if (response.statusCode() == 400 || response.statusCode() == 401)
                            throw new IOException("E-mail ou senha inválidos.");
                        if (response.statusCode() == 429)
                            throw new IOException("Muitas tentativas. Aguarde um pouco.");
                        throw new IOException("Falha no login (HTTP " + response.statusCode() + ").");
                    }
                    String token = asString(object(Json.parse(response.body())).get("access_token"));
                    if (token.isBlank()) throw new IOException("Login sem token de acesso.");
                    return token;
                } finally {
                    Arrays.fill(password, '\0');
                }
            }
            @Override protected void done() {
                trigger.setText("Entrar");
                trigger.setEnabled(true);
                try {
                    accessToken = get();
                    catalogLoaded = false;
                    filmes.clear();
                    generos.clear();
                    userEmail = email;
                    passwordField.setText("");
                    showCatalog();
                } catch (Exception ex) {
                    String why = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    message.setText(why != null && why.contains("timed out")
                            ? "Conexão demorou demais. Tente de novo." : safeMessage(why));
                }
            }
        }.execute();
    }

    // =============== TELA 3: CATÁLOGO ==================
    private JPanel sideMenu(String current) {
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(SIDEBAR);
        side.setPreferredSize(new Dimension(220, 700));

        JPanel list = new JPanel();
        list.setBackground(SIDEBAR);
        list.setBorder(new EmptyBorder(25, 13, 10, 13));
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

        JPanel brand = plain(new FlowLayout(FlowLayout.LEFT, 9, 3));
        ImageIcon img = brandIcon(32, 32);
        JLabel mark = new JLabel(img != null ? img : new ImageIcon());
        if (img == null) mark.setText("▶");
        brand.add(mark);
        brand.add(text("CineRate", 20, true, WHITE));
        brand.setMaximumSize(new Dimension(195, 48));
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        list.add(brand);
        list.add(Box.createVerticalStrut(38));

        sideButton(list, "⌂   Início", "catalogo", current, this::showCatalog);
        sideButton(list, "♙   Meu Perfil", "perfil", current,
                () -> showUpcoming("Meu Perfil", "Aqui ficarão suas avaliações e dados de perfil.", "perfil"));
        sideButton(list, "+   Sugerir Filme", "sugestao", current,
                () -> showUpcoming("Sugerir Filme", "Aqui você poderá sugerir um filme que ainda não está no catálogo.", "sugestao"));
        list.add(Box.createVerticalStrut(20));
        sideButton(list, "↪   Sair", "sair", current, () -> {
            accessToken = null; userEmail = null;
            filmes.clear(); generos.clear(); catalogLoaded = false;
            catalogRequestVersion++;
            showLogin();
        });
        side.add(list, BorderLayout.NORTH);
        JLabel footer = text("Java Swing  ·  Supabase", 11, false, MUTED);
        footer.setBorder(new EmptyBorder(10, 15, 20, 5));
        side.add(footer, BorderLayout.SOUTH);
        return side;
    }

    private void sideButton(JPanel parent, String title, String section,
                            String current, Runnable action) {
        boolean active = section.equals(current);
        JButton b = button(title, active);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setFont(font(13, active));
        b.setBorder(new EmptyBorder(12, 14, 12, 8));
        b.setPreferredSize(new Dimension(190, 45));
        b.setMinimumSize(new Dimension(190, 45));
        b.setMaximumSize(new Dimension(190, 45));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(b);
        parent.add(Box.createVerticalStrut(7));
        b.addActionListener(e -> action.run());
    }

    private JPanel mainLayout(String section, JComponent content) {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(BG);
        container.add(sideMenu(section), BorderLayout.WEST);
        container.add(content, BorderLayout.CENTER);
        return container;
    }

    private void prepareAppWindow(String title) {
        setTitle("CineRate - " + title);
        setMinimumSize(new Dimension(930, 620));
        if (getWidth() < 1100 || getHeight() < 740) {
            setSize(1190, 780);
            setLocationRelativeTo(null);
        }
    }

    private void showCatalog() {
        if (accessToken == null) { showLogin(); return; }
        prepareAppWindow("Catálogo de Filmes");

        JPanel main = new JPanel(new BorderLayout(0, 20));
        main.setBackground(BG);
        main.setBorder(new EmptyBorder(26, 26, 24, 26));

        JPanel top = plain(new BorderLayout(0, 18));
        JPanel row = plain(new BorderLayout(15, 0));
        JTextField search = new JTextField() {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setColor(MUTED);
                    g.setFont(font(13, false));
                    Insets ins = getInsets();
                    g.drawString("⌕  Pesquisar filmes...", ins.left, (getHeight() + g.getFontMetrics().getAscent()) / 2 - 3);
                    g.dispose();
                }
            }
        };
        search.setToolTipText("Digite o título para pesquisar");
        search.setFont(font(13, false));
        search.setForeground(WHITE);
        search.setCaretColor(WHITE);
        search.setBackground(INPUT);
        search.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(56, 70, 92)),
                new EmptyBorder(11, 13, 11, 13)));
        row.add(search, BorderLayout.CENTER);

        JComboBox<String> genreFilter = new JComboBox<>();
        genreFilter.setForeground(WHITE);
        genreFilter.setBackground(CARD);
        genreFilter.setFont(font(12, false));
        genreFilter.setPreferredSize(new Dimension(162, 43));
        row.add(genreFilter, BorderLayout.EAST);
        top.add(row, BorderLayout.NORTH);
        JPanel headings = plain(new BorderLayout());
        headings.add(text("Filmes em destaque", 23, true, WHITE), BorderLayout.WEST);
        JPanel statusBox = plain(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        catalogStatus = text("", 12, false, MUTED);
        reloadButton = button("↻ Atualizar", false);
        reloadButton.setPreferredSize(new Dimension(116, 32));
        reloadButton.addActionListener(e -> loadCatalog());
        statusBox.add(catalogStatus);
        statusBox.add(reloadButton);
        headings.add(statusBox, BorderLayout.EAST);
        top.add(headings, BorderLayout.SOUTH);
        main.add(top, BorderLayout.NORTH);

        JPanel grid = plain(new GridLayout(0, 3, 16, 22));
        JPanel holder = plain(new BorderLayout());
        holder.setBorder(new EmptyBorder(0, 1, 15, 1));
        holder.add(grid, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        main.add(scroll, BorderLayout.CENTER);

        Runnable draw = () -> {
            grid.removeAll();
            String q = search.getText().toLowerCase(Locale.ROOT).trim();
            String selected = (String) genreFilter.getSelectedItem();
            int quantity = 0;
            for (Filme movie : filmes) {
                String gn = generos.getOrDefault(movie.genreId(), "Gênero não informado");
                if (!movie.title().toLowerCase(Locale.ROOT).contains(q)) continue;
                if (selected != null && !selected.equals("Todos os gêneros") && !selected.equals(gn)) continue;
                grid.add(movieCard(movie, gn));
                quantity++;
            }
            if (quantity == 0) {
                JLabel empty = text(filmes.isEmpty() ? "Nenhum filme carregado." : "Nenhum filme encontrado com esses filtros.",
                        15, false, MUTED);
                empty.setBorder(new EmptyBorder(30, 10, 30, 0));
                grid.add(empty);
            }
            if (catalogLoaded) catalogStatus.setText(quantity + " filme(s)");
            grid.revalidate(); grid.repaint();
        };

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { draw.run(); }
            public void removeUpdate(DocumentEvent e) { draw.run(); }
            public void changedUpdate(DocumentEvent e) { draw.run(); }
        });
        // Evita eventos de seleção no meio da atualização dos itens.
        // O modelo é preparado de uma vez para não disparar renderizações com estado parcial.
        final boolean[] updatingGenre = { false };
        genreFilter.addActionListener(e -> {
            if (!updatingGenre[0]) draw.run();
        });

        Runnable updateFilters = () -> {
            updatingGenre[0] = true;
            try {
                String selected = (String) genreFilter.getSelectedItem();
                java.util.List<String> options = new ArrayList<>();
                options.add("Todos os gêneros");
                options.addAll(new TreeSet<>(generos.values()));
                genreFilter.setModel(new DefaultComboBoxModel<>(options.toArray(new String[0])));
                if (selected != null && options.contains(selected)) genreFilter.setSelectedItem(selected);
                else genreFilter.setSelectedIndex(0);
            } finally {
                updatingGenre[0] = false;
            }
            draw.run();
        };

        holder.addComponentListener(new ComponentAdapter() {
            int previousColumns = 0;
            public void componentResized(ComponentEvent e) {
                int columns = Math.max(2, holder.getWidth() / (CARD_W + 16));
                if (columns != previousColumns) {
                    previousColumns = columns;
                    grid.setLayout(new GridLayout(0, columns, 16, 22));
                    grid.revalidate();
                }
            }
        });

        replaceContent(mainLayout("catalogo", main));
        if (!catalogLoaded) {
            reloadButton.setEnabled(false);
            catalogStatus.setText("Carregando filmes...");
            loadCatalog(() -> updateFilters.run());
        } else updateFilters.run();
    }

    private JPanel movieCard(Filme movie, String genreName) {
        JPanel card = new JPanel(new BorderLayout(0, 11));
        card.setBackground(CARD);
        card.setBorder(new EmptyBorder(11, 12, 13, 12));
        card.setPreferredSize(new Dimension(CARD_W, 349));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel cover = poster(movie, POSTER_W, POSTER_H);
        cover.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cover.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) { showMovie(movie); }
        });
        JPanel coverWrap = plain(new FlowLayout(FlowLayout.CENTER, 0, 0));
        coverWrap.add(cover);
        card.add(coverWrap, BorderLayout.NORTH);

        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JLabel title = text(cut(movie.title(), 23), 13, true, WHITE);
        title.setToolTipText(movie.title());
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(title);
        info.add(Box.createVerticalStrut(5));
        String year = movie.year() > 0 ? String.valueOf(movie.year()) : "Ano n/d";
        JLabel meta = text(year + "  ·  " + cut(genreName, 20), 11, false, MUTED);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(meta);
        info.add(Box.createVerticalStrut(4));
        String duration = movie.duration() > 0 ? movie.duration() + " min" : "Duração n/d";
        JLabel dur = text(duration, 11, false, MUTED);
        dur.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(dur);
        info.add(Box.createVerticalStrut(5));
        JButton toMovie = new JButton("★  Ver filme →");
        toMovie.setFont(font(12, true));
        toMovie.setForeground(GOLD);
        toMovie.setContentAreaFilled(false);
        toMovie.setFocusPainted(false);
        toMovie.setBorderPainted(false);
        toMovie.setHorizontalAlignment(SwingConstants.LEFT);
        toMovie.setBorder(new EmptyBorder(0, 0, 0, 0));
        toMovie.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toMovie.addActionListener(e -> showMovie(movie));
        toMovie.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(toMovie);
        card.add(info, BorderLayout.CENTER);

        card.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) { showMovie(movie); }
        });
        return card;
    }

    private JLabel poster(Filme movie, int width, int height) {
        JLabel target = new JLabel("CAPA DO FILME", SwingConstants.CENTER);
        target.setBackground(INPUT);
        target.setForeground(MUTED);
        target.setFont(font(12, true));
        target.setOpaque(true);
        target.setPreferredSize(new Dimension(width, height));
        target.setMinimumSize(new Dimension(width, height));
        target.setMaximumSize(new Dimension(width, height));

        String url = movie.imageUrl();
        if (url.isBlank()) { target.setText("Sem imagem"); return target; }
        String cacheKey = url + "#" + width + "x" + height;
        ImageIcon cached = posterCache.get(cacheKey);
        if (cached != null) { target.setIcon(cached); target.setText(""); return target; }
        if (failedPosters.contains(cacheKey)) { target.setText("Capa indisponível"); return target; }
        List<JLabel> existing = pendingPosters.get(cacheKey);
        if (existing != null) { existing.add(target); return target; }
        List<JLabel> labels = new ArrayList<>(); labels.add(target);
        pendingPosters.put(cacheKey, labels);

        new SwingWorker<ImageIcon, Void>() {
            @Override protected ImageIcon doInBackground() throws Exception {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(18)).GET().build();
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200)
                    throw new IOException("Storage HTTP " + response.statusCode());
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(response.body()));
                if (img == null) throw new IOException("Formato de imagem não suportado");
                // Corte central para ocupar toda a capa sem distorcer a imagem.
                double scale = Math.max((double) width / img.getWidth(), (double) height / img.getHeight());
                int newW = (int) Math.ceil(img.getWidth() * scale);
                int newH = (int) Math.ceil(img.getHeight() * scale);
                BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = resized.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(img, (width - newW) / 2, (height - newH) / 2, newW, newH, null);
                g.dispose();
                return new ImageIcon(resized);
            }
            @Override protected void done() {
                List<JLabel> waiting = pendingPosters.remove(cacheKey);
                if (waiting == null) return;
                try {
                    ImageIcon icon = get(); posterCache.put(cacheKey, icon);
                    for (JLabel target : waiting) { target.setIcon(icon); target.setText(""); }
                } catch (Exception ex) {
                    failedPosters.add(cacheKey);
                    for (JLabel target : waiting) {
                        target.setText("Capa indisponível");
                        target.setToolTipText("Confira a URL no campo imagem_url e se o bucket é público.");
                    }
                }
            }
        }.execute();
        return target;
    }

    private void showMovie(Filme movie) {
        prepareAppWindow(movie.title());
        JPanel main = new JPanel(new BorderLayout(24, 20));
        main.setBackground(BG);
        main.setBorder(new EmptyBorder(36, 34, 40, 34));
        JButton back = button("← Voltar ao catálogo", false);
        back.addActionListener(e -> showCatalog());
        JPanel upper = plain(new FlowLayout(FlowLayout.LEFT));
        upper.add(back);
        main.add(upper, BorderLayout.NORTH);
        JPanel info = plain(new BorderLayout(30, 10));
        info.add(poster(movie, 250, 354), BorderLayout.WEST);
        JPanel desc = new JPanel(); desc.setOpaque(false);
        desc.setLayout(new BoxLayout(desc, BoxLayout.Y_AXIS));
        desc.add(text(movie.title(), 30, true, WHITE));
        desc.add(Box.createVerticalStrut(14));
        desc.add(text((movie.year() > 0 ? movie.year() : "Ano n/d") + "   ·   "
                + (movie.duration() > 0 ? movie.duration() + " min" : "Duração n/d"), 16, false, MUTED));
        desc.add(Box.createVerticalStrut(7));
        desc.add(text(generos.getOrDefault(movie.genreId(), "Gênero não informado"), 15, false, MUTED));
        desc.add(Box.createVerticalStrut(35));
        desc.add(text("Pronto para avaliar?", 19, true, WHITE));
        desc.add(Box.createVerticalStrut(12));
        JLabel explanation = text("Na próxima etapa, vamos ligar esta tela às avaliações.", 13, false, MUTED);
        desc.add(explanation);
        desc.add(Box.createVerticalStrut(18));
        JButton rate = button("Avaliar este filme (próxima etapa)", true);
        rate.setMaximumSize(new Dimension(330, 46));
        rate.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "A tela de avaliação será implementada na próxima etapa.", "CineRate", JOptionPane.INFORMATION_MESSAGE));
        desc.add(rate);
        info.add(desc, BorderLayout.CENTER);
        main.add(info, BorderLayout.CENTER);
        replaceContent(mainLayout("catalogo", main));
    }

    private void showUpcoming(String title, String description, String current) {
        prepareAppWindow(title);
        JPanel main = new JPanel(new GridBagLayout());
        main.setBackground(BG);
        JPanel contents = new JPanel();
        contents.setOpaque(false);
        contents.setLayout(new BoxLayout(contents, BoxLayout.Y_AXIS));
        contents.add(centered(title, 30, true, WHITE, 50));
        contents.add(Box.createVerticalStrut(14));
        contents.add(centered(description, 13, false, MUTED, 32));
        contents.add(Box.createVerticalStrut(24));
        JButton back = button("Voltar ao catálogo", true);
        back.setAlignmentX(Component.CENTER_ALIGNMENT);
        back.setMaximumSize(new Dimension(245, 43));
        back.addActionListener(e -> showCatalog());
        contents.add(back);
        main.add(contents);
        replaceContent(mainLayout(current, main));
    }

    // =============== REST/SUPABASE (somente consulta) ===============
    private record Filme(long id, String title, int year, int duration, long genreId, String imageUrl) { }
    private record CatalogData(List<Filme> movies, Map<Long, String> genres, String warning) { }

    private void loadCatalog() { loadCatalog(null); }
    private void loadCatalog(Runnable callback) {
        if (accessToken == null) return;
        long requestId = ++catalogRequestVersion;
        if (reloadButton != null) reloadButton.setEnabled(false);
        if (catalogStatus != null) catalogStatus.setText("Buscando filmes...");
        new SwingWorker<CatalogData, Void>() {
            @Override protected CatalogData doInBackground() throws Exception {
                // Importante: consulta as tabelas já existentes; não altera o banco nem o bucket.
                List<Object> result = array(Json.parse(getApi(
                        "/rest/v1/filme?select=id_filme,titulo,ano_lancamento,duracao,id_genero,imagem_url&order=titulo.asc")));
                List<Filme> list = new ArrayList<>();
                for (Object o : result) {
                    Map<String, Object> row = object(o);
                    list.add(new Filme(toLong(row.get("id_filme")), asString(row.get("titulo")),
                            (int) toLong(row.get("ano_lancamento")), (int) toLong(row.get("duracao")),
                            toLong(row.get("id_genero")), coverUrl(asString(row.get("imagem_url")))));
                }
                Map<Long, String> gs = new LinkedHashMap<>();
                String warn = null;
                try {
                    List<Object> rows = array(Json.parse(getApi(
                            "/rest/v1/genero?select=id_genero,nome_genero&order=nome_genero.asc")));
                    for (Object o : rows) {
                        Map<String, Object> r = object(o);
                        gs.put(toLong(r.get("id_genero")), asString(r.get("nome_genero")));
                    }
                } catch (Exception ex) {
                    warn = "Gêneros não puderam ser consultados; confira permissões de leitura.";
                }
                return new CatalogData(list, gs, warn);
            }
            @Override protected void done() {
                if (requestId != catalogRequestVersion || accessToken == null) return;
                if (reloadButton != null) reloadButton.setEnabled(true);
                CatalogData result;
                try {
                    result = get();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    cause.printStackTrace(); // Mostra o local exato do erro no terminal.
                    if (catalogStatus != null) catalogStatus.setText("Falha na consulta");
                    JOptionPane.showMessageDialog(avaliacao_filmes.this,
                            "Falha ao consultar os filmes no Supabase.\n\n" + safeMessage(cause.getMessage())
                            + "\n\nSe aparecer HTTP 401/403, verifique as políticas RLS de leitura."
                            + "\nSe aparecer HTTP 400, confira os nomes das colunas.",
                            "CineRate - Erro de consulta", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                filmes = result.movies();
                generos = result.genres();
                catalogLoaded = true;
                try {
                    if (callback != null) callback.run();
                    else showCatalog();
                } catch (Exception ex) {
                    ex.printStackTrace(); // Falha de UI não é erro de consulta ao banco.
                    if (catalogStatus != null) catalogStatus.setText("Erro na exibição");
                    JOptionPane.showMessageDialog(avaliacao_filmes.this,
                            "Os dados foram carregados, mas ocorreu um erro ao montar o catálogo.\n\n"
                                    + safeMessage(ex.getMessage()),
                            "CineRate - Erro de interface", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (filmes.isEmpty()) {
                    if (catalogStatus != null) catalogStatus.setText("0 filmes visíveis");
                    JOptionPane.showMessageDialog(avaliacao_filmes.this,
                            "A consulta funcionou, mas não retornou filmes para esta conta.\n"
                            + "Se existem filmes no Table Editor, confira a política SELECT (RLS)\n"
                            + "da tabela public.filme para usuários autenticados.",
                            "CineRate - Nenhum filme visível", JOptionPane.INFORMATION_MESSAGE);
                }
                if (result.warning() != null) JOptionPane.showMessageDialog(
                        avaliacao_filmes.this, result.warning(), "CineRate", JOptionPane.WARNING_MESSAGE);
            }
        }.execute();
    }

    private String getApi(String route) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(SUPABASE_URL + route))
                .timeout(Duration.ofSeconds(20))
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .header("Accept-Profile", "public")
                .GET().build();
        HttpResponse<String> res = http.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            String detail = res.body();
            try {
                Map<String, Object> obj = object(Json.parse(detail));
                detail = asString(obj.getOrDefault("message", obj.getOrDefault("hint", detail)));
            } catch (Exception ignored) { }
            if (res.statusCode() == 401) throw new IOException("Sessão expirada ou sem permissão (HTTP 401).");
            throw new IOException("HTTP " + res.statusCode() + ": " + cut(detail, 200));
        }
        return res.body();
    }

    private static String coverUrl(String stored) {
        String value = stored == null ? "" : stored.trim();
        if (value.isEmpty()) return "";
        // Valor completo do Supabase Storage ou outra hospedagem.
        if (value.startsWith("https://") || value.startsWith("http://"))
            return value.replace(" ", "%20");
        // Caminho /storage/v1/object/public/... já salvo no banco.
        if (value.startsWith("/storage/v1/")) return SUPABASE_URL + value.replace(" ", "%20");
        // Aceita nome da imagem, subpasta ou caminho que comece pelo nome do bucket.
        if (value.startsWith(BUCKET + "/")) value = value.substring(BUCKET.length() + 1);
        StringJoiner encoded = new StringJoiner("/");
        for (String part : value.split("/")) {
            if (!part.isEmpty()) encoded.add(URLEncoder.encode(part, StandardCharsets.UTF_8)
                    .replace("+", "%20"));
        }
        return SUPABASE_URL + "/storage/v1/object/public/" + BUCKET + "/" + encoded;
    }

    private static String asString(Object v) { return v == null ? "" : String.valueOf(v); }
    private static long toLong(Object v) {
        if (v == null) return 0;
        try { return v instanceof Number ? ((Number) v).longValue() : Long.parseLong(asString(v)); }
        catch (Exception ex) { return 0; }
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : Map.of();
    }
    @SuppressWarnings("unchecked")
    private static List<Object> array(Object v) {
        return v instanceof List ? (List<Object>) v : List.of();
    }
    private static String cut(String str, int len) {
        if (str == null || str.length() <= len) return str == null ? "" : str;
        return str.substring(0, len - 1) + "…";
    }
    private static String safeMessage(String message) {
        return message == null ? "Verifique a conexão com a internet." : cut(message, 220);
    }

    // JSON sem dependências externas (Java 17).
    private static final class Json {
        static Object parse(String json) {
            Parser p = new Parser(json);
            Object value = p.value(); p.ws();
            if (p.pos != p.s.length()) throw new IllegalArgumentException("JSON inválido");
            return value;
        }
        static String encode(Object value) {
            if (value == null) return "null";
            if (value instanceof String s) {
                StringBuilder out = new StringBuilder("\"");
                for (char c : s.toCharArray()) {
                    switch (c) {
                        case '"' -> out.append("\\\"");
                        case '\\' -> out.append("\\\\");
                        case '\n' -> out.append("\\n");
                        case '\r' -> out.append("\\r");
                        case '\t' -> out.append("\\t");
                        default -> { if (c < 32) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
                    }
                }
                return out.append('"').toString();
            }
            if (value instanceof Number || value instanceof Boolean) return value.toString();
            if (value instanceof Map<?, ?> map) {
                StringJoiner out = new StringJoiner(",", "{", "}");
                map.forEach((k,v) -> out.add(encode(String.valueOf(k)) + ":" + encode(v)));
                return out.toString();
            }
            if (value instanceof List<?> list) {
                StringJoiner out = new StringJoiner(",", "[", "]");
                list.forEach(v -> out.add(encode(v)));
                return out.toString();
            }
            throw new IllegalArgumentException("JSON não suportado");
        }
        private static class Parser {
            final String s; int pos;
            Parser(String s) { this.s = s; }
            void ws() { while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++; }
            char current() { ws(); return pos < s.length() ? s.charAt(pos) : '\0'; }
            void expect(char c) {
                if (current() != c) throw new IllegalArgumentException("JSON inválido");
                pos++;
            }
            Object value() {
                char c = current();
                if (c == '{') return object();
                if (c == '[') return array();
                if (c == '"') return string();
                if (s.startsWith("true", pos)) { pos += 4; return true; }
                if (s.startsWith("false", pos)) { pos += 5; return false; }
                if (s.startsWith("null", pos)) { pos += 4; return null; }
                if (c == '-' || Character.isDigit(c)) return number();
                throw new IllegalArgumentException("JSON inválido na posição " + pos);
            }
            Map<String,Object> object() {
                Map<String,Object> map = new LinkedHashMap<>(); expect('{');
                if (current() == '}') { pos++; return map; }
                while (true) {
                    String k = string(); expect(':'); map.put(k, value());
                    if (current() == '}') { pos++; break; }
                    expect(',');
                }
                return map;
            }
            List<Object> array() {
                List<Object> list = new ArrayList<>(); expect('[');
                if (current() == ']') { pos++; return list; }
                while (true) {
                    list.add(value());
                    if (current() == ']') { pos++; break; }
                    expect(',');
                }
                return list;
            }
            String string() {
                expect('"'); StringBuilder out = new StringBuilder();
                while (pos < s.length()) {
                    char c = s.charAt(pos++);
                    if (c == '"') return out.toString();
                    if (c != '\\') { out.append(c); continue; }
                    if (pos == s.length()) throw new IllegalArgumentException("Escape JSON inválido");
                    char escaped = s.charAt(pos++);
                    switch (escaped) {
                        case '"' -> out.append('"'); case '\\' -> out.append('\\');
                        case '/' -> out.append('/'); case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f'); case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r'); case 't' -> out.append('\t');
                        case 'u' -> { out.append((char) Integer.parseInt(s.substring(pos, pos+4),16)); pos+=4; }
                        default -> throw new IllegalArgumentException("Escape inválido");
                    }
                }
                throw new IllegalArgumentException("String JSON não terminada");
            }
            Number number() {
                int begin = pos;
                if (s.charAt(pos) == '-') pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                if (pos < s.length() && s.charAt(pos) == '.') {
                    pos++; while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                }
                if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                    pos++; if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                    while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                }
                String val = s.substring(begin, pos);
                if (val.contains(".") || val.contains("e") || val.contains("E")) return Double.valueOf(val);
                return Long.valueOf(val);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
            new avaliacao_filmes().setVisible(true);
        });
    }
}
