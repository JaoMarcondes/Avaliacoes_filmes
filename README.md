# 🎬 CineRate — Sistema de Avaliação de Filmes

## 🌐 Site exemplo

https://avaliacoes-filmes.vercel.app/

## 📌 Sobre o projeto

O **CineRate** é um aplicativo desktop desenvolvido em **Java**, com interface gráfica utilizando **Java Swing** e integração com o banco de dados **Supabase (PostgreSQL)**.

O projeto tem como objetivo permitir que usuários consultem um catálogo de filmes previamente cadastrados, visualizem suas informações e capas, atribuam notas e escrevam avaliações.

O sistema foi desenvolvido como parte das **Atividades Práticas Supervisionadas (APS)** do curso de Sistemas de Informação.

## 🚀 Funcionalidades

- **Login e cadastro:** autenticação de usuários.
- **Catálogo de filmes:** exibição de filmes cadastrados no banco de dados.
- **Pesquisa e filtros:** busca por título e gênero.
- **Detalhes dos filmes:** visualização de capa, título, ano de lançamento e duração.
- **Avaliações:** atribuição de notas de 0 a 10 e comentários.
- **Perfil do usuário:** consulta, edição e exclusão das próprias avaliações.
- **Sugestão de filmes:** possibilidade de sugerir títulos que ainda não estão disponíveis no catálogo.

Os usuários não podem cadastrar ou excluir filmes diretamente do catálogo.

## 🛠️ Tecnologias utilizadas

- **Java:** linguagem principal do sistema.
- **Java Swing:** desenvolvimento das interfaces gráficas.
- **Supabase:** integração com o banco de dados e autenticação.
- **PostgreSQL:** armazenamento dos dados.
- **Supabase Storage:** armazenamento das capas dos filmes.
- **API REST:** comunicação entre o Java e o Supabase.

## 🗄️ Banco de dados

O sistema utiliza as seguintes tabelas principais:

| Tabela | Finalidade |
|---|---|
| `cliente` | Informações dos usuários |
| `filme` | Informações dos filmes cadastrados |
| `genero` | Classificação dos filmes |
| `avaliacao` | Notas e comentários dos usuários |
| `sugestao_filme` | Sugestões de novos filmes |

## 📂 Status do desenvolvimento

**Projeto em desenvolvimento.**

A tela de login e o catálogo já foram implementados. A consulta dos filmes ao Supabase está funcionando, enquanto o carregamento das capas e as demais funcionalidades estão em desenvolvimento e testes.

## 🎯 Objetivo acadêmico

Aplicar conceitos de programação orientada a objetos, interfaces gráficas, bancos de dados relacionais, chaves primárias e estrangeiras, relacionamentos entre tabelas e operações CRUD (Create, Read, Update e Delete).
