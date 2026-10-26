# 🎬 CineRate — Sistema de Avaliação de Filmes

O **CineRate** é um aplicativo desktop desenvolvido em **Java Swing**, com integração ao banco de dados **MySQL**.

O projeto permite consultar um catálogo de filmes, visualizar suas informações e compartilhar avaliações com outros usuários. Foi desenvolvido como parte das **Atividades Práticas Supervisionadas (APS)** do curso de Sistemas de Informação.

## 🚀 Funcionalidades

- **Login e cadastro:** autenticação de usuários por e-mail e senha.
- **Catálogo:** visualização dos filmes com capa, título, gênero, ano e duração.
- **Pesquisa e filtros:** busca por título e filtragem por gênero.
- **Detalhes:** informações do filme, média das notas e avaliações.
- **Avaliações:** notas de 0 a 5 estrelas, com intervalos de 0,5, e comentários.
- **Meu perfil:** consulta, edição e exclusão das próprias avaliações, além da alteração do nome.

## 🖥️ Organização das telas

O sistema foi simplificado e dividido em duas áreas principais:

**1. Área de acesso**

Reúne as telas de login e cadastro em uma única janela.

**2. Área principal**

Reúne catálogo, detalhes dos filmes, avaliações e perfil, com navegação por menu lateral.

## 🛠️ Tecnologias utilizadas

- Java 17+
- Java Swing
- MySQL
- JDBC (MySQL Connector/J)
- SQL

## 📁 Estrutura do projeto

```text
Avaliacoes_filmes-main/
├── avaliacao_filmes.java
├── TelasFilmes.java
├── Supabase.java
├── Estilo.java
├── inicializar.sql
├── catalogo.sql
└── database.properties
```

**Responsabilidades:**

- `avaliacao_filmes.java` — Login, cadastro, perfil e navegação.
- `TelasFilmes.java` — Catálogo, detalhes e avaliações.
- `Supabase.java` — Conexão com MySQL e operações no banco.
- `Estilo.java` — Componentes visuais, imagens e estrelas.
- `inicializar.sql` — Criação do banco e das tabelas.
- `catalogo.sql` — Cadastro dos 25 filmes.
- `database.properties` — Configurações da conexão.

*O nome Supabase.java foi preservado, mas a conexão atual utiliza MySQL.*

## ⚙️ Como executar

**Pré-requisitos:** Java JDK 17+, MySQL Server e MySQL Connector/J.

**1.** Execute `inicializar.sql` e depois `catalogo.sql` no MySQL.

**2.** Configure o arquivo `database.properties`:

```properties
db.host=localhost
db.port=3306
db.name=cinerate
db.user=root
db.password=SUA_SENHA
```

**3.** Coloque o driver JDBC na pasta do projeto, com o nome `mysql-connector-j.jar`.

**4.** Abra o terminal na pasta do projeto e compile:

```bash
javac *.java
```

**5.** Execute a aplicação no Windows:

```bat
java -cp ".;mysql-connector-j.jar" avaliacao_filmes
```

No Linux/macOS, substitua `;` por `:`.

## 🗃️ Banco de dados

O sistema utiliza quatro tabelas principais:

- `usuario` — Dados das contas.
- `filme` — Informações dos filmes.
- `genero` — Categorias dos filmes.
- `avaliacao_web` — Notas e comentários dos usuários.

Cada usuário pode cadastrar uma avaliação por filme, podendo editá-la ou excluí-la posteriormente.

**Observação:** as capas dos filmes utilizam caminhos definidos no banco de dados. Os arquivos de imagem locais não estão incluídos no ZIP e devem ser disponibilizados nos caminhos correspondentes.

---

**CineRate — Avalie. Descubra. Compartilhe.**
