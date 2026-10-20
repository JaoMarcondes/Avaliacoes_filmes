import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
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
 * CineRate - catálogo e menu originais, com avaliação interativa de 0 a 5 estrelas
 * (meia estrela) e persistência no Supabase.
 *
 * Java 17+; sem bibliotecas externas.
 * Supabase Auth + tabelas filme, genero, avaliacao_web + Storage capas-filmes.
 *
 * Execute avaliacoes.sql no Supabase antes de usar as avaliações.
 * NUNCA coloque uma chave service_role/secret no código cliente.
 */
public class avaliacao_filmes extends JFrame {
    private static final String SUPABASE_URL = "https://pfqubgfnjtxsocwtmzrm.supabase.co";
    private static final String SUPABASE_KEY = "sb_publishable_155R5aCPgq2Rv24Y_NHwEQ_YAeUgs2D";
    private static final String BUCKET = "capas-filmes";
    private static final String TABLE = "avaliacao_web";

    private static final Color BG = new Color(13, 18, 29);
    private static final Color SIDEBAR = new Color(19, 27, 41);
    private static final Color CARD = new Color(24, 32, 47);
    private static final Color INPUT = new Color(17, 25, 39);
    private static final Color HOVER = new Color(37, 49, 71);
    private static final Color ACCENT = new Color(236, 43, 83);
    private static final Color WHITE = new Color(245, 247, 252);
    private static final Color MUTED = new Color(169, 183, 203);
    private static final Color GOLD = new Color(255, 194, 77);
    private static final Color ERROR = new Color(255, 154, 171);
    private static final int POSTER_W = 174;
    private static final int POSTER_H = 235;
    private static final int CARD_W = 205;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<String, ImageIcon> posterCache = new ConcurrentHashMap<>();
    // A versão original evitava disparar vários downloads da mesma capa.
    private final Set<String> failedPosters = ConcurrentHashMap.newKeySet();
    private final Map<String, List<JLabel>> pendingPosters = new HashMap<>();
    private volatile String accessToken;
    private volatile String refreshToken;
    private volatile String userId;
    private volatile String userEmail;
    private volatile long expiresAtMillis;
    private List<Movie> movies = new ArrayList<>();
    private Map<Long, String> genres = new LinkedHashMap<>();
    private boolean catalogLoaded = false;
    private int catalogVersion = 0;
    private int reviewVersion = 0;
    private long currentMovieId = -1;
    private String currentPage = "login";

    private record Movie(long id, String title, int year, int duration, long genreId, String image) { }
    private record Review(long id, long movieId, String userId, double rating,
                          String comment, String createdAt, String updatedAt) { }
    private record CatalogResult(List<Movie> movies, Map<Long, String> genres) { }

    public avaliacao_filmes() {
        super("CineRate - Login");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(515, 650);
        setMinimumSize(new Dimension(470, 600));
        setLocationRelativeTo(null);
        ImageIcon icon = brandIcon(34);
        if (icon != null) setIconImage(icon.getImage());
        showLogin();
    }

