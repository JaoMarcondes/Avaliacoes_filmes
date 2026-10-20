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
const REVIEWS_TABLE = 'avaliacao_web'; // Criada em sql/avaliacoes.sql

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
    userId: payload.user?.id || app.session?.userId || '',
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
  if (status === 403) return 'Sem permissão para consultar ou alterar dados. Confira as políticas RLS do Supabase.';
  if (status === 404 || code === 'pgrst205' || message.includes('schema cache')) return `Tabela do Supabase não encontrada. Execute o arquivo sql/avaliacoes.sql no SQL Editor e atualize o projeto.`;
  if (status === 409 || code === '23505') return 'Você já tem uma avaliação para este filme. Atualize a página e tente editar a existente.';
  if (status === 400) return `Consulta inválida (HTTP 400). Confira os nomes das tabelas e colunas.${message ? ' Detalhe: ' + message.slice(0, 140) : ''}`;
  return `Erro no Supabase (HTTP ${status}).${message ? ' ' + message.slice(0, 160) : ''}`;
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

/** Requisições REST autenticadas, com renovação automática da sessão. */
async function apiRequest(method, route, body, retry = true) {
  const token = await getAccessToken();
  const headers = {
    apikey: SUPABASE_KEY,
    Authorization: `Bearer ${token}`,
    Accept: 'application/json',
    'Accept-Profile': 'public',
  };
  if (method !== 'GET') {
    headers['Content-Profile'] = 'public';
    headers.Prefer = 'return=representation';
    if (body !== undefined) headers['Content-Type'] = 'application/json';
  }
  const response = await fetch(`${SUPABASE_URL}${route}`, {
    method,
    headers,
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  });
  if (response.status === 401 && retry) {
    try { await refreshSession(); return apiRequest(method, route, body, false); }
    catch (_) { clearSession(); showLogin(); throw new Error('Sua sessão expirou. Faça login novamente.'); }
  }
  const result = await jsonResponse(response);
  if (!response.ok) throw new Error(supabaseError(response.status, result));
  return result;
}
async function apiGet(route) {
  const rows = await apiRequest('GET', route);
  if (!Array.isArray(rows)) throw new Error('Resposta inesperada do banco de dados.');
  return rows;
}
async function ensureUserId() {
  if (app.session?.userId) return app.session.userId;
  const token = await getAccessToken();
  const response = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { apikey: SUPABASE_KEY, Authorization: `Bearer ${token}` },
  });
  const user = await jsonResponse(response);
  if (!response.ok || !user?.id) throw new Error('Não foi possível identificar seu usuário. Entre novamente.');
  app.session.userId = user.id;
  app.session.email = user.email || app.session.email;
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(app.session));
  return app.session.userId;
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

// ================= AVALIAÇÕES / SUPABASE =================
// Escala de 0 a 5 estrelas, com incrementos de 0,5. O banco valida os limites e a propriedade via RLS.
function reviewRoute(movieId) {
  return `/rest/v1/${REVIEWS_TABLE}?select=id_avaliacao,id_filme,id_usuario,nota,comentario,criado_em,atualizado_em&id_filme=eq.${encodeURIComponent(movieId)}&order=atualizado_em.desc`;
}
function profileRoute(userId) {
  return `/rest/v1/${REVIEWS_TABLE}?select=id_avaliacao,id_filme,id_usuario,nota,comentario,criado_em,atualizado_em&id_usuario=eq.${encodeURIComponent(userId)}&order=atualizado_em.desc`;
}
function reviewDate(value) {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleDateString('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' });
}
function formatAverage(value) { return value.toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 }); }
function noteError(container, error) {
  const message = document.createElement('p');
  message.className = 'review-error';
  message.setAttribute('role', 'alert');
  message.textContent = error instanceof TypeError ? 'Falha de conexão com o Supabase. Verifique a internet.' : error.message;
  container.replaceChildren(message);
}
/** Exibe cinco estrelas com preenchimento completo, vazio ou pela metade. */
function starsDisplay(value, extraClass = '') {
  const stars = document.createElement('span');
  stars.className = `stars-display ${extraClass}`.trim();
  stars.setAttribute('aria-label', `${formatAverage(Number(value))} de 5 estrelas`);
  const score = Math.round(Math.max(0, Math.min(5, Number(value) || 0)) * 2) / 2;
  // Para leitura, arredonda o preenchimento visual para a meia estrela mais próxima.
  for (let i = 1; i <= 5; i += 1) {
    const wrap = document.createElement('span');
    wrap.className = 'star-art';
    wrap.setAttribute('aria-hidden', 'true');
    const empty = svgIcon('star', 'star-shape star-shape--empty');
    const fill = svgIcon('star', 'star-shape star-shape--filled');
    const fraction = Math.max(0, Math.min(1, score - (i - 1)));
    fill.style.clipPath = `inset(0 ${100 - fraction * 100}% 0 0)`;
    wrap.append(empty, fill);
    stars.append(wrap);
  }
  return stars;
}

