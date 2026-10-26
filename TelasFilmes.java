import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

// Telas de filmes: Catálogo e Avaliações.

// -------- Catalogo --------
/** Tela inicial com pesquisa, filtro por gênero e capas locais ou por URL. */
class CatalogoView extends JPanel {
    private final avaliacao_filmes app;
    private final JTextField pesquisa = Estilo.campo();
    private final JComboBox<String> filtro = Estilo.combo();
    private final JPanel grade = Estilo.transparente(new GridLayout(0, 4, 15, 20));
    private final JLabel quantidade = Estilo.secundario("Carregando filmes...");
    private final JButton atualizar = Estilo.botao("↻ Atualizar", false);
    private boolean preenchendoFiltro = false;

    CatalogoView(avaliacao_filmes app) {
        super(new BorderLayout(0, 22));
        this.app = app;
        setBackground(Estilo.FUNDO);
        setBorder(new EmptyBorder(26, 27, 24, 27));
        JPanel topo = Estilo.transparente(new BorderLayout(0, 17));
        JPanel ferramentas = Estilo.transparente(new BorderLayout(16, 0));
        pesquisa.setToolTipText("Pesquisar filmes pelo título");
        JPanel busca = Estilo.transparente(new BorderLayout(10,0));
        busca.add(Estilo.secundario("Pesquisar filmes:"), BorderLayout.WEST);
        busca.add(pesquisa, BorderLayout.CENTER);
        ferramentas.add(busca, BorderLayout.CENTER);
        filtro.setPreferredSize(new Dimension(188, 42));
        ferramentas.add(filtro, BorderLayout.EAST);
        topo.add(ferramentas, BorderLayout.NORTH);
        JPanel linha = Estilo.transparente(new BorderLayout());
        linha.add(Estilo.texto("Filmes em destaque", 23, true), BorderLayout.WEST);
        JPanel controles = Estilo.transparente(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controles.add(quantidade);
        controles.add(atualizar);
        linha.add(controles, BorderLayout.EAST);
        topo.add(linha, BorderLayout.SOUTH);
        add(topo, BorderLayout.NORTH);

        JPanel conteudo = Estilo.transparente(new BorderLayout());
        conteudo.add(grade, BorderLayout.NORTH);
        add(Estilo.rolagem(conteudo), BorderLayout.CENTER);
        filtro.addItem("Todos os gêneros");
        atualizar.addActionListener(e -> carregar());
        filtro.addActionListener(e -> { if (!preenchendoFiltro) mostrar(); });
        pesquisa.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { mostrar(); }
            public void removeUpdate(DocumentEvent e) { mostrar(); }
            public void changedUpdate(DocumentEvent e) { mostrar(); }
        });
        carregar();
    }

    private void carregar() {
        atualizar.setEnabled(false);
        quantidade.setText("Buscando filmes...");
        // Uma chamada traz filmes e gêneros. O catálogo usa os dados da tabela filme.
        Estilo.executar(() -> {
            List<Filme> filmes = app.dados.listarFilmes();
            Map<Long, String> generos = app.dados.listarGeneros();
            return new Resultado(filmes, generos);
        }, resultado -> {
            app.filmes = resultado.filmes();
            app.generos = resultado.generos();
            String selecionado = (String) filtro.getSelectedItem();
            preenchendoFiltro = true;
            try {
                filtro.removeAllItems();
                filtro.addItem("Todos os gêneros");
                for (String nome : app.generos.values()) filtro.addItem(nome);
                if (selecionado != null) filtro.setSelectedItem(selecionado);
            } finally { preenchendoFiltro = false; }
            atualizar.setEnabled(true);
            mostrar();
        }, erro -> {
            atualizar.setEnabled(true);
            quantidade.setText("Erro ao carregar");
            Estilo.erro(this, erro);
        });
    }

    private void mostrar() {
        grade.removeAll();
        String textoBusca = pesquisa.getText().toLowerCase(Locale.ROOT).trim();
        String selecionado = (String) filtro.getSelectedItem();
        int total = 0;
        for (Filme filme : app.filmes) {
            if (!filme.titulo().toLowerCase(Locale.ROOT).contains(textoBusca)) continue;
            String genero = app.nomeGenero(filme);
            if (selecionado != null && !selecionado.equals("Todos os gêneros") && !genero.equals(selecionado))
                continue;
            grade.add(cartao(filme));
            total++;
        }
        if (total == 0) grade.add(Estilo.secundario("Nenhum filme encontrado."));
        quantidade.setText(total + " filme(s)");
        grade.revalidate();
        grade.repaint();
    }

    private JPanel cartao(Filme filme) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Estilo.CARD);
        card.setBorder(new EmptyBorder(12, 11, 12, 11));
        card.setPreferredSize(new Dimension(202, 382));
        JLabel capa = Imagens.capa(filme, 169, 231);
        capa.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        capa.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) { app.abrirFilme(filme); }
        });
        JPanel imagem = Estilo.transparente(new FlowLayout(FlowLayout.CENTER, 0, 0));
        imagem.add(capa);
        card.add(imagem, BorderLayout.NORTH);
        JPanel detalhes = Estilo.vertical();
        JLabel titulo = Estilo.texto(Estilo.curto(filme.titulo(), 25), 12, true);
        titulo.setToolTipText(filme.titulo());
        detalhes.add(titulo);
        detalhes.add(Box.createVerticalStrut(5));
        detalhes.add(Estilo.secundario(filme.ano() + " · " + app.nomeGenero(filme)));
        detalhes.add(Box.createVerticalStrut(4));
        detalhes.add(Estilo.secundario(filme.duracao() + " min"));
        JButton abrir = Estilo.botao("★  Ver filme →", false);
        abrir.setAlignmentX(LEFT_ALIGNMENT);
        abrir.addActionListener(e -> app.abrirFilme(filme));
        detalhes.add(Box.createVerticalStrut(7));
        detalhes.add(abrir);
        card.add(detalhes, BorderLayout.CENTER);
        return card;
    }

    private record Resultado(List<Filme> filmes, Map<Long, String> generos) { }
}


