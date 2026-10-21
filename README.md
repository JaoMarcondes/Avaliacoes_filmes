<div align="center">

# 🎬 CineRate

**Sistema de Avaliação de Filmes**

Aplicativo desktop desenvolvido em **Java Swing**, integrado ao **Supabase**.

**Avalie • Descubra • Compartilhe**

</div>

---

## 📖 Sobre o projeto

O **CineRate** permite explorar um catálogo de filmes, visualizar capas e informações e publicar avaliações com notas e comentários. Cada usuário pode consultar e gerenciar suas próprias avaliações.

Projeto desenvolvido para as **Atividades Práticas Supervisionadas (APS)** do curso de Sistemas de Informação.

## ✨ Funcionalidades

- **Login e cadastro:** autenticação de usuários pelo Supabase Auth.
- **Catálogo de filmes:** capas, títulos, gêneros, ano de lançamento e duração.
- **Busca e filtros:** pesquisa por título e seleção por gênero.
- **Avaliações:** notas de **0 a 5 estrelas**, com intervalos de meia estrela, e comentários.
- **Meu perfil:** histórico de avaliações, com opções para editar e excluir.
- **Área administrativa:** cadastro de novos filmes e capas por usuários autorizados.

## 🧰 Tecnologias

| Tecnologia | Utilização |
| :--- | :--- |
| **Java 17+** | Desenvolvimento da aplicação |
| **Java Swing** | Interface gráfica |
| **Supabase Auth** | Login e cadastro |
| **PostgreSQL (Supabase)** | Banco de dados |
| **Supabase Storage** | Armazenamento das capas |
| **API REST** | Comunicação entre Java e Supabase |

## 📂 Organização do projeto

O código foi organizado em **cinco arquivos Java**, agrupando responsabilidades relacionadas para facilitar a leitura e a manutenção.

```text
CineRate/
├── avaliacao_filmes.java   # Inicialização e navegação
├── TelasUsuario.java      # Login, cadastro e perfil
├── TelasFilmes.java       # Catálogo, avaliações e cadastro de filmes
├── Supabase.java          # Autenticação e operações no banco
├── Estilo.java            # Cores, componentes e imagens
├── assets/
│   └── icone_cinerate.png
└── executar_windows.bat
```

### Responsabilidades dos arquivos

| Arquivo | O que faz |
| :--- | :--- |
| `avaliacao_filmes.java` | Inicia o aplicativo e controla a troca de telas. |
| `TelasUsuario.java` | Reúne login, cadastro e perfil do usuário. |
| `TelasFilmes.java` | Exibe o catálogo, detalhes, avaliações e cadastro administrativo. |
| `Supabase.java` | Faz as requisições à API, autentica usuários e consulta/grava dados. |
| `Estilo.java` | Padroniza cores, botões, campos, estrelas e carregamento das capas. |

## 🗄️ Banco de dados

| Tabela | Finalidade |
| :--- | :--- |
| `filme` | Dados dos filmes e URL de suas capas. |
| `genero` | Categorias utilizadas nos filtros e cadastros. |
| `avaliacao_web` | Notas e comentários associados a filmes e usuários. |
| `cinerate_perfis` | Nomes públicos exibidos nas avaliações. |
| `cinerate_admins` | Controle de usuários administradores. |

A autenticação é feita pelo **Supabase Auth**. As capas ficam no bucket **`capas-filmes`**; a tabela `filme` guarda os links no campo `imagem_url`.

## ▶️ Como executar

**Requisito:** JDK 17 ou superior.

1. Baixe ou clone o repositório.
2. Abra a pasta do projeto no VS Code.
3. No Windows, execute `executar_windows.bat`.

Como alternativa, execute no terminal da pasta onde estão os arquivos Java:

```bash
javac -encoding UTF-8 *.java
java avaliacao_filmes
```

> **Nota:** a aplicação depende da configuração e das permissões de acesso do projeto Supabase. Não inclua senhas do banco ou chaves secretas (`service_role`) no código público.

## 🎯 Objetivo acadêmico

Praticar **programação orientada a objetos**, interfaces gráficas, organização em classes, **bancos de dados relacionais** e operações **CRUD** (criar, consultar, atualizar e excluir dados).

## 🚧 Status

Projeto em desenvolvimento e testes. As funcionalidades dependem de validação com o Supabase configurado para o CineRate.