/** Cada metade da estrela é clicável; tabulação permite selecionar 0,5, 1, ... 5. */
function ratingButtons(initialValue, onChange) {
  const wrapper = document.createElement('div');
  wrapper.className = 'rating-picker';
  const group = document.createElement('div');
  group.className = 'rating-choices';
  group.setAttribute('role', 'radiogroup');
  group.setAttribute('aria-label', 'Selecione de 0,5 a 5 estrelas');
  let selected = initialValue;
  let hovered = null;
  const halves = [];
  const visuals = [];
  function paint() {
    const score = hovered === null ? selected : hovered;
    visuals.forEach((shape, index) => {
      const fraction = Math.max(0, Math.min(1, (score ?? 0) - index));
      shape.style.clipPath = `inset(0 ${100 - fraction * 100}% 0 0)`;
    });
    halves.forEach(({ button, value }) => {
      const checked = selected === value;
      button.setAttribute('aria-checked', String(checked));
      button.tabIndex = checked || ((selected === null || selected === 0) && value === 0.5) ? 0 : -1;
    });
    clear.setAttribute('aria-pressed', String(selected === 0));
  }
  function choose(value) {
    selected = value;
    hovered = null;
    paint();
    onChange(value);
  }
  for (let i = 1; i <= 5; i += 1) {
    const container = document.createElement('span');
    container.className = 'rating-star';
    const empty = svgIcon('star', 'star-shape star-shape--empty');
    const filled = svgIcon('star', 'star-shape star-shape--filled');
    visuals.push(filled);
    container.append(empty, filled);
    for (let h = 0; h < 2; h += 1) {
      const value = i - 0.5 + (h * 0.5);
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'rating-hit';
      const title = `${formatAverage(value)} de 5 estrelas`;
      button.setAttribute('role', 'radio');
      button.setAttribute('aria-label', `Nota ${title}`);
      button.title = title;
      button.addEventListener('mouseenter', () => { hovered = value; paint(); });
      button.addEventListener('focus', () => { hovered = value; paint(); });
      button.addEventListener('blur', () => { hovered = null; paint(); });
      button.addEventListener('click', () => { choose(value); });
      button.addEventListener('keydown', (event) => {
        if (!['ArrowLeft', 'ArrowDown', 'ArrowRight', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
        event.preventDefault();
        let next = selected ?? 0.5;
        if (event.key === 'Home') next = 0.5;
        else if (event.key === 'End') next = 5;
        else next = Math.max(0.5, Math.min(5, next + (['ArrowRight', 'ArrowUp'].includes(event.key) ? 0.5 : -0.5)));
        const target = halves.find((item) => item.value === next)?.button;
        choose(next);
        target?.focus();
      });
      halves.push({ button, value });
      container.append(button);
    }
    group.append(container);
  }
  const clear = document.createElement('button');
  clear.type = 'button';
  clear.className = 'rating-clear';
  clear.textContent = '0 estrelas';
  clear.setAttribute('aria-label', 'Selecionar nota zero de cinco estrelas');
  clear.addEventListener('click', () => choose(0));
  wrapper.append(group, clear);
  wrapper.addEventListener('mouseleave', () => { hovered = null; paint(); });
  wrapper.setDisabled = (busy) => {
    halves.forEach(({button}) => { button.disabled = busy; });
    clear.disabled = busy;
  };
  paint();
  return wrapper;
}

async function deleteReview(reviewId, onSuccess) {
  if (!window.confirm('Excluir sua avaliação? Essa ação não pode ser desfeita.')) return;
  const result = await apiRequest('DELETE', `/rest/v1/${REVIEWS_TABLE}?id_avaliacao=eq.${encodeURIComponent(reviewId)}`);
  if (!Array.isArray(result) || result.length === 0) throw new Error('Não foi possível excluir sua avaliação. Confira as permissões RLS.');
  showToast('Avaliação excluída.');
  await onSuccess();
}

function renderReviewsPanel(movie, panel, reviews, userId) {
  panel.replaceChildren();
  const mine = reviews.find((review) => review.id_usuario === userId);
  const summary = document.createElement('div');
  summary.className = 'reviews-heading';
  const titleArea = document.createElement('div');
  const label = document.createElement('p');
  label.className = 'eyebrow';
  label.textContent = 'A OPINIÃO DO PÚBLICO';
  const heading = document.createElement('h2');
  heading.textContent = 'Avaliações do filme';
  titleArea.append(label, heading);
  const stats = document.createElement('div');
  stats.className = 'review-stats';
  const average = document.createElement('strong');
  const mean = reviews.length ? reviews.reduce((sum, r) => sum + Number(r.nota), 0) / reviews.length : 0;
  average.textContent = reviews.length ? `${formatAverage(mean)}/5` : '—/5';
  const votes = document.createElement('span');
  votes.textContent = `${reviews.length} ${reviews.length === 1 ? 'avaliação' : 'avaliações'}`;
  stats.append(starsDisplay(mean, 'stars-display--summary'), average, votes);
  summary.append(titleArea, stats);
  panel.append(summary);

  const editor = document.createElement('section');
  editor.className = 'review-editor';
  const editorTitle = document.createElement('h3');
  editorTitle.textContent = mine ? 'Editar minha avaliação' : 'Qual é a sua nota?';
  const editorHelp = document.createElement('p');
  editorHelp.className = 'review-help';
  editorHelp.textContent = 'Clique na metade esquerda ou direita de cada estrela para avaliar em passos de 0,5. Depois, compartilhe sua opinião.';
  const form = document.createElement('form');
  form.className = 'review-form';
  form.noValidate = true;
  let selectedRating = mine ? Number(mine.nota) : null;
  const ratingLabel = document.createElement('p');
  ratingLabel.className = 'rating-label';
  ratingLabel.textContent = selectedRating === null ? 'Selecione uma nota' : `Sua nota: ${formatAverage(selectedRating)}/5`;
  const choices = ratingButtons(selectedRating, (value) => {
    selectedRating = value;
    ratingLabel.textContent = `Sua nota: ${formatAverage(value)}/5`;
    error.hidden = true;
  });
  const commentLabel = document.createElement('label');
  commentLabel.className = 'review-comment-label';
  commentLabel.textContent = 'Seu comentário (opcional)';
  const comment = document.createElement('textarea');
  comment.rows = 4;
  comment.maxLength = 1000;
  comment.placeholder = 'O que você achou da história, personagens ou final?';
  comment.value = mine?.comentario || '';
  commentLabel.append(comment);
  const error = document.createElement('p');
  error.className = 'form-error';
  error.setAttribute('role', 'alert');
  error.hidden = true;
  const actions = document.createElement('div');
  actions.className = 'review-actions';
  const saveButton = document.createElement('button');
  saveButton.type = 'submit';
  saveButton.className = 'btn btn--primary';
  saveButton.textContent = mine ? 'Salvar alterações' : 'Publicar avaliação';
  actions.append(saveButton);
  let deleteButton = null;
  if (mine) {
    deleteButton = document.createElement('button');
    deleteButton.type = 'button';
    deleteButton.className = 'btn btn--danger';
    deleteButton.textContent = 'Excluir minha avaliação';
    actions.append(deleteButton);
  }
  form.append(ratingLabel, choices, commentLabel, error, actions);
  editor.append(editorTitle, editorHelp, form);
  panel.append(editor);

  function setBusy(busy) {
    saveButton.disabled = busy;
    saveButton.textContent = busy ? 'Salvando...' : (mine ? 'Salvar alterações' : 'Publicar avaliação');
    if (deleteButton) deleteButton.disabled = busy;
    choices.setDisabled(busy);
    comment.disabled = busy;
  }
  function showError(err) {
    error.textContent = err instanceof TypeError ? 'Não foi possível conectar ao Supabase.' : err.message;
    error.hidden = false;
  }
  async function reload() {
    if (panel.isConnected && location.hash === `#filme/${encodeURIComponent(movie.id_filme)}`) await loadMovieReviews(movie.id_filme, panel);
  }
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    error.hidden = true;
    if (selectedRating === null) {
      showError(new Error('Escolha uma nota entre 0 e 5 estrelas antes de publicar.'));
      return;
    }
    if (!Number.isFinite(selectedRating) || selectedRating < 0 || selectedRating > 5 || selectedRating * 2 % 1 !== 0) { showError(new Error('Nota inválida. Escolha um valor de 0 a 5, em passos de meia estrela.')); return; }
    if (comment.value.length > 1000) { showError(new Error('Limite de 1.000 caracteres.')); return; }
    setBusy(true);
    try {
      let result;
      const data = { nota: selectedRating, comentario: comment.value.trim() };
      if (mine) {
        result = await apiRequest('PATCH', `/rest/v1/${REVIEWS_TABLE}?id_avaliacao=eq.${encodeURIComponent(mine.id_avaliacao)}`, data);
      } else {
        result = await apiRequest('POST', `/rest/v1/${REVIEWS_TABLE}`, { ...data, id_filme: Number(movie.id_filme) });
      }
      if (!Array.isArray(result) || result.length === 0) throw new Error('O banco não confirmou a gravação. Confira a política RLS.');
      showToast(mine ? 'Avaliação atualizada!' : 'Avaliação publicada!');
      await reload();
    } catch (err) { showError(err); }
    finally { setBusy(false); }
  });
  if (deleteButton) deleteButton.addEventListener('click', async () => {
    error.hidden = true;
    setBusy(true);
    try { await deleteReview(mine.id_avaliacao, reload); }
    catch (err) { showError(err); }
    finally { setBusy(false); }
  });

  const listHeading = document.createElement('h3');
  listHeading.className = 'reviews-list-heading';
  listHeading.textContent = 'O que estão dizendo';
  panel.append(listHeading);
  const list = document.createElement('div');
  list.className = 'reviews-list';
  if (!reviews.length) {
    list.append(emptyState('Ainda não há avaliações', 'Seja a primeira pessoa a avaliar este filme.'));
  } else {
    for (const review of reviews) {
      const card = document.createElement('article');
      card.className = 'review-card';
      const upper = document.createElement('div');
      upper.className = 'review-card-top';
      const author = document.createElement('strong');
      author.textContent = review.id_usuario === userId ? 'Você' : 'Espectador';
      const chip = document.createElement('span');
      chip.className = 'review-score';
      chip.append(starsDisplay(review.nota, 'stars-display--small'), document.createTextNode(`${formatAverage(Number(review.nota))}/5`));
      upper.append(author, chip);
      const date = document.createElement('small');
      date.textContent = reviewDate(review.atualizado_em || review.criado_em);
      card.append(upper, date);
      if (review.comentario) {
        const content = document.createElement('p');
        content.className = 'review-comment';
        content.textContent = review.comentario; // sempre textContent: sem injeção de HTML
        card.append(content);
      }
      list.append(card);
    }
  }
  panel.append(list);
}

