/**
 * CineRate — conversão do projeto Java Swing para JavaScript puro.
 * Backend original preservado: Supabase Auth, public.filme, public.genero e Storage.
 * A chave abaixo é PUBLISHABLE (pública). Nunca use uma secret/service_role aqui.
 */
'use strict';

const SUPABASE_URL = 'https://pfqubgfnjtxsocwtmzrm.supabase.co';
const SUPABASE_KEY = 'sb_publishable_155R5aCPgq2Rv24Y_NHwEQ_YAeUgs2D';
const STORAGE_BUCKET = 'capas-filmes';
const STORAGE_KEY = 'cinerate_session';

const $ = (selector) => document.querySelector(selector);
const app = {
  session: readSavedSession(),
  movies: [],
  genres: new Map(),
  catalogLoaded: false,
  catalogLoading: false,
  loadVersion: 0,
  tokenRefresh: null,
  toastTimer: null,
};

function readSavedSession() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || 'null');
    if (saved && typeof saved.accessToken === 'string' && typeof saved.refreshToken === 'string') return saved;
  } catch (_) { /* sessão inválida */ }
  return null;
}

function saveSession(payload, fallbackEmail = '') {
  if (!payload || !payload.access_token) throw new Error('O Supabase não retornou o token de acesso.');
  app.session = {
    accessToken: payload.access_token,
    refreshToken: payload.refresh_token || app.session?.refreshToken || '',
    expiresAt: Date.now() + (Number(payload.expires_in) || 3600) * 1000,
    email: payload.user?.email || fallbackEmail || app.session?.email || '',
  };
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(app.session));
  return app.session;
}

function clearSession() {
  app.session = null;
  app.catalogLoaded = false;
  app.movies = [];
  app.genres.clear();
  app.loadVersion += 1;
  sessionStorage.removeItem(STORAGE_KEY);
}

function supabaseError(status, response, kind = 'data') {
  const code = String(response?.error_code || response?.code || '').toLowerCase();
  const message = String(response?.msg || response?.message || response?.error_description || response?.error || '').toLowerCase();

  if (kind === 'auth') {
    if (code === 'email_not_confirmed' || message.includes('email not confirmed'))
      return 'Seu e-mail ainda não foi confirmado no Supabase Auth.';
    if (status === 429 || code.includes('rate_limit'))
      return 'Muitas tentativas de login. Aguarde alguns minutos e tente novamente.';
    if (status === 400 || status === 401)
      return 'E-mail ou senha inválidos.';
    return `Não foi possível entrar (HTTP ${status}). Tente novamente.`;
  }

  if (status === 401) return 'Sua sessão expirou. Faça login novamente.';
  if (status === 403) return 'Sem permissão para consultar os filmes. Confira as políticas RLS do Supabase.';
  if (status === 400) return `Consulta inválida (HTTP 400). Confira os nomes das tabelas e colunas.${message ? ' Detalhe: ' + message.slice(0, 140) : ''}`;
  return `Erro ao consultar o Supabase (HTTP ${status}).${message ? ' ' + message.slice(0, 160) : ''}`;
}

async function jsonResponse(response) {
  const body = await response.text();
  try { return body ? JSON.parse(body) : {}; }
  catch (_) { return { message: body.slice(0, 200) }; }
}

