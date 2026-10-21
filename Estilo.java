import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;

// Estilo comum: botões, campos, capas, estrelas e tarefas em segundo plano.

// -------- Estilo --------
/** Cores e componentes visuais usados em todas as telas. */
class Estilo {
    static final Color FUNDO = new Color(13, 18, 29);
    static final Color LATERAL = new Color(19, 27, 41);
    static final Color CARD = new Color(24, 32, 47);
    static final Color CAMPO = new Color(17, 25, 39);
    static final Color HOVER = new Color(37, 49, 71);
    static final Color ROSA = new Color(236, 43, 83);
    static final Color BRANCO = new Color(245, 247, 252);
    static final Color CINZA = new Color(169, 183, 203);
    static final Color OURO = new Color(255, 194, 77);
    static final Color ERRO = new Color(255, 154, 171);

    static JLabel texto(String valor, int tamanho, boolean negrito) {
        JLabel label = new JLabel(valor);
        label.setForeground(BRANCO);
        label.setFont(new Font("SansSerif", negrito ? Font.BOLD : Font.PLAIN, tamanho));
        return label;
    }
    static JLabel secundario(String valor) {
        JLabel l = texto(valor, 12, false);
        l.setForeground(CINZA);
        return l;
    }
    static JButton botao(String valor, boolean destaque) {
        JButton b = new JButton(valor);
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setBackground(destaque ? ROSA : HOVER);
        b.setForeground(BRANCO);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
    static JTextField campo() {
        JTextField c = new JTextField();
        estiloCampo(c);
        return c;
    }
    static void estiloCampo(JTextField campo) {
        campo.setBackground(CAMPO);
        campo.setForeground(BRANCO);
        campo.setCaretColor(BRANCO);
        campo.setFont(new Font("SansSerif", Font.PLAIN, 14));
        campo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(65, 78, 101)),
                new EmptyBorder(9, 11, 9, 11)));
    }
    static JTextArea area(int linhas) {
        JTextArea a = new JTextArea(linhas, 25);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setForeground(BRANCO);
        a.setBackground(CAMPO);
        a.setCaretColor(BRANCO);
        a.setBorder(new EmptyBorder(9, 11, 9, 11));
        a.setFont(new Font("SansSerif", Font.PLAIN, 13));
        return a;
    }
    static JPanel painel(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(FUNDO);
        return p;
    }
    static JPanel transparente(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }
    static JPanel vertical() {
        JPanel p = transparente(null);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }
    static JScrollPane rolagem(Component conteudo) {
        JScrollPane sc = new JScrollPane(conteudo);
        sc.getViewport().setBackground(FUNDO);
        sc.setBorder(BorderFactory.createEmptyBorder());
        sc.getVerticalScrollBar().setUnitIncrement(20);
        return sc;
    }
    static void aviso(Component parent, String mensagem) {
        JOptionPane.showMessageDialog(parent, mensagem, "CineRate", JOptionPane.INFORMATION_MESSAGE);
    }
    static void erro(Component parent, Exception erro) {
        Throwable t = erro;
        while (t.getCause() != null) t = t.getCause();
        JOptionPane.showMessageDialog(parent,
                t.getMessage() == null ? "Erro inesperado." : t.getMessage(),
                "CineRate - Erro", JOptionPane.ERROR_MESSAGE);
    }
    static String curto(String txt, int max) {
        return txt == null ? "" : txt.length() > max ? txt.substring(0, max - 1) + "…" : txt;
    }

    /** Pinta o seletor fechado e aberto para não ficar branco no Windows. */

    /** Executa operações do Supabase sem travar a janela. */
    static <T> void executar(Callable<T> tarefa, Consumer<T> sucesso, Consumer<Exception> erro) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return tarefa.call(); }
            @Override protected void done() {
                try { sucesso.accept(get()); }
                catch (Exception e) { erro.accept(e); }
            }
        }.execute();
    }

    static JComboBox<String> combo() {
        JComboBox<String> combo = new JComboBox<>();
        combo.setUI(new BasicComboBoxUI() {
            @Override public void paintCurrentValue(Graphics graphics, Rectangle bounds, boolean focused) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setColor(CAMPO);
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                Object selecionado = comboBox.getSelectedItem();
                if (selecionado != null) {
                    g.setColor(BRANCO);
                    g.setFont(new Font("SansSerif", Font.PLAIN, 13));
                    FontMetrics m = g.getFontMetrics();
                    g.drawString(selecionado.toString(), bounds.x + 11,
                            bounds.y + (bounds.height + m.getAscent())/2 - 2);
                }
                g.dispose();
            }
            @Override protected JButton createArrowButton() {
                JButton arrow = new JButton("⌄");
                arrow.setForeground(CINZA);
                arrow.setBackground(CAMPO);
                arrow.setFocusPainted(false);
                arrow.setBorderPainted(false);
                return arrow;
            }
            @Override protected ComboPopup createPopup() {
                BasicComboPopup popup = new BasicComboPopup(comboBox);
                popup.getList().setBackground(CAMPO);
                popup.getList().setForeground(BRANCO);
                popup.getList().setSelectionBackground(HOVER);
                popup.getList().setSelectionForeground(BRANCO);
                return popup;
            }
        });
        combo.setBackground(CAMPO);
        combo.setForeground(BRANCO);
        combo.setFont(new Font("SansSerif", Font.PLAIN, 13));
        combo.setBorder(BorderFactory.createLineBorder(new Color(65, 78, 101)));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focused) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                label.setOpaque(true);
                label.setBackground(selected ? HOVER : CAMPO);
                label.setForeground(BRANCO);
                label.setBorder(new EmptyBorder(8, 12, 8, 10));
                return label;
            }
        });
        return combo;
    }
}