// -------- Filme --------
/** Detalhes do filme, notas dos usuários e criação/edição da avaliação. */
class FilmeView extends JPanel {
    private final avaliacao_filmes app;
    private final Filme filme;
    private final Estrelas estrelas = new Estrelas(true);
    private final JTextArea comentario = Estilo.area(4);
    private final JLabel media = Estilo.texto("Carregando avaliações...", 14, true);
    private final JLabel nota = Estilo.texto("0,0 / 5", 13, true);
    private final JPanel comentarios = Estilo.vertical();
    private final JButton salvar = Estilo.botao("Publicar avaliação", true);
    private final JButton excluir = Estilo.botao("Excluir minha avaliação", false);
    private Avaliacao minha;

    FilmeView(avaliacao_filmes app, Filme filme) {
        super(new BorderLayout(0, 20));
        this.app = app;
        this.filme = filme;
        setBackground(Estilo.FUNDO);
        setBorder(new EmptyBorder(23, 27, 24, 27));
        JButton voltar = Estilo.botao("← Voltar ao catálogo", false);
        voltar.addActionListener(e -> app.abrirCatalogo());
        JPanel cabecalho = Estilo.transparente(new FlowLayout(FlowLayout.LEFT,0,0));
        cabecalho.add(voltar);
        add(cabecalho, BorderLayout.NORTH);

        JPanel corpo = Estilo.transparente(new BorderLayout(25,0));
        JPanel esquerda = Estilo.vertical();
        esquerda.add(Imagens.capa(filme,220,315));
        esquerda.add(Box.createVerticalStrut(15));
        esquerda.add(Estilo.secundario(app.nomeGenero(filme)));
        corpo.add(esquerda, BorderLayout.WEST);

        JPanel direita = Estilo.vertical();
        direita.add(Estilo.texto(filme.titulo(),26,true));
        direita.add(Box.createVerticalStrut(8));
        direita.add(Estilo.secundario(filme.ano() + " · " + filme.duracao() + " min"));
        direita.add(Box.createVerticalStrut(13));
        media.setForeground(Estilo.OURO);
        direita.add(media);
        direita.add(Box.createVerticalStrut(19));

        JPanel editor = new JPanel();
        editor.setBackground(Estilo.CARD);
        editor.setBorder(new EmptyBorder(17,19,17,19));
        editor.setLayout(new BoxLayout(editor,BoxLayout.Y_AXIS));
        editor.add(Estilo.texto("Sua avaliação",17,true));
        editor.add(Box.createVerticalStrut(12));
        JPanel linha = Estilo.transparente(new FlowLayout(FlowLayout.LEFT,8,0));
        linha.add(estrelas);
        nota.setForeground(Estilo.OURO);
        linha.add(nota);
        estrelas.aoMudar(n -> nota.setText(String.format(Locale.forLanguageTag("pt-BR"), "%.1f / 5",n)));
        editor.add(linha);
        editor.add(Box.createVerticalStrut(7));
        JButton limpar = Estilo.botao("Limpar estrelas (0)",false);
        limpar.addActionListener(e -> estrelas.setValor(0));
        editor.add(limpar);
        editor.add(Box.createVerticalStrut(14));
        editor.add(Estilo.secundario("Seu comentário (opcional, até 1000 caracteres)"));
        editor.add(Box.createVerticalStrut(6));
        JScrollPane campoComentario = Estilo.rolagem(comentario);
        campoComentario.setPreferredSize(new Dimension(460,110));
        campoComentario.setMaximumSize(new Dimension(Integer.MAX_VALUE,110));
        editor.add(campoComentario);
        editor.add(Box.createVerticalStrut(12));
        JPanel botoes = Estilo.transparente(new FlowLayout(FlowLayout.LEFT,8,0));
        botoes.add(salvar);
        excluir.setVisible(false);
        botoes.add(excluir);
        editor.add(botoes);
        for (Component componente : editor.getComponents()) {
            if (componente instanceof JComponent c) c.setAlignmentX(LEFT_ALIGNMENT);
        }
        direita.add(editor);
        direita.add(Box.createVerticalStrut(20));
        direita.add(Estilo.texto("Avaliações da comunidade",18,true));
        direita.add(Box.createVerticalStrut(10));
        direita.add(comentarios);
        for (Component componente : direita.getComponents()) {
            if (componente instanceof JComponent c) c.setAlignmentX(LEFT_ALIGNMENT);
        }
        corpo.add(direita, BorderLayout.CENTER);
        add(Estilo.rolagem(corpo),BorderLayout.CENTER);

        salvar.addActionListener(e -> salvarAvaliacao());
        excluir.addActionListener(e -> excluirAvaliacao());
        carregarAvaliacoes();
    }