async function login(email, password) {
  const response = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { apikey: SUPABASE_KEY, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  const result = await jsonResponse(response);
  if (!response.ok) throw new Error(supabaseError(response.status, result, 'auth'));
  saveSession(result, email);
}

async function refreshSession() {
  if (app.tokenRefresh) return app.tokenRefresh;
  if (!app.session?.refreshToken) throw new Error('Sessão expirada. Entre novamente.');

  app.tokenRefresh = (async () => {
    const response = await fetch(`${SUPABASE_URL}/auth/v1/token?grant_type=refresh_token`, {
      method: 'POST',
      headers: { apikey: SUPABASE_KEY, 'Content-Type': 'application/json' },
      body: JSON.stringify({ refresh_token: app.session.refreshToken }),
    });
    const result = await jsonResponse(response);
    if (!response.ok) {
      clearSession();
      throw new Error('Sua sessão expirou. Faça login novamente.');
    }
    return saveSession(result);
  })();

  try { return await app.tokenRefresh; }
  finally { app.tokenRefresh = null; }
}

async function getAccessToken() {
  if (!app.session) throw new Error('Entre na sua conta para acessar o catálogo.');
  if (Date.now() >= app.session.expiresAt - 60_000) await refreshSession();
  return app.session.accessToken;
}

async function apiGet(route, retry = true) {
  const token = await getAccessToken();
  const response = await fetch(`${SUPABASE_URL}${route}`, {
    headers: { apikey: SUPABASE_KEY, Authorization: `Bearer ${token}`, Accept: 'application/json', 'Accept-Profile': 'public' },
  });
  if (response.status === 401 && retry) {
    try { await refreshSession(); return apiGet(route, false); }
    catch (_) { clearSession(); showLogin(); throw new Error('Sua sessão expirou. Faça login novamente.'); }
  }
  const result = await jsonResponse(response);
  if (!response.ok) throw new Error(supabaseError(response.status, result));
  if (!Array.isArray(result)) throw new Error('Resposta inesperada do banco de dados.');
  return result;
}

/** Aceita URL pública completa, caminho do Storage, nome do arquivo ou subpasta. */
function coverUrl(storedValue) {
  let path = String(storedValue || '').trim();
  if (!path) return '';
  if (/^https?:\/\//i.test(path)) return path.replace(/ /g, '%20');
  if (path.startsWith('/storage/v1/')) return `${SUPABASE_URL}${path.replace(/ /g, '%20')}`;
  if (path.startsWith(`${STORAGE_BUCKET}/`)) path = path.slice(STORAGE_BUCKET.length + 1);
  const encoded = path.split('/').filter(Boolean).map((segment) => encodeURIComponent(segment)).join('/');
  return encoded ? `${SUPABASE_URL}/storage/v1/object/public/${STORAGE_BUCKET}/${encoded}` : '';
}

function normalizeText(value) {
  return String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('pt-BR');
}

function svgIcon(name, className = 'icon') {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('class', className);
  svg.setAttribute('aria-hidden', 'true');
  const use = document.createElementNS('http://www.w3.org/2000/svg', 'use');
  use.setAttribute('href', `#i-${name}`);
  svg.append(use);
  return svg;
}

function showToast(message) {
  const toast = $('#toast');
  toast.textContent = message;
  toast.hidden = false;
  clearTimeout(app.toastTimer);
  app.toastTimer = setTimeout(() => { toast.hidden = true; }, 4500);
}

function showLogin() {
  $('#login-screen').hidden = false;
  $('#app-screen').hidden = true;
  $('#login-error').hidden = true;
  document.title = 'CineRate — Login';
  closeMobileMenu();
}

function showApp() {
  if (!app.session) { showLogin(); return; }
  $('#login-screen').hidden = true;
  $('#app-screen').hidden = false;
  $('#header-email').textContent = app.session.email || 'Usuário';
  $('#header-avatar').textContent = (app.session.email || 'U').charAt(0).toUpperCase();
  renderRoute();
}

function setLoginBusy(busy) {
  $('#login-submit').disabled = busy;
  $('#login-submit').replaceChildren(document.createTextNode(busy ? 'Entrando...' : 'Entrar '));
  if (!busy) $('#login-submit').append(svgIcon('chevron'));
  $('#login-email').disabled = busy;
  $('#login-password').disabled = busy;
}

async function handleLogin(event) {
  event.preventDefault();
  const email = $('#login-email').value.trim();
  const password = $('#login-password').value;
  const error = $('#login-error');
  error.hidden = true;
  if (!email || !password) { error.textContent = 'Preencha e-mail e senha.'; error.hidden = false; return; }
  if (!$('#login-email').checkValidity()) { error.textContent = 'Digite um e-mail válido.'; error.hidden = false; return; }
  setLoginBusy(true);
  try {
    await login(email, password);
    $('#login-password').value = '';
    if (!location.hash || location.hash === '#login') location.hash = '#inicio';
    showApp();
    if (!app.catalogLoaded) await loadCatalog();
  } catch (err) {
    error.textContent = err instanceof TypeError ? 'Não foi possível conectar ao Supabase. Confira sua conexão.' : err.message;
    error.hidden = false;
  } finally { setLoginBusy(false); }
}

function posterElement(movie, kind = '') {
  const poster = document.createElement('div');
  poster.className = `poster ${kind}`.trim();
  const placeholder = document.createElement('span');
  placeholder.className = 'poster-placeholder';
  const url = coverUrl(movie.imagem_url);
  placeholder.textContent = url ? 'Carregando capa...' : 'Capa indisponível';
  poster.append(placeholder);
  if (!url) return poster;

  poster.classList.add('poster--loading');
  const img = document.createElement('img');
  img.alt = `Capa do filme ${movie.titulo}`;
  img.loading = kind === 'poster--detail' ? 'eager' : 'lazy';
  img.decoding = 'async';
  img.addEventListener('load', () => { poster.classList.remove('poster--loading'); placeholder.remove(); });
  img.addEventListener('error', () => {
    poster.classList.remove('poster--loading');
    placeholder.textContent = 'Capa indisponível';
    img.remove();
  });
  img.src = url;
  poster.append(img);
  return poster;
}

function movieGenre(movie) { return app.genres.get(Number(movie.id_genero)) || 'Gênero não informado'; }
function movieYear(movie) { return Number(movie.ano_lancamento) > 0 ? String(movie.ano_lancamento) : 'Ano n/d'; }
function movieDuration(movie) { return Number(movie.duracao) > 0 ? `${movie.duracao} min` : 'Duração n/d'; }

function buildMovieCard(movie) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = 'movie-card';
  button.setAttribute('aria-label', `Abrir detalhes de ${movie.titulo}`);
  button.append(posterElement(movie));

  const info = document.createElement('div');
  info.className = 'movie-card-info';
  const title = document.createElement('h3');
  title.className = 'movie-card-title';
  title.textContent = movie.titulo;
  title.title = movie.titulo;
  const meta = document.createElement('p');
  meta.className = 'movie-meta';
  meta.textContent = `${movieYear(movie)}  ·  ${movieGenre(movie)}`;
  const duration = document.createElement('span');
  duration.className = 'movie-duration';
  duration.textContent = movieDuration(movie);
  const link = document.createElement('span');
  link.className = 'movie-link';
  link.append(svgIcon('star'), document.createTextNode('Ver filme →'));
  info.append(title, meta, duration, link);
  button.append(info);
  button.addEventListener('click', () => { location.hash = `#filme/${encodeURIComponent(movie.id_filme)}`; });
  return button;
}

function emptyState(title, description) {
  const wrapper = document.createElement('div');
  wrapper.className = 'empty-state';
  const heading = document.createElement('h3');
  heading.textContent = title;
  const text = document.createElement('p');
  text.textContent = description;
  wrapper.append(svgIcon('film'), heading, text);
  return wrapper;
}

function renderMovies() {
  const grid = $('#movies-grid');
  const query = normalizeText($('#search-input').value.trim());
  const selectedGenre = $('#genre-filter').value;
  const visible = app.movies.filter((movie) => {
    const matchesText = normalizeText(movie.titulo).includes(query);
    return matchesText && (!selectedGenre || movieGenre(movie) === selectedGenre);
  });
  $('#movie-count').textContent = String(visible.length);
  $('#catalog-status').textContent = app.catalogLoading ? 'Buscando filmes...' : `${visible.length} filme(s) encontrado(s)`;
  grid.replaceChildren();
  if (app.catalogLoading && !app.catalogLoaded) {
    grid.append(emptyState('Carregando catálogo...', 'Buscando os filmes cadastrados no Supabase.'));
  } else if (!visible.length) {
    grid.append(emptyState(app.movies.length ? 'Nenhum filme encontrado' : 'Nenhum filme disponível', app.movies.length ? 'Tente outro título ou selecione todos os gêneros.' : 'Nenhum filme foi retornado pelo banco. Confira os registros e as permissões de leitura.'));
  } else {
    const fragment = document.createDocumentFragment();
    visible.forEach((movie) => fragment.append(buildMovieCard(movie)));
    grid.append(fragment);
  }
}

function updateGenreFilter() {
  const select = $('#genre-filter');
  const selected = select.value;
  const options = [''].concat([...new Set(app.genres.values())].sort((a,b) => a.localeCompare(b,'pt-BR')));
  select.replaceChildren();
  for (const item of options) {
    const option = document.createElement('option');
    option.value = item;
    option.textContent = item || 'Todos os gêneros';
    select.append(option);
  }
  select.value = options.includes(selected) ? selected : '';
}

function setCatalogNotice(message) {
  $('#catalog-notice').textContent = message;
  $('#catalog-notice').hidden = !message;
}

async function loadCatalog() {
  if (!app.session || app.catalogLoading) return;
  app.catalogLoading = true;
  const version = ++app.loadVersion;
  $('#refresh-button').disabled = true;
  setCatalogNotice('');
  renderMovies();
  let warning = '';
  try {
    // Mesmos nomes de tabelas e colunas consultados no código Java original.
    const movieRows = await apiGet('/rest/v1/filme?select=id_filme,titulo,ano_lancamento,duracao,id_genero,imagem_url&order=titulo.asc');
    let genreRows = [];
    try {
      genreRows = await apiGet('/rest/v1/genero?select=id_genero,nome_genero&order=nome_genero.asc');
    } catch (err) { warning = 'Os gêneros não puderam ser consultados. Confira a política SELECT da tabela genero.'; }
    if (version !== app.loadVersion || !app.session) return;
    app.movies = movieRows.map((row) => ({
      id_filme: row.id_filme,
      titulo: String(row.titulo || 'Filme sem título'),
      ano_lancamento: row.ano_lancamento,
      duracao: row.duracao,
      id_genero: row.id_genero,
      imagem_url: row.imagem_url,
    }));
    app.genres = new Map(genreRows.map((row) => [Number(row.id_genero), String(row.nome_genero || '')]));
    app.catalogLoaded = true;
    updateGenreFilter();
    if (warning) setCatalogNotice(warning);
  } catch (err) {
    if (version === app.loadVersion && app.session) setCatalogNotice(err instanceof TypeError ? 'Falha de conexão com o Supabase. Confira a internet e tente atualizar.' : err.message);
  } finally {
    if (version === app.loadVersion) {
      app.catalogLoading = false;
      $('#refresh-button').disabled = false;
      renderMovies();
      if (location.hash.startsWith('#filme/')) renderRoute();
    }
  }
}

function renderMovieDetail(id) {
  const detail = $('#movie-detail');
  detail.replaceChildren();
  const movie = app.movies.find((item) => String(item.id_filme) === String(id));
  if (!movie) {
    detail.append(emptyState(app.catalogLoading ? 'Carregando filme...' : 'Filme não encontrado', app.catalogLoading ? 'Buscando os dados do filme no catálogo.' : 'Este filme não está disponível no catálogo atual.'));
    return;
  }

  const info = document.createElement('div');
  info.className = 'detail-info';
  const eyebrow = document.createElement('p');
  eyebrow.className = 'eyebrow';
  eyebrow.textContent = 'CONHEÇA O FILME';
  const heading = document.createElement('h1');
  heading.textContent = movie.titulo;
  const meta = document.createElement('div');
  meta.className = 'detail-meta';
  const year = document.createElement('span');
  year.textContent = movieYear(movie);
  const sep = document.createElement('span');
  sep.className = 'sep';
  sep.textContent = '•';
  const duration = document.createElement('span');
  duration.textContent = movieDuration(movie);
  meta.append(year, sep, duration);
  const genre = document.createElement('span');
  genre.className = 'genre-pill';
  genre.textContent = movieGenre(movie);
  const rule = document.createElement('div');
  rule.className = 'detail-rule';
  const subheading = document.createElement('h2');
  subheading.textContent = 'Pronto para avaliar?';
  const description = document.createElement('p');
  description.textContent = 'Na próxima etapa, esta tela será conectada ao sistema de avaliações.';
  const rateButton = document.createElement('button');
  rateButton.type = 'button';
  rateButton.className = 'btn btn--primary';
  rateButton.textContent = 'Avaliar este filme (em breve)';
  rateButton.addEventListener('click', () => showToast('A função de avaliação será implementada na próxima etapa.'));
  info.append(eyebrow, heading, meta, genre, rule, subheading, description, rateButton);
  detail.append(posterElement(movie, 'poster--detail'), info);
}

function renderPlaceholder(route) {
  const title = route === 'perfil' ? 'Meu perfil' : 'Sugerir filme';
  const description = route === 'perfil'
    ? 'Aqui ficarão suas avaliações e informações de perfil. A integração ainda será desenvolvida.'
    : 'Em breve, você poderá sugerir filmes que ainda não estão no catálogo.';
  $('#placeholder-title').textContent = title;
  $('#placeholder-description').textContent = description;
}

function renderRoute() {
  if (!app.session) { showLogin(); return; }
  const hash = decodeURIComponent(location.hash.slice(1) || 'inicio');
  const movieId = hash.startsWith('filme/') ? hash.slice('filme/'.length) : null;
  const route = movieId ? 'filme' : (['inicio','perfil','sugestao'].includes(hash) ? hash : 'inicio');
  $('#catalog-view').hidden = route !== 'inicio';
  $('#movie-view').hidden = route !== 'filme';
  $('#placeholder-view').hidden = !['perfil','sugestao'].includes(route);
  document.querySelectorAll('[data-route]').forEach((link) => {
    const active = link.dataset.route === (route === 'filme' ? 'inicio' : route);
    link.classList.toggle('is-active', active);
    if (active) link.setAttribute('aria-current', 'page'); else link.removeAttribute('aria-current');
  });
  if (route === 'filme') renderMovieDetail(movieId);
  if (route === 'perfil' || route === 'sugestao') renderPlaceholder(route);
  document.title = `CineRate — ${{inicio:'Catálogo de filmes',filme:'Detalhes do filme',perfil:'Meu perfil',sugestao:'Sugerir filme'}[route]}`;
  closeMobileMenu();
  window.scrollTo({ top: 0, behavior: 'instant' });
}

async function logout() {
  const token = app.session?.accessToken;
  clearSession();
  location.hash = '#inicio';
  $('#login-form').reset();
  showLogin();
  if (token) {
    try {
      await fetch(`${SUPABASE_URL}/auth/v1/logout`, { method: 'POST', headers: { apikey: SUPABASE_KEY, Authorization: `Bearer ${token}` } });
    } catch (_) { /* sessão já removida deste navegador */ }
  }
}

function closeMobileMenu() {
  $('#sidebar').classList.remove('is-open');
  $('#sidebar-backdrop').hidden = true;
  $('#mobile-menu-button').setAttribute('aria-expanded','false');
  $('#mobile-menu-button').setAttribute('aria-label','Abrir menu');
}

function toggleMobileMenu() {
  const open = !$('#sidebar').classList.contains('is-open');
  $('#sidebar').classList.toggle('is-open',open);
  $('#sidebar-backdrop').hidden = !open;
  $('#mobile-menu-button').setAttribute('aria-expanded',String(open));
  $('#mobile-menu-button').setAttribute('aria-label',open ? 'Fechar menu' : 'Abrir menu');
}

async function initialize() {
  $('#login-form').addEventListener('submit', handleLogin);
  $('#toggle-password').addEventListener('click', () => {
    const field = $('#login-password');
    const show = field.type === 'password';
    field.type = show ? 'text' : 'password';
    $('#toggle-password').setAttribute('aria-label', show ? 'Ocultar senha' : 'Mostrar senha');
    $('#toggle-password').setAttribute('aria-pressed', String(show));
    $('#toggle-password use').setAttribute('href', show ? '#i-eye-off' : '#i-eye');
  });
  $('#create-account').addEventListener('click', () => showToast('A tela de cadastro será desenvolvida na próxima etapa.'));
  $('#logout-button').addEventListener('click', logout);
  $('#search-input').addEventListener('input', renderMovies);
  $('#genre-filter').addEventListener('change', renderMovies);
  $('#refresh-button').addEventListener('click', loadCatalog);
  $('#back-to-catalog').addEventListener('click', () => { location.hash = '#inicio'; });
  $('#placeholder-back').addEventListener('click', () => { location.hash = '#inicio'; });
  $('#mobile-menu-button').addEventListener('click', toggleMobileMenu);
  $('#sidebar-backdrop').addEventListener('click', closeMobileMenu);
  document.addEventListener('keydown', (event) => { if (event.key === 'Escape') closeMobileMenu(); });
  window.addEventListener('hashchange', renderRoute);

  // Recupera a sessão ao atualizar a página, caso ela ainda esteja válida.
  if (!app.session) { showLogin(); return; }
  try {
    await getAccessToken();
    showApp();
    await loadCatalog();
  } catch (_) { clearSession(); showLogin(); }
}

initialize();