    private static Font font(int size, boolean bold) {
        return new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size);
    }

    private static JLabel text(String value, int size, boolean bold, Color color) {
        JLabel label = new JLabel(value);
        label.setFont(font(size, bold));
        label.setForeground(color);
        return label;
    }

    private static JPanel transparent() {
        return transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
    }

    private static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static JButton button(String title, boolean primary) {
        JButton btn = new JButton(title);
        btn.setBackground(primary ? ACCENT : HOVER);
        btn.setForeground(WHITE);
        btn.setFont(font(13, true));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private static void styleInput(JTextField field) {
        field.setBackground(INPUT);
        field.setForeground(WHITE);
        field.setCaretColor(WHITE);
        field.setFont(font(14, false));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(65, 78, 101)),
                new EmptyBorder(10, 12, 10, 12)));
    }

    private ImageIcon brandIcon(int size) {
        try {
            java.net.URL resource = getClass().getResource("/assets/icone_cinerate.png");
            if (resource == null) return null;
            return new ImageIcon(new ImageIcon(resource).getImage()
                    .getScaledInstance(size, size, Image.SCALE_SMOOTH));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void showScreen(String page, JComponent component) {
        currentPage = page;
        setContentPane(component);
        revalidate();
        repaint();
    }

    private void prepareAppWindow(String title) {
        setTitle("CineRate - " + title);
        setMinimumSize(new Dimension(930, 620));
        if (getWidth() < 1100 || getHeight() < 740) {
            setSize(1190, 780);
            setLocationRelativeTo(null);
        }
    }

    // --------------------------- LOGIN ----------------------------------
    private void showLogin() {
        setTitle("CineRate - Login");
        setMinimumSize(new Dimension(470, 600));
        setSize(515, 650);
        setLocationRelativeTo(null);
        JPanel screen = new JPanel(new GridBagLayout());
        screen.setBackground(BG);
        JPanel form = new JPanel();
        form.setBackground(CARD);
        form.setPreferredSize(new Dimension(410, 540));
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(30, 35, 25, 35));

        JLabel logo = text("▶", 60, true, ACCENT);
        ImageIcon icon = brandIcon(86);
        if (icon != null) { logo.setIcon(icon); logo.setText(""); }
        logo.setHorizontalAlignment(SwingConstants.CENTER);
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        logo.setMaximumSize(new Dimension(320, 98));
        form.add(logo);
        form.add(Box.createVerticalStrut(7));
        JLabel title = text("CineRate", 33, true, WHITE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        form.add(title);
        form.add(Box.createVerticalStrut(7));
        JLabel subtitle = text("Avalie. Descubra. Compartilhe.", 12, false, MUTED);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        form.add(subtitle);
        form.add(Box.createVerticalStrut(30));

        JLabel emailLabel = text("E-mail", 13, false, WHITE);
        emailLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        form.add(emailLabel);
        form.add(Box.createVerticalStrut(8));
        JTextField email = new JTextField();
        styleInput(email);
        email.setHorizontalAlignment(SwingConstants.CENTER);
        email.setMaximumSize(new Dimension(330, 44));
        form.add(email);
        form.add(Box.createVerticalStrut(17));
        JLabel passLabel = text("Senha", 13, false, WHITE);
        passLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        form.add(passLabel);
        form.add(Box.createVerticalStrut(8));
        JPasswordField password = new JPasswordField();
        styleInput(password);
        password.setHorizontalAlignment(SwingConstants.CENTER);
        password.setMaximumSize(new Dimension(330, 44));
        form.add(password);
        form.add(Box.createVerticalStrut(22));

        JButton signIn = button("Entrar", true);
        signIn.setAlignmentX(Component.CENTER_ALIGNMENT);
        signIn.setMaximumSize(new Dimension(330, 46));
        form.add(signIn);
        form.add(Box.createVerticalStrut(13));
        JLabel feedback = text(" ", 11, false, ERROR);
        feedback.setAlignmentX(Component.CENTER_ALIGNMENT);
        form.add(feedback);
        form.add(Box.createVerticalStrut(17));
        JButton createAccount = button("Criar uma conta", false);
        createAccount.setAlignmentX(Component.CENTER_ALIGNMENT);
        createAccount.setMaximumSize(new Dimension(330, 40));
        createAccount.addActionListener(e -> message("O cadastro será implementado na próxima etapa."));
        form.add(createAccount);

        Runnable enter = () -> {
            String mail = email.getText().trim();
            char[] pwd = password.getPassword();
            if (mail.isEmpty() || pwd.length == 0) {
                Arrays.fill(pwd, '\0');
                feedback.setText("Preencha e-mail e senha.");
                return;
            }
            signIn.setEnabled(false);
            signIn.setText("Entrando...");
            feedback.setText(" ");
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    try {
                        login(mail, new String(pwd));
                    } finally { Arrays.fill(pwd, '\0'); }
                    return null;
                }
                @Override protected void done() {
                    signIn.setText("Entrar");
                    signIn.setEnabled(true);
                    try {
                        get();
                        password.setText("");
                        catalogLoaded = false;
                        movies.clear();
                        genres.clear();
                        showCatalog();
                    } catch (Exception ex) { feedback.setText(shortError(ex)); }
                }
            }.execute();
        };
        signIn.addActionListener(e -> enter.run());
        password.addActionListener(e -> enter.run());
        screen.add(form);
        showScreen("login", screen);
    }

    // --------------------------- MENU ORIGINAL ---------------------------
    private JPanel appLayout(String section, JComponent content) {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(BG);
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(SIDEBAR);
        side.setPreferredSize(new Dimension(220, 700));

        JPanel list = new JPanel();
        list.setBackground(SIDEBAR);
        list.setBorder(new EmptyBorder(25, 13, 10, 13));
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JPanel brand = transparent(new FlowLayout(FlowLayout.LEFT, 9, 3));
        ImageIcon logo = brandIcon(32);
        if (logo != null) brand.add(new JLabel(logo));
        else brand.add(text("▶", 25, true, ACCENT));
        brand.add(text("CineRate", 20, true, WHITE));
        brand.setMaximumSize(new Dimension(195, 48));
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        list.add(brand);
        list.add(Box.createVerticalStrut(38));
        menuButton(list, "⌂   Início", "catalogo", section, this::showCatalog);
        menuButton(list, "♙   Meu Perfil", "perfil", section, this::showProfile);
        menuButton(list, "+   Sugerir Filme", "sugestao", section, () -> showUpcoming(
                "Sugerir Filme", "Aqui você poderá sugerir um filme que ainda não está no catálogo.", "sugestao"));
        list.add(Box.createVerticalStrut(20));
        menuButton(list, "↪   Sair", "sair", section, this::logout);
        side.add(list, BorderLayout.NORTH);
        JLabel footer = text("Java Swing  ·  Supabase", 11, false, MUTED);
        footer.setBorder(new EmptyBorder(10, 15, 20, 5));
        side.add(footer, BorderLayout.SOUTH);
        container.add(side, BorderLayout.WEST);
        container.add(content, BorderLayout.CENTER);
        return container;
    }

    private void menuButton(JPanel parent, String title, String section, String current, Runnable action) {
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

    private void logout() {
        accessToken = null;
        refreshToken = null;
        userId = null;
        userEmail = null;
        catalogLoaded = false;
        movies.clear();
        genres.clear();
        currentMovieId = -1;
        catalogVersion++;
        reviewVersion++;
        showLogin();
    }

    // --------------------------- CATÁLOGO ORIGINAL -----------------------
    private void showCatalog() {
        if (accessToken == null) { showLogin(); return; }
        currentMovieId = -1;
        ++reviewVersion;
        prepareAppWindow("Catálogo de Filmes");

        JPanel main = new JPanel(new BorderLayout(0, 20));
        main.setBackground(BG);
        main.setBorder(new EmptyBorder(26, 26, 24, 26));

        JPanel top = transparent(new BorderLayout(0, 18));
        JPanel row = transparent(new BorderLayout(15, 0));
        JTextField search = new JTextField() {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setColor(MUTED);
                    g.setFont(font(13, false));
                    Insets ins = getInsets();
                    g.drawString("⌕  Pesquisar filmes...", ins.left,
                            (getHeight() + g.getFontMetrics().getAscent()) / 2 - 3);
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
        JPanel headings = transparent(new BorderLayout());
        headings.add(text("Filmes em destaque", 23, true, WHITE), BorderLayout.WEST);
        JPanel statusBox = transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JLabel catalogStatus = text("", 12, false, MUTED);
        JButton reloadButton = button("↻ Atualizar", false);
        reloadButton.setPreferredSize(new Dimension(116, 32));
        statusBox.add(catalogStatus);
        statusBox.add(reloadButton);
        headings.add(statusBox, BorderLayout.EAST);
        top.add(headings, BorderLayout.SOUTH);
        main.add(top, BorderLayout.NORTH);

        JPanel grid = transparent(new GridLayout(0, 3, 16, 22));
        JPanel holder = transparent(new BorderLayout());
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
            for (Movie movie : movies) {
                String genre = genres.getOrDefault(movie.genreId(), "Gênero não informado");
                if (!movie.title().toLowerCase(Locale.ROOT).contains(q)) continue;
                if (selected != null && !selected.equals("Todos os gêneros") && !selected.equals(genre)) continue;
                grid.add(movieCard(movie, genre));
                quantity++;
            }
            if (quantity == 0) {
                JLabel empty = text(movies.isEmpty() ? "Nenhum filme carregado."
                        : "Nenhum filme encontrado com esses filtros.", 15, false, MUTED);
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
        final boolean[] updatingGenre = {false};
        genreFilter.addActionListener(e -> {
            if (!updatingGenre[0]) draw.run();
        });
        Runnable updateFilters = () -> {
            updatingGenre[0] = true;
            try {
                String selected = (String) genreFilter.getSelectedItem();
                java.util.List<String> options = new ArrayList<>();
                options.add("Todos os gêneros");
                options.addAll(new TreeSet<>(genres.values()));
                genreFilter.setModel(new DefaultComboBoxModel<>(options.toArray(new String[0])));
                if (selected != null && options.contains(selected)) genreFilter.setSelectedItem(selected);
                else genreFilter.setSelectedIndex(0);
            } finally { updatingGenre[0] = false; }
            draw.run();
        };
        holder.addComponentListener(new ComponentAdapter() {
            int previousColumns = 0;
            @Override public void componentResized(ComponentEvent e) {
                int columns = Math.max(2, holder.getWidth() / (CARD_W + 16));
                if (columns != previousColumns) {
                    previousColumns = columns;
                    grid.setLayout(new GridLayout(0, columns, 16, 22));
                    grid.revalidate();
                }
            }
        });
        reloadButton.addActionListener(e -> loadCatalog(reloadButton, catalogStatus, updateFilters));
        showScreen("catalogo", appLayout("catalogo", main));
        if (!catalogLoaded) {
            reloadButton.setEnabled(false);
            catalogStatus.setText("Carregando filmes...");
            loadCatalog(reloadButton, catalogStatus, updateFilters);
        } else updateFilters.run();
    }

    private JPanel movieCard(Movie movie, String genreName) {
        JPanel card = new JPanel(new BorderLayout(0, 11));
        card.setBackground(CARD);
        card.setBorder(new EmptyBorder(11, 12, 13, 12));
        card.setPreferredSize(new Dimension(CARD_W, 349));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JLabel cover = poster(movie, POSTER_W, POSTER_H);
        cover.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cover.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { showMovie(movie); }
        });
        JPanel coverWrap = transparent(new FlowLayout(FlowLayout.CENTER, 0, 0));
        coverWrap.add(cover);
        card.add(coverWrap, BorderLayout.NORTH);
        JPanel info = transparent();
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
        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { showMovie(movie); }
        });
        return card;
    }

    private void loadCatalog(JButton reload, JLabel status, Runnable onLoaded) {
        int version = ++catalogVersion;
        reload.setEnabled(false);
        status.setText("Buscando filmes...");
        new SwingWorker<CatalogResult, Void>() {
            @Override protected CatalogResult doInBackground() throws Exception {
                List<Object> records = asList(api("GET", "/rest/v1/filme?select=id_filme,titulo,ano_lancamento,duracao,id_genero,imagem_url&order=titulo.asc", null));
                List<Movie> list = new ArrayList<>();
                for (Object item : records) {
                    Map<String, Object> r = asMap(item);
                    list.add(new Movie(number(r.get("id_filme")).longValue(), str(r.get("titulo")),
                            number(r.get("ano_lancamento")).intValue(), number(r.get("duracao")).intValue(),
                            number(r.get("id_genero")).longValue(), str(r.get("imagem_url"))));
                }
                Map<Long, String> gen = new LinkedHashMap<>();
                try {
                    List<Object> genreRecords = asList(api("GET", "/rest/v1/genero?select=id_genero,nome_genero&order=nome_genero.asc", null));
                    for (Object item : genreRecords) {
                        Map<String, Object> r = asMap(item);
                        gen.put(number(r.get("id_genero")).longValue(), str(r.get("nome_genero")));
                    }
                } catch (Exception ex) {
                    // O catálogo deve continuar acessível mesmo sem permissão para gêneros.
                }
                return new CatalogResult(list, gen);
            }
            @Override protected void done() {
                if (version != catalogVersion || !"catalogo".equals(currentPage)) return;
                reload.setEnabled(true);
                try {
                    CatalogResult result = get();
                    movies = result.movies;
                    genres = result.genres;
                    catalogLoaded = true;
                    onLoaded.run();
                } catch (Exception ex) {
                    status.setText("Falha na consulta");
                    error("Não foi possível carregar os filmes.\n\n" + shortError(ex)
                            + "\nConfira as políticas SELECT/RLS de filme e genero.");
                }
            }
        }.execute();
    }

    // --------------------------- TELA DE FILME + AVALIAÇÃO ----------------
    private void showMovie(Movie movie) {
        if (accessToken == null) { showLogin(); return; }
        currentMovieId = movie.id;
        int version = ++reviewVersion;
        prepareAppWindow(movie.title);
        JPanel main = new JPanel(new BorderLayout(0, 20));
        main.setBackground(BG);
        main.setBorder(new EmptyBorder(25, 28, 26, 28));
        JButton back = button("← Voltar ao catálogo", false);
        back.addActionListener(e -> showCatalog());
        JPanel nav = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        nav.add(back);
        main.add(nav, BorderLayout.NORTH);

        JPanel contents = transparent(new BorderLayout(24, 0));
        JPanel left = transparent();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel cover = poster(movie, 230, 324);
        cover.setAlignmentX(Component.LEFT_ALIGNMENT);
        left.add(cover);
        left.add(Box.createVerticalStrut(20));
        JLabel genre = text(genres.getOrDefault(movie.genreId, "Gênero não informado"), 13, false, MUTED);
        left.add(genre);
        contents.add(left, BorderLayout.WEST);

        JPanel right = transparent();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        JLabel heading = text(movie.title, 27, true, WHITE);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        right.add(heading);
        right.add(Box.createVerticalStrut(8));
        right.add(text((movie.year > 0 ? movie.year : "Ano n/d") + "  ·  "
                + (movie.duration > 0 ? movie.duration + " min" : "Duração n/d"), 13, false, MUTED));
        right.add(Box.createVerticalStrut(18));
        JLabel average = text("★  Carregando avaliações...", 15, true, GOLD);
        average.setAlignmentX(Component.LEFT_ALIGNMENT);
        right.add(average);
        right.add(Box.createVerticalStrut(24));

        JPanel editor = new JPanel(new GridBagLayout());
        editor.setBackground(CARD);
        editor.setBorder(new EmptyBorder(18, 19, 18, 19));
        editor.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints editorConstraints = new GridBagConstraints();
        editorConstraints.gridx = 0;
        editorConstraints.weightx = 1;
        editorConstraints.fill = GridBagConstraints.HORIZONTAL;
        editorConstraints.anchor = GridBagConstraints.WEST;
        editorConstraints.insets = new Insets(0, 0, 0, 0);

        JLabel editorTitle = text("Sua avaliação", 19, true, WHITE);
        editorConstraints.gridy = 0;
        editorConstraints.insets = new Insets(0, 0, 12, 0);
        editor.add(editorTitle, editorConstraints);

        // Estrelas + valor na mesma linha, alinhados à esquerda.
        JPanel grade = transparent(new FlowLayout(FlowLayout.LEFT, 12, 0));
        StarRating stars = new StarRating(true);
        stars.setValue(0);
        grade.add(stars);
        JLabel gradeText = text("0 / 5", 14, true, GOLD);
        grade.add(gradeText);
        stars.setOnChanged(value -> gradeText.setText(ratingText(value) + " / 5"));
        editorConstraints.gridy = 1;
        editorConstraints.insets = new Insets(0, 0, 6, 0);
        editor.add(grade, editorConstraints);

        // Botão 0 estrelas logo abaixo das estrelas, sem deslocar à direita.
        JPanel zeroRow = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JButton zero = button("Limpar estrelas (0)", false);
        zero.setFont(font(11, false));
        zero.setPreferredSize(new Dimension(148, 28));
        zero.addActionListener(e -> stars.setValue(0));
        zeroRow.add(zero);
        editorConstraints.gridy = 2;
        editorConstraints.insets = new Insets(0, 0, 16, 0);
        editor.add(zeroRow, editorConstraints);

        JLabel commentTitle = text("Sua opinião sobre o filme (opcional, até 1000 caracteres)", 12, false, MUTED);
        editorConstraints.gridy = 3;
        editorConstraints.insets = new Insets(0, 0, 7, 0);
        editor.add(commentTitle, editorConstraints);
        JTextArea comment = new JTextArea(4, 32);
        comment.setLineWrap(true);
        comment.setWrapStyleWord(true);
        comment.setFont(font(13, false));
        comment.setBackground(INPUT);
        comment.setForeground(WHITE);
        comment.setCaretColor(WHITE);
        comment.setBorder(new EmptyBorder(10, 10, 10, 10));
        JScrollPane commentScroll = new JScrollPane(comment);
        commentScroll.setPreferredSize(new Dimension(420, 108));
        commentScroll.setBorder(BorderFactory.createLineBorder(new Color(65, 78, 101)));
        editorConstraints.gridy = 4;
        editorConstraints.insets = new Insets(0, 0, 12, 0);
        editor.add(commentScroll, editorConstraints);

        JPanel actions = transparent(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JButton save = button("Publicar avaliação", true);
        JButton delete = button("Excluir minha avaliação", false);
        delete.setVisible(false);
        actions.add(save);
        actions.add(delete);
        editorConstraints.gridy = 5;
        editorConstraints.insets = new Insets(0, 0, 7, 0);
        editor.add(actions, editorConstraints);
        JLabel feedback = text("", 12, false, ERROR);
        editorConstraints.gridy = 6;
        editorConstraints.insets = new Insets(0, 0, 0, 0);
        editor.add(feedback, editorConstraints);
        right.add(editor);
        right.add(Box.createVerticalStrut(20));
        JLabel reviewTitle = text("Avaliações da comunidade", 18, true, WHITE);
        reviewTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        right.add(reviewTitle);
        right.add(Box.createVerticalStrut(10));
        JPanel reviewList = transparent();
        reviewList.setLayout(new BoxLayout(reviewList, BoxLayout.Y_AXIS));
        reviewList.add(text("Carregando comentários...", 13, false, MUTED));
        right.add(reviewList);
        contents.add(right, BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(contents);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setUnitIncrement(22);
        main.add(scroll, BorderLayout.CENTER);
        showScreen("filme", appLayout("catalogo", main));

        final int[] latestReviewRequest = {0};
        Runnable reloadReviews = () -> loadReviews(movie, version, ++latestReviewRequest[0],
                latestReviewRequest, stars, comment, save, delete, editorTitle, average,
                reviewList, feedback);
        save.addActionListener(e -> {
            double chosenRating = stars.getValue();
            String content = comment.getText().trim();
            if (content.length() > 1000) {
                feedback.setText("O comentário deve ter até 1000 caracteres.");
                return;
            }
            // Nota zero também é permitida.
            save.setEnabled(false);
            delete.setEnabled(false);
            feedback.setForeground(MUTED);
            feedback.setText("Salvando sua avaliação...");
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    saveReview(movie.id, chosenRating, content);
                    return null;
                }
                @Override protected void done() {
                    if (version != reviewVersion || currentMovieId != movie.id) return;
                    save.setEnabled(true);
                    delete.setEnabled(true);
                    try {
                        get();
                        feedback.setForeground(GOLD);
                        feedback.setText("Avaliação salva com sucesso!");
                        reloadReviews.run();
                    } catch (Exception ex) {
                        feedback.setForeground(ERROR);
                        feedback.setText(shortError(ex));
                    }
                }
            }.execute();
        });
        delete.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this,
                    "Deseja excluir sua avaliação deste filme?", "CineRate",
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            save.setEnabled(false);
            delete.setEnabled(false);
            feedback.setText("Excluindo...");
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception {
                    deleteReview(movie.id);
                    return null;
                }
                @Override protected void done() {
                    if (version != reviewVersion || currentMovieId != movie.id) return;
                    save.setEnabled(true);
                    delete.setEnabled(true);
                    try {
                        get();
                        stars.setValue(0);
                        comment.setText("");
                        feedback.setForeground(GOLD);
                        feedback.setText("Avaliação excluída.");
                        reloadReviews.run();
                    } catch (Exception ex) {
                        feedback.setForeground(ERROR);
                        feedback.setText(shortError(ex));
                    }
                }
            }.execute();
        });
        reloadReviews.run();
    }

    private void loadReviews(Movie movie, int version, int requestNumber, int[] latestRequest,
                             StarRating stars, JTextArea comment, JButton save, JButton delete,
                             JLabel editorTitle, JLabel average, JPanel list, JLabel feedback) {
        new SwingWorker<List<Review>, Void>() {
            @Override protected List<Review> doInBackground() throws Exception {
                String route = "/rest/v1/" + TABLE + "?select=id_avaliacao,id_filme,id_usuario,nota,comentario,criado_em,atualizado_em"
                        + "&id_filme=eq." + movie.id + "&order=atualizado_em.desc";
                return reviewRows(api("GET", route, null));
            }
            @Override protected void done() {
                if (version != reviewVersion || requestNumber != latestRequest[0]
                        || currentMovieId != movie.id || !"filme".equals(currentPage)) return;
                try {
                    List<Review> reviews = get();
                    list.removeAll();
                    Review mine = null;
                    double sum = 0;
                    for (Review review : reviews) {
                        sum += review.rating;
                        if (review.userId.equals(userId)) mine = review;
                        list.add(reviewCard(review));
                        list.add(Box.createVerticalStrut(10));
                    }
                    if (reviews.isEmpty()) list.add(text("Seja a primeira pessoa a avaliar!", 13, false, MUTED));
                    if (!reviews.isEmpty()) {
                        average.setText("★  " + String.format(Locale.forLanguageTag("pt-BR"), "%.1f", sum / reviews.size())
                                + " / 5  ·  " + reviews.size() + " avaliação(ões)");
                    } else average.setText("★  Ainda sem avaliações");
                    delete.setVisible(mine != null);
                    save.setText(mine == null ? "Publicar avaliação" : "Salvar alterações");
                    editorTitle.setText(mine == null ? "Sua avaliação" : "Editar minha avaliação");
                    // Não apagar alterações em andamento após um salvamento.
                    if (mine != null && save.isEnabled()) {
                        stars.setValue(mine.rating);
                        comment.setText(mine.comment);
                    }
                    list.revalidate();
                    list.repaint();
                } catch (Exception ex) {
                    average.setText("★  Avaliações indisponíveis");
                    list.removeAll();
                    list.add(text("Erro ao consultar avaliações: " + shortError(ex), 12, false, ERROR));
                    list.revalidate();
                    list.repaint();
                    feedback.setForeground(ERROR);
                    feedback.setText("Execute avaliacoes.sql no Supabase se ainda não o fez.");
                }
            }
        }.execute();
    }

    private JPanel reviewCard(Review review) {
        JPanel box = new JPanel(new BorderLayout(0, 7));
        box.setBackground(CARD);
        box.setBorder(new EmptyBorder(12, 14, 12, 14));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.setMaximumSize(new Dimension(Integer.MAX_VALUE, 105));
        box.setPreferredSize(new Dimension(420, 92));
        JPanel heading = transparent(new FlowLayout(FlowLayout.LEFT, 11, 0));
        heading.add(text(review.userId.equals(userId) ? "Você" : "Usuário", 13, true, WHITE));
        StarRating display = new StarRating(false);
        display.setValue(review.rating);
        heading.add(display);
        heading.add(text(ratingText(review.rating) + "/5", 12, true, GOLD));
        box.add(heading, BorderLayout.NORTH);
        JTextArea body = new JTextArea(review.comment.isBlank() ? "(Sem comentário)" : review.comment);
        body.setEditable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setOpaque(false);
        body.setForeground(MUTED);
        body.setFont(font(12, false));
        body.setBorder(null);
        box.add(body, BorderLayout.CENTER);
        return box;
    }

    // --------------------------- PERFIL ----------------------------------
    private void showProfile() {
        if (accessToken == null) { showLogin(); return; }
        prepareAppWindow("Meu Perfil");
        currentMovieId = -1;
        int version = ++reviewVersion;
        JPanel main = new JPanel(new BorderLayout(0, 17));
        main.setBackground(BG);
        main.setBorder(new EmptyBorder(28, 30, 25, 30));
        JPanel head = transparent(new GridLayout(2, 1, 0, 8));
        head.add(text("Minhas avaliações", 26, true, WHITE));
        head.add(text(userEmail == null ? "" : userEmail, 13, false, MUTED));
        main.add(head, BorderLayout.NORTH);
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.add(text("Buscando suas avaliações...", 13, false, MUTED));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(BG);
        main.add(scroll, BorderLayout.CENTER);
        showScreen("perfil", appLayout("perfil", main));
        new SwingWorker<List<Review>, Void>() {
            @Override protected List<Review> doInBackground() throws Exception {
                String route = "/rest/v1/" + TABLE + "?select=id_avaliacao,id_filme,id_usuario,nota,comentario,criado_em,atualizado_em"
                        + "&id_usuario=eq." + userId + "&order=atualizado_em.desc";
                return reviewRows(api("GET", route, null));
            }
            @Override protected void done() {
                if (version != reviewVersion || !"perfil".equals(currentPage)) return;
                list.removeAll();
                try {
                    List<Review> reviews = get();
                    if (reviews.isEmpty()) list.add(text("Você ainda não avaliou nenhum filme.", 14, false, MUTED));
                    for (Review review : reviews) {
                        Movie movie = findMovie(review.movieId);
                        JPanel item = new JPanel(new BorderLayout(20, 5));
                        item.setBorder(new EmptyBorder(15, 18, 15, 18));
                        item.setBackground(CARD);
                        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 108));
                        JLabel label = text(movie == null ? "Filme #" + review.movieId : movie.title, 15, true, WHITE);
                        item.add(label, BorderLayout.NORTH);
                        JPanel metadata = transparent(new FlowLayout(FlowLayout.LEFT, 10, 0));
                        StarRating display = new StarRating(false);
                        display.setValue(review.rating);
                        metadata.add(display);
                        metadata.add(text(ratingText(review.rating) + "/5", 12, true, GOLD));
                        item.add(metadata, BorderLayout.CENTER);
                        if (movie != null) {
                            JButton open = button("Editar →", false);
                            open.addActionListener(e -> showMovie(movie));
                            item.add(open, BorderLayout.EAST);
                        }
                        item.setAlignmentX(Component.LEFT_ALIGNMENT);
                        list.add(item);
                        list.add(Box.createVerticalStrut(10));
                    }
                } catch (Exception ex) {
                    list.add(text("Não foi possível consultar as avaliações: " + shortError(ex), 12, false, ERROR));
                }
                list.revalidate();
                list.repaint();
            }
        }.execute();
    }

    private Movie findMovie(long id) {
        for (Movie movie : movies) if (movie.id == id) return movie;
        return null;
    }

    private void showUpcoming(String title, String description, String page) {
        prepareAppWindow(title);
        currentMovieId = -1;
        ++reviewVersion;
        JPanel center = new JPanel(new GridBagLayout());
        center.setBackground(BG);
        JPanel content = transparent();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel label = text(title, 28, true, WHITE);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(label);
        content.add(Box.createVerticalStrut(15));
        JLabel subtitle = text(description, 13, false, MUTED);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(subtitle);
        content.add(Box.createVerticalStrut(22));
        JButton back = button("Voltar ao catálogo", true);
        back.setAlignmentX(Component.CENTER_ALIGNMENT);
        back.addActionListener(e -> showCatalog());
        content.add(back);
        center.add(content);
        showScreen(page, appLayout(page, center));
    }

    // --------------------------- ESTRELAS (0..5, PASSO 0,5) -------------
    /** Uma estrela pode ser preenchida até a metade, à esquerda ou por inteiro. */
    private static class StarRating extends JComponent {
        private final boolean editable;
        private final int size;
        private final int step;
        private double value;
        private double hover = -1;
        private java.util.function.DoubleConsumer onChanged = ignored -> {};

        StarRating(boolean editable) {
            this.editable = editable;
            this.size = editable ? 34 : 18;
            this.step = editable ? 8 : 5;
            int w = 5 * size + 4 * step;
            setPreferredSize(new Dimension(w, size + 9));
            setMinimumSize(getPreferredSize());
            setMaximumSize(getPreferredSize());
            setOpaque(false);
            if (editable) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setFocusable(true);
                setToolTipText("Clique à esquerda para meia estrela ou à direita para estrela inteira. Use ← e →.");
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
                    @Override public void mouseClicked(MouseEvent e) {
                        requestFocusInWindow();
                        setValue(valueAt(e.getX()));
                    }
                });
                addMouseMotionListener(new MouseMotionAdapter() {
                    @Override public void mouseMoved(MouseEvent e) {
                        hover = valueAt(e.getX());
                        repaint();
                    }
                });
                getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "increase");
                getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "decrease");
                getActionMap().put("increase", new AbstractAction() {
                    @Override public void actionPerformed(ActionEvent e) { setValue(value + 0.5); }
                });
                getActionMap().put("decrease", new AbstractAction() {
                    @Override public void actionPerformed(ActionEvent e) { setValue(value - 0.5); }
                });
            }
        }

        double getValue() { return value; }
        void setOnChanged(java.util.function.DoubleConsumer listener) { onChanged = listener; }
        void setValue(double newValue) {
            value = Math.max(0, Math.min(5, Math.round(newValue * 2.0) / 2.0));
            onChanged.accept(value);
            repaint();
        }
        private double valueAt(int mouseX) {
            int stride = size + step;
            int index = Math.min(4, Math.max(0, mouseX / stride));
            int within = mouseX - index * stride;
            double half = within < size / 2.0 ? 0.5 : 1.0;
            return Math.min(5, index + half);
        }
        @Override protected void paintComponent(Graphics raw) {
            super.paintComponent(raw);
            Graphics2D g = (Graphics2D) raw.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double amount = hover >= 0 ? hover : value;
            for (int i = 0; i < 5; i++) {
                double x = i * (size + step) + size / 2.0;
                double cy = size / 2.0 + 4;
                Path2D star = new Path2D.Double();
                for (int corner = 0; corner < 10; corner++) {
                    double angle = -Math.PI / 2 + corner * Math.PI / 5;
                    double radius = corner % 2 == 0 ? size * 0.47 : size * 0.23;
                    double px = x + Math.cos(angle) * radius;
                    double py = cy + Math.sin(angle) * radius;
                    if (corner == 0) star.moveTo(px, py); else star.lineTo(px, py);
                }
                star.closePath();
                g.setColor(new Color(102, 115, 132));
                g.fill(star);
                double fill = Math.min(1, Math.max(0, amount - i));
                if (fill > 0) {
                    Shape old = g.getClip();
                    g.clip(new Rectangle2D.Double(i * (size + step), 0, size * fill, size + 9));
                    g.setColor(GOLD);
                    g.fill(star);
                    g.setClip(old);
                }
                g.setColor(fill > 0 ? new Color(230, 157, 30) : new Color(152, 161, 174));
                g.setStroke(new BasicStroke(editable ? 1.6f : 1.0f));
                g.draw(star);
            }
            g.dispose();
        }
    }

    private static String ratingText(double rating) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%.1f", rating);
    }

    // --------------------------- CAPAS: ROTINA ORIGINAL -----------------
    private JLabel poster(Movie movie, int width, int height) {
        JLabel target = new JLabel("CAPA DO FILME", SwingConstants.CENTER);
        target.setBackground(INPUT);
        target.setForeground(MUTED);
        target.setFont(font(12, true));
        target.setOpaque(true);
        target.setPreferredSize(new Dimension(width, height));
        target.setMinimumSize(new Dimension(width, height));
        target.setMaximumSize(new Dimension(width, height));

        String url = coverUrl(movie.image());
        if (url.isBlank()) { target.setText("Sem imagem"); return target; }
        String cacheKey = url + "#" + width + "x" + height;
        ImageIcon cached = posterCache.get(cacheKey);
        if (cached != null) { target.setIcon(cached); target.setText(""); return target; }
        if (failedPosters.contains(cacheKey)) { target.setText("Capa indisponível"); return target; }
        List<JLabel> existing = pendingPosters.get(cacheKey);
        if (existing != null) { existing.add(target); return target; }
        List<JLabel> labels = new ArrayList<>();
        labels.add(target);
        pendingPosters.put(cacheKey, labels);
        new SwingWorker<ImageIcon, Void>() {
            @Override protected ImageIcon doInBackground() throws Exception {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(18)).GET().build();
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200)
                    throw new IOException("Storage HTTP " + response.statusCode());
                BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(response.body()));
                if (img == null) throw new IOException("Formato de imagem não suportado");
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
                    ImageIcon icon = get();
                    posterCache.put(cacheKey, icon);
                    for (JLabel item : waiting) { item.setIcon(icon); item.setText(""); }
                } catch (Exception ex) {
                    failedPosters.add(cacheKey);
                    for (JLabel item : waiting) {
                        item.setText("Capa indisponível");
                        item.setToolTipText("Confira a URL no campo imagem_url e se o bucket é público.");
                    }
                }
            }
        }.execute();
        return target;
    }

    private static String coverUrl(String stored) {
        String path = stored == null ? "" : stored.trim();
        if (path.isBlank()) return "";
        if (path.startsWith("http://") || path.startsWith("https://")) return path.replace(" ", "%20");
        if (path.startsWith("/storage/v1/")) return SUPABASE_URL + path.replace(" ", "%20");
        if (path.startsWith(BUCKET + "/")) path = path.substring(BUCKET.length() + 1);
        StringJoiner pieces = new StringJoiner("/");
        for (String chunk : path.split("/")) {
            if (!chunk.isEmpty()) pieces.add(URLEncoder.encode(chunk, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return SUPABASE_URL + "/storage/v1/object/public/" + BUCKET + "/" + pieces;
    }

    // --------------------------- API / SUPABASE --------------------------
    private void login(String mail, String password) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("email", mail);
        payload.put("password", password);
        Map<String, Object> result = asMap(auth("password", payload));
        String token = str(result.get("access_token"));
        if (token.isEmpty()) throw new IOException("Login não retornou token de acesso.");
        accessToken = token;
        refreshToken = str(result.get("refresh_token"));
        userEmail = mail;
        expiresAtMillis = System.currentTimeMillis() + Math.max(60, number(result.get("expires_in")).longValue()) * 1000L;
        Map<String, Object> user = asMap(result.get("user"));
        userId = str(user.get("id"));
        if (userId.isBlank()) {
            Map<String, Object> identified = asMap(api("GET", "/auth/v1/user", null));
            userId = str(identified.get("id"));
        }
        if (userId.isBlank()) throw new IOException("Não foi possível identificar o usuário autenticado.");
    }

    private Object auth(String grantType, Map<String, Object> payload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(SUPABASE_URL + "/auth/v1/token?grant_type=" + grantType))
                .timeout(Duration.ofSeconds(25))
                .header("apikey", SUPABASE_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.encode(payload))).build();
        return responseJson(http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)));
    }

    private synchronized void ensureFreshToken() throws Exception {
        if (accessToken == null) throw new IOException("Faça login para continuar.");
        if (System.currentTimeMillis() < expiresAtMillis - 60_000) return;
        if (refreshToken == null || refreshToken.isBlank()) throw new IOException("Sessão expirada. Entre novamente.");
        String oldRefresh = refreshToken;
        Map<String, Object> refreshed = asMap(auth("refresh_token", Map.of("refresh_token", oldRefresh)));
        String next = str(refreshed.get("access_token"));
        if (next.isBlank()) throw new IOException("Não foi possível renovar a sessão.");
        accessToken = next;
        String replacement = str(refreshed.get("refresh_token"));
        if (!replacement.isBlank()) refreshToken = replacement;
        expiresAtMillis = System.currentTimeMillis() + Math.max(60, number(refreshed.get("expires_in")).longValue()) * 1000L;
    }

    private Object api(String method, String route, Object payload) throws Exception {
        ensureFreshToken();
        String token = accessToken;
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(SUPABASE_URL + route))
                .timeout(Duration.ofSeconds(25))
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .header("Accept-Profile", "public");
        if ("GET".equals(method)) b.GET();
        else {
            b.header("Content-Profile", "public")
             .header("Prefer", "return=representation");
            if (payload == null) b.method(method, HttpRequest.BodyPublishers.noBody());
            else b.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(Json.encode(payload), StandardCharsets.UTF_8));
        }
        HttpResponse<String> response = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return responseJson(response);
    }

    private Object responseJson(HttpResponse<String> response) throws IOException {
        String raw = response.body() == null ? "" : response.body();
        Object parsed;
        try { parsed = raw.isBlank() ? Map.of() : Json.parse(raw); }
        catch (Exception ignored) { parsed = Map.of("message", raw); }
        if (response.statusCode() / 100 != 2) {
            Map<String, Object> json = asMap(parsed);
            String detail = str(json.getOrDefault("message", json.getOrDefault("msg", json.getOrDefault("error_description", raw))));
            if (response.statusCode() == 401) throw new IOException("Sessão inválida/expirada ou sem permissão (HTTP 401).");
            if (response.statusCode() == 403) throw new IOException("Sem permissão (HTTP 403). Confira as políticas RLS.");
            if (response.statusCode() == 429) throw new IOException("Muitas tentativas. Aguarde alguns minutos.");
            if (response.statusCode() == 409) throw new IOException("Já existe uma avaliação para este filme.");
            if (response.statusCode() == 400 && detail.toLowerCase(Locale.ROOT).contains("email"))
                throw new IOException("E-mail ou senha inválidos (ou e-mail não confirmado).");
            throw new IOException("Supabase HTTP " + response.statusCode() + ": " + cut(detail, 160));
        }
        return parsed;
    }

    private static List<Review> reviewRows(Object result) {
        List<Review> records = new ArrayList<>();
        for (Object item : asList(result)) {
            Map<String, Object> row = asMap(item);
            records.add(new Review(number(row.get("id_avaliacao")).longValue(),
                    number(row.get("id_filme")).longValue(), str(row.get("id_usuario")),
                    number(row.get("nota")).doubleValue(), str(row.get("comentario")),
                    str(row.get("criado_em")), str(row.get("atualizado_em"))));
        }
        return records;
    }

    private void saveReview(long movieId, double rating, String comment) throws Exception {
        if (rating < 0 || rating > 5 || rating * 2 != Math.rint(rating * 2))
            throw new IOException("Escolha uma nota de 0 a 5 com intervalos de meia estrela.");
        if (comment.length() > 1000) throw new IOException("Comentário muito longo.");
        String own = "/rest/v1/" + TABLE + "?select=id_avaliacao&id_filme=eq." + movieId
                + "&id_usuario=eq." + userId + "&limit=1";
        List<Object> existing = asList(api("GET", own, null));
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("nota", rating);
        fields.put("comentario", comment);
        if (existing.isEmpty()) {
            fields.put("id_filme", movieId);
            // id_usuario = auth.uid() pelo DEFAULT configurado no SQL.
            api("POST", "/rest/v1/" + TABLE, fields);
        } else {
            long id = number(asMap(existing.get(0)).get("id_avaliacao")).longValue();
            api("PATCH", "/rest/v1/" + TABLE + "?id_avaliacao=eq." + id
                    + "&id_usuario=eq." + userId, fields);
        }
    }

    private void deleteReview(long movieId) throws Exception {
        api("DELETE", "/rest/v1/" + TABLE + "?id_filme=eq." + movieId
                + "&id_usuario=eq." + userId, null);
    }

    // --------------------------- HELPERS / JSON --------------------------
    private static void message(String msg) {
        JOptionPane.showMessageDialog(null, msg, "CineRate", JOptionPane.INFORMATION_MESSAGE);
    }
    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "CineRate - Erro", JOptionPane.ERROR_MESSAGE);
    }
    private static String cut(String value, int limit) {
        return value == null ? "" : value.length() > limit ? value.substring(0, limit - 1) + "…" : value;
    }
    private static String shortError(Throwable ex) {
        Throwable target = ex;
        while (target.getCause() != null) target = target.getCause();
        return cut(target.getMessage() == null ? "Erro de conexão." : target.getMessage(), 175);
    }
    private static String str(Object value) { return value == null ? "" : value.toString(); }
    private static Number number(Object value) {
        if (value instanceof Number n) return n;
        try { return Double.parseDouble(str(value)); } catch (Exception ignored) { return 0; }
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }
    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return value instanceof List<?> ? (List<Object>) value : List.of();
    }

    /** JSON mínimo de leitura/escrita, evitando dependências externas ao Java 17. */
    private static class Json {
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
            new avaliacao_filmes().setVisible(true);
        });
    }
}