    private void carregarAvaliacoes() {
        comentarios.removeAll();
        comentarios.add(Estilo.secundario("Buscando avaliações..."));
        Estilo.executar(() -> {
            List<Avaliacao> lista = app.dados.listarAvaliacoes(filme.id());
            Map<String,String> nomes = app.dados.nomesPublicos(lista);
            return new Comentarios(lista, nomes);
        }, resultado -> {
            List<Avaliacao> lista = resultado.avaliacoes();
            Map<String,String> nomes = resultado.nomes();
            comentarios.removeAll();
            minha = null;
            double soma = 0;
            for (Avaliacao a : lista) {
                soma += a.nota();
                if (a.usuarioId().equals(app.dados.usuarioId)) minha = a;
                comentarios.add(cartaoAvaliacao(a, nomes));
                comentarios.add(Box.createVerticalStrut(9));
            }
            if (lista.isEmpty()) {
                media.setText("★ Ainda sem avaliações");
                comentarios.add(Estilo.secundario("Seja a primeira pessoa a avaliar!"));
            } else media.setText(String.format(Locale.forLanguageTag("pt-BR"),
                    "★ %.1f / 5 · %d avaliação(ões)",soma/lista.size(),lista.size()));
            if (minha != null) {
                estrelas.setValor(minha.nota());
                comentario.setText(minha.comentario());
            }
            salvar.setText(minha == null ? "Publicar avaliação" : "Salvar alterações");
            excluir.setVisible(minha != null);
            comentarios.revalidate(); comentarios.repaint();
        }, erro -> {
            comentarios.removeAll();
            comentarios.add(Estilo.secundario("Erro ao consultar avaliações."));
            Estilo.erro(this, erro);
        });
    }

    private record Comentarios(List<Avaliacao> avaliacoes, Map<String,String> nomes) { }

    private JPanel cartaoAvaliacao(Avaliacao a, Map<String,String> nomes) {
        JPanel card = new JPanel(new BorderLayout(0,9));
        card.setBackground(Estilo.CARD);
        card.setBorder(new EmptyBorder(12,15,12,15));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE,130));
        String nome = nomes.getOrDefault(a.usuarioId(),"Usuário");
        if (nome.isBlank()) nome="Usuário";
        if (a.usuarioId().equals(app.dados.usuarioId)) nome += " (você)";
        JPanel topo = Estilo.transparente(new FlowLayout(FlowLayout.LEFT,10,0));
        topo.add(Estilo.texto(nome,13,true));
        Estrelas stars = new Estrelas(false);
        stars.setValor(a.nota());
        topo.add(stars);
        JLabel score = Estilo.secundario(String.format(Locale.forLanguageTag("pt-BR"), "%.1f / 5",a.nota()));
        score.setForeground(Estilo.OURO);
        topo.add(score);
        card.add(topo,BorderLayout.NORTH);
        JTextArea body = Estilo.area(2);
        body.setText(a.comentario().isBlank() ? "(Sem comentário)" : a.comentario());
        body.setOpaque(false);
        body.setEditable(false);
        body.setFocusable(false);
        card.add(body,BorderLayout.CENTER);
        return card;
    }

    private void salvarAvaliacao() {
        double valor = estrelas.valor();
        String texto = comentario.getText().trim();
        salvar.setEnabled(false);
        Estilo.executar(() -> { app.dados.salvarAvaliacao(filme.id(),valor,texto); return true; },
                ok -> { salvar.setEnabled(true); carregarAvaliacoes(); }, erro -> {
                    salvar.setEnabled(true); Estilo.erro(this,erro);
                });
    }
    private void excluirAvaliacao() {
        if (minha == null) return;
        if (JOptionPane.showConfirmDialog(this,"Excluir sua avaliação?","CineRate",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        long id = minha.id();
        excluir.setEnabled(false);
        Estilo.executar(() -> { app.dados.excluirAvaliacao(id); return true; }, ok -> {
            excluir.setEnabled(true);
            estrelas.setValor(0);
            comentario.setText("");
            carregarAvaliacoes();
        }, erro -> { excluir.setEnabled(true); Estilo.erro(this,erro); });
    }
}

