# 🎬 CineRate — Sistema de Avaliação de Filmes

## 📌 Sobre o projeto

O **CineRate** é um aplicativo desktop desenvolvido em **Java**, utilizando **Java Swing** para a interface gráfica e **Supabase (PostgreSQL)** para o armazenamento e gerenciamento dos dados.

O objetivo do sistema é permitir que usuários consultem um catálogo de filmes, visualizem suas capas e informações, atribuam notas e compartilhem avaliações.

O projeto foi desenvolvido como parte das **Atividades Práticas Supervisionadas (APS)** do curso de Sistemas de Informação.

## 🚀 Funcionalidades

- **Login e cadastro:** criação de contas e autenticação de usuários.
- **Catálogo de filmes:** exibição dos filmes cadastrados com suas respectivas capas.
- **Pesquisa e filtros:** busca de filmes por título e gênero.
- **Detalhes dos filmes:** visualização de título, gênero, ano de lançamento e duração.
- **Avaliações:** notas de 0 a 5 estrelas, permitindo intervalos de meia estrela, e comentários.
- **Meu Perfil:** visualização, edição e exclusão das próprias avaliações.
- **Cadastro de filmes:** inclusão de novos filmes, gêneros e capas por usuários administradores.
- **Permissões:** usuários comuns podem avaliar filmes, enquanto administradores também podem cadastrar novos títulos.

## 🛠️ Tecnologias utilizadas

- **Java:** linguagem de programação principal.
- **Java Swing:** criação das interfaces gráficas.
- **Supabase:** autenticação e comunicação com o banco.
- **PostgreSQL:** banco de dados relacional.
- **Supabase Storage:** armazenamento das imagens dos filmes.
- **API REST:** comunicação entre Java e Supabase.

## 📁 Organização do código

Para facilitar o desenvolvimento e a manutenção, o CineRate foi dividido em **5 arquivos Java principais**.

Essa organização permite separar as telas, a comunicação com o banco de dados e os componentes visuais, evitando concentrar todo o sistema em um único arquivo.

### Estrutura do projeto

```text
CineRate/
│
├── avaliacao_filmes.java
├── TelasUsuario.java
├── TelasFilmes.java
├── Supabase.java
├── Estilo.java
│
├── assets/
│   └── icone_cinerate.png
│
├── sql/

### 1. avaliacao_filmes.java — Arquivo principal

É responsável por **iniciar o programa e controlar a navegação entre as telas**.

Suas principais funções são:

- Iniciar a aplicação.
- Exibir o menu lateral.
- Controlar a passagem de uma tela para outra.
- Organizar o funcionamento geral do programa.

### 2. TelasUsuario.java — Telas dos usuários

Agrupa as interfaces relacionadas às contas e aos perfis.

Contém três classes:

- **LoginView:** entrada dos usuários no sistema.
- **CadastroView:** criação de novas contas.
- **PerfilView:** visualização dos dados e das avaliações do usuário.

### 3. TelasFilmes.java — Telas dos filmes

Reúne as interfaces relacionadas ao catálogo e às avaliações.

Contém três classes:

- **CatalogoView:** apresenta os filmes, imagens, pesquisa e filtros.
- **FilmeView:** exibe os detalhes de um filme e permite publicar, editar ou excluir avaliações.
- **CadastrarFilmeView:** permite que administradores cadastrem novos filmes e suas capas.

### 4. Supabase.java — Comunicação com o banco

É responsável por **enviar e receber informações do Supabase**.

Suas principais funções são:

- Autenticar e cadastrar usuários.
- Consultar os filmes e gêneros.
- Salvar, consultar, atualizar e excluir avaliações.
- Consultar os nomes dos usuários.
- Verificar permissões de administrador.
- Registrar novos filmes.

Também reúne as estruturas de dados utilizadas pelo aplicativo e o tratamento das respostas JSON.

### 5. Estilo.java — Aparência e componentes

Centraliza os elementos visuais que são utilizados em diferentes telas.

Suas principais funções são:

- Definir as cores e fontes.
- Padronizar botões e campos.
- Personalizar seletores de gênero.
- Carregar e exibir as capas dos filmes.
- Desenhar as estrelas das avaliações.
- Reutilizar componentes gráficos.

Dessa forma, é possível modificar o visual do aplicativo sem precisar alterar cada tela separadamente.

## 🗄️ Banco de dados

O CineRate utiliza as seguintes tabelas:

| Tabela | Finalidade |
|---|---|
| `filme` | Armazena os filmes cadastrados |
| `genero` | Organiza os gêneros dos filmes |
| `avaliacao_web` | Armazena notas e comentários |
| `cinerate_perfis` | Armazena os nomes públicos dos usuários |
| `cinerate_admins` | Identifica os administradores |

O **Supabase Auth** é utilizado para autenticar as contas. As imagens ficam no bucket `capas-filmes`, e suas URLs são armazenadas no campo `imagem_url` da tabela `filme`.

Todos os arquivos Java devem permanecer na mesma pasta para que a compilação funcione corretamente.