// -------- Imagens --------
/** Faz download das capas em segundo plano e evita repetições. */
class Imagens {
    private static final Map<String, ImageIcon> cache = new ConcurrentHashMap<>();
    private static final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    static JLabel capa(Filme filme, int largura, int altura) {
        JLabel label = new JLabel("Carregando capa...", SwingConstants.CENTER);
        label.setForeground(Estilo.CINZA);
        label.setBackground(Estilo.CAMPO);
        label.setOpaque(true);
        label.setPreferredSize(new Dimension(largura, altura));
        label.setMinimumSize(new Dimension(largura, altura));
        label.setMaximumSize(new Dimension(largura, altura));
        String url = urlDaCapa(filme.capa());
        if (url.isBlank()) { label.setText("Sem imagem"); return label; }
        String chave = url + "|" + largura + "x" + altura;
        if (cache.containsKey(chave)) {
            label.setIcon(cache.get(chave));
            label.setText("");
            return label;
        }
        new SwingWorker<ImageIcon, Void>() {
            @Override protected ImageIcon doInBackground() throws Exception {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(java.time.Duration.ofSeconds(18)).GET().build();
                HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() != 200) throw new Exception("HTTP " + resp.statusCode());
                BufferedImage imagem = ImageIO.read(new ByteArrayInputStream(resp.body()));
                if (imagem == null) throw new Exception("Formato de imagem desconhecido");
                // Ajusta a capa sem distorcer sua proporção.
                double escala = Math.max(largura / (double) imagem.getWidth(), altura / (double) imagem.getHeight());
                int w = (int) Math.ceil(imagem.getWidth() * escala);
                int h = (int) Math.ceil(imagem.getHeight() * escala);
                BufferedImage pronta = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = pronta.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(imagem, (largura-w)/2, (altura-h)/2, w, h, null);
                g.dispose();
                return new ImageIcon(pronta);
            }
            @Override protected void done() {
                try {
                    ImageIcon icon = get();
                    cache.put(chave, icon);
                    label.setIcon(icon);
                    label.setText("");
                } catch (Exception e) {
                    label.setText("Capa indisponível");
                    label.setToolTipText("Confira imagem_url e o bucket capas-filmes");
                }
            }
        }.execute();
        return label;
    }

    private static String urlDaCapa(String url) {
        if (url == null || url.isBlank()) return "";
        url = url.trim();
        if (url.startsWith("https://") || url.startsWith("http://")) return url.replace(" ", "%20");
        if (url.startsWith("/storage/v1/")) return Supabase.URL + url.replace(" ", "%20");
        if (url.startsWith(Supabase.BUCKET + "/")) url = url.substring(Supabase.BUCKET.length()+1);
        StringJoiner caminho = new StringJoiner("/");
        for (String parte : url.split("/"))
            if (!parte.isEmpty()) caminho.add(URLEncoder.encode(parte, StandardCharsets.UTF_8).replace("+", "%20"));
        return Supabase.URL + "/storage/v1/object/public/" + Supabase.BUCKET + "/" + caminho;
    }
}


// -------- Estrelas --------
/** Estrelas clicáveis de 0 a 5 com passos de 0,5. */
class Estrelas extends JComponent {
    private double valor = 0;
    private final boolean editavel;
    private final int tamanho;
    private java.util.function.DoubleConsumer mudou = n -> {};

    Estrelas(boolean editavel) {
        this.editavel = editavel;
        tamanho = editavel ? 32 : 18;
        setPreferredSize(new Dimension(5 * (tamanho + 7), tamanho + 8));
        if (editavel) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Clique na metade de uma estrela para notas de 0,5 em 0,5.");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int i = Math.min(4, Math.max(0, e.getX() / (tamanho + 7)));
                    double meio = (e.getX() % (tamanho + 7) < tamanho/2) ? 0.5 : 1;
                    setValor(i + meio);
                }
            });
        }
    }
    void aoMudar(java.util.function.DoubleConsumer evento) { mudou = evento; }
    double valor() { return valor; }
    void setValor(double n) {
        valor = Math.max(0, Math.min(5, Math.round(n * 2) / 2.0));
        mudou.accept(valor);
        repaint();
    }
    @Override protected void paintComponent(Graphics raw) {
        Graphics2D g = (Graphics2D) raw.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i=0; i<5; i++) {
            double x = i * (tamanho+7) + tamanho/2.0;
            double y = tamanho/2.0 + 4;
            Path2D estrela = new Path2D.Double();
            for (int j=0; j<10; j++) {
                double angulo = -Math.PI/2 + j*Math.PI/5;
                double raio = tamanho * (j%2==0 ? 0.47 : 0.23);
                double px = x + Math.cos(angulo)*raio;
                double py = y + Math.sin(angulo)*raio;
                if (j==0) estrela.moveTo(px,py); else estrela.lineTo(px,py);
            }
            estrela.closePath();
            g.setColor(new Color(102,115,132)); g.fill(estrela);
            double parte = Math.min(1,Math.max(0,valor-i));
            if (parte>0) {
                Shape anterior = g.getClip();
                g.clip(new Rectangle2D.Double(i*(tamanho+7),0,tamanho*parte,tamanho+8));
                g.setColor(Estilo.OURO); g.fill(estrela);
                g.setClip(anterior);
            }
        }
        g.dispose();
    }
}