async function loadMovieReviews(movieId, panel) {
  panel.replaceChildren();
  const loading = document.createElement('p');
  loading.className = 'review-loading';
  loading.textContent = 'Carregando avaliações...';
  panel.append(loading);
  try {
    const [userId, reviews] = await Promise.all([ensureUserId(), apiGet(reviewRoute(movieId))]);
    if (!panel.isConnected || location.hash !== `#filme/${encodeURIComponent(movieId)}`) return;
    const movie = app.movies.find((item) => String(item.id_filme) === String(movieId));
    if (movie) renderReviewsPanel(movie, panel, reviews, userId);
  } catch (error) { if (panel.isConnected) noteError(panel, error); }
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
  subheading.textContent = 'Avalie este filme';
  const description = document.createElement('p');
  description.textContent = 'Sua opinião faz parte da experiência CineRate. Veja as avaliações e publique a sua logo abaixo.';
  info.append(eyebrow, heading, meta, genre, rule, subheading, description);
  const panel = document.createElement('section');
  panel.className = 'reviews-panel';
  panel.setAttribute('aria-label', `Avaliações de ${movie.titulo}`);
  detail.append(posterElement(movie, 'poster--detail'), info, panel);
  loadMovieReviews(id, panel);
}

async function renderProfile() {
  const list = $('#profile-list');
  const notice = $('#profile-notice');
  notice.hidden = true;
  $('#profile-email').textContent = app.session?.email || '';
  list.replaceChildren(emptyState('Carregando avaliações...', 'Buscando suas notas no banco.'));
  try {
    const userId = await ensureUserId();
    const reviews = await apiGet(profileRoute(userId));
    if (location.hash !== '#perfil' || !app.session) return;
    list.replaceChildren();
    if (!reviews.length) {
      list.append(emptyState('Você ainda não avaliou filmes', 'Escolha um filme no catálogo e publique sua primeira avaliação.'));
      return;
    }
    for (const review of reviews) {
      const movie = app.movies.find((item) => String(item.id_filme) === String(review.id_filme));
      const row = document.createElement('article');
      row.className = 'profile-review-card';
      if (movie) row.append(posterElement(movie));
      const content = document.createElement('div');
      content.className = 'profile-review-text';
      const title = document.createElement('h2');
      title.textContent = movie?.titulo || `Filme #${review.id_filme}`;
      const score = document.createElement('div');
      score.className = 'review-score';
      score.append(starsDisplay(review.nota, 'stars-display--small'), document.createTextNode(`${formatAverage(Number(review.nota))}/5`));
      const date = document.createElement('small');
      date.textContent = reviewDate(review.atualizado_em || review.criado_em);
      content.append(title, score, date);
      if (review.comentario) {
        const text = document.createElement('p');
        text.textContent = review.comentario;
        content.append(text);
      }
      const actions = document.createElement('div');
      actions.className = 'profile-actions';
      const editButton = document.createElement('button');
      editButton.className = 'btn btn--secondary';
      editButton.textContent = 'Ver / editar';
      editButton.addEventListener('click', () => { location.hash = `#filme/${encodeURIComponent(review.id_filme)}`; });
      const deleteButton = document.createElement('button');
      deleteButton.className = 'btn btn--danger';
      deleteButton.textContent = 'Excluir';
      deleteButton.addEventListener('click', async () => {
        deleteButton.disabled = true;
        try { await deleteReview(review.id_avaliacao, renderProfile); }
        catch (error) { notice.textContent = error.message; notice.hidden = false; }
        finally { deleteButton.disabled = false; }
      });
      actions.append(editButton, deleteButton);
      content.append(actions);
      row.append(content);
      list.append(row);
    }
  } catch (error) {
    if (location.hash === '#perfil') {
      list.replaceChildren();
      notice.textContent = error instanceof TypeError ? 'Não foi possível conectar ao Supabase.' : error.message;
      notice.hidden = false;
    }
  }
}

function renderPlaceholder(route) {
  const title = 'Sugerir filme';
  const description = 'Em breve, você poderá sugerir filmes que ainda não estão no catálogo.';
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
  $('#profile-view').hidden = route !== 'perfil';
  $('#placeholder-view').hidden = route !== 'sugestao';
  document.querySelectorAll('[data-route]').forEach((link) => {
    const active = link.dataset.route === (route === 'filme' ? 'inicio' : route);
    link.classList.toggle('is-active', active);
    if (active) link.setAttribute('aria-current', 'page'); else link.removeAttribute('aria-current');
  });
  if (route === 'filme') renderMovieDetail(movieId);
  if (route === 'perfil') renderProfile();
  if (route === 'sugestao') renderPlaceholder(route);
  document.title = `CineRate — ${{inicio:'Catálogo de filmes',filme:'Avaliar filme',perfil:'Minhas avaliações',sugestao:'Sugerir filme'}[route]}`;
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
