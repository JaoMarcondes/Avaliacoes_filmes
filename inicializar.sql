CREATE DATABASE IF NOT EXISTS cinerate CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cinerate;
CREATE TABLE IF NOT EXISTS usuario (
 id_usuario CHAR(36) PRIMARY KEY,
 nome VARCHAR(100) NOT NULL,
 email VARCHAR(254) NOT NULL UNIQUE,
 senha_hash VARCHAR(255) NOT NULL,
 administrador BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS genero (
 id_genero BIGINT PRIMARY KEY AUTO_INCREMENT,
 nome_genero VARCHAR(100) NOT NULL UNIQUE
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS filme (
 id_filme BIGINT PRIMARY KEY AUTO_INCREMENT,
 titulo VARCHAR(255) NOT NULL,
 ano_lancamento INT NOT NULL,
 duracao INT NOT NULL,
 id_genero BIGINT NOT NULL,
 imagem_url TEXT NOT NULL,
 FOREIGN KEY (id_genero) REFERENCES genero(id_genero),
 CHECK (duracao > 0)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS avaliacao_web (
 id_avaliacao BIGINT PRIMARY KEY AUTO_INCREMENT,
 id_filme BIGINT NOT NULL,
 id_usuario CHAR(36) NOT NULL,
 nota DECIMAL(2,1) NOT NULL,
 comentario VARCHAR(1000) NOT NULL DEFAULT '',
 criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uma_avaliacao (id_filme,id_usuario),
 FOREIGN KEY (id_filme) REFERENCES filme(id_filme) ON DELETE CASCADE,
 FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario) ON DELETE CASCADE,
 CHECK (nota BETWEEN 0 AND 5 AND MOD(nota * 2,1)=0)
) ENGINE=InnoDB;
INSERT IGNORE INTO genero (nome_genero) VALUES
 ('Ação'),('Animação'),('Aventura'),('Comédia'),('Documentário'),('Drama'),('Fantasia'),('Ficção científica'),('Romance'),('Suspense'),('Terror');

