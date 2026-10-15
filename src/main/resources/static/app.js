const state = {
  token: localStorage.getItem('smartservice-token'),
  user: null,
  page: 'dashboard',
  orders: [],
  ordersExpanded: false,
  knowledgeDocuments: [],
  knowledgeCategory: 'ALL',
  pending: { drawer: null, page: null },
  busy: { drawer: false, page: false }
};

const $ = id => document.getElementById(id);
const pageMeta = {
  dashboard: ['智能服务工作台', 'AGENT SERVICE CONSOLE'],
  agent: ['Agent 会话', 'BUSINESS COLLABORATION'],
  aftersale: ['售后工单', 'AFTER-SALE MANAGEMENT'],
  knowledge: ['知识中心', 'HYBRID RETRIEVAL'],
  observability: ['调用观测', 'RUNTIME OBSERVABILITY']
};
const categoryNames = {
  AFTER_SALE: '售后政策',
  LOGISTICS: '物流规则',
  ORDER: '订单规则',
  PRODUCT: '商品说明',
  PROMOTION: '优惠规则'
};

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  const response = await fetch(path, { ...options, headers });
  const body = await response.json();
  if (!response.ok || body.success === false) throw new Error(body.message || '请求失败');
  return body.data;
}

async function login() {
  try {
    const data = await api('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ username: 'demo', password: 'demo123' })
    });
    state.token = data.token;
    state.user = data.user;
    localStorage.setItem('smartservice-token', state.token);
  } catch (error) {
    localStorage.removeItem('smartservice-token');
    showToast('演示账号初始化失败：' + error.message);
    throw error;
  }
}

async function initialize() {
  await login();
  $('displayName').textContent = state.user.displayName;
  $('heroName').textContent = state.user.displayName;
  const today = new Intl.DateTimeFormat('zh-CN', {
    weekday: 'long', month: 'long', day: 'numeric'
  }).format(new Date());
  $('pageEyebrow').textContent = today.toUpperCase();
  await Promise.all([loadOrders(), loadSummary(), loadAfterSales(), loadKnowledgeDocuments(), loadRuntimeStatus()]);
}

async function loadRuntimeStatus() {
  try {
    const status = await api('/api/agent/status');
    $('runtimeModeBadge').innerHTML = status.aiEnabled
      ? `<i></i> Spring AI · ${escapeHtml(status.model)} · 5 个工具`
      : '<i></i> 规则降级模式 · 5 个工具';
  } catch (error) {
    $('runtimeModeBadge').innerHTML = '<i></i> 运行模式未知';
  }
}

function switchPage(page) {
  if (!pageMeta[page]) return;
  state.page = page;
  document.querySelectorAll('.page').forEach(node => node.classList.toggle('active', node.id === `page-${page}`));
  document.querySelectorAll('.nav-item').forEach(node => node.classList.toggle('active', node.dataset.page === page));
  $('pageTitle').textContent = pageMeta[page][0];
  $('pageEyebrow').textContent = pageMeta[page][1];
  window.scrollTo({ top: 0, behavior: 'smooth' });
  if (page === 'aftersale') loadAfterSales();
  if (page === 'knowledge') loadKnowledgeDocuments();
  if (page === 'observability') loadSummary();
  if (page === 'agent') setTimeout(() => $('agentPageInput').focus(), 80);
}

async function loadOrders() {
  try {
    state.orders = await api('/api/orders');
    renderOrders();
  } catch (error) {
    $('orderList').innerHTML = `<div class="table-empty">订单加载失败：${escapeHtml(error.message)}</div>`;
  }
}

function renderOrders() {
  const visible = state.ordersExpanded ? state.orders : state.orders.slice(0, 3);
  $('orderList').innerHTML = visible.length
    ? visible.map(order => `<div class="order-item">
        <div class="product-icon">${productIcon(order.productName)}</div>
        <div><strong>${escapeHtml(order.productName)}</strong><small>${escapeHtml(order.orderNo)} · ${formatTime(order.paidAt)}</small></div>
        <div class="order-side"><small class="status">${statusLabel(order.status)}</small><small class="price">¥${Number(order.amount).toFixed(2)}</small></div>
      </div>`).join('')
    : '<div class="table-empty">暂无订单</div>';
  $('showOrdersButton').textContent = state.orders.length > 3
    ? (state.ordersExpanded ? '收起订单 ↑' : '查看全部 →')
    : '已显示全部';
}

async function loadAfterSales() {
  try {
    const requests = await api('/api/after-sales');
    $('afterSaleTotal').textContent = requests.length;
    $('afterSalePending').textContent = requests.filter(item => item.status === 'PENDING_REVIEW').length;
    $('afterSaleCompleted').textContent = requests.filter(item => ['COMPLETED', 'REFUNDED', 'CLOSED'].includes(item.status)).length;
    $('afterSaleTable').innerHTML = requests.length
      ? `<div class="table-header"><span>工单 / 时间</span><span>关联订单</span><span>商品 / 原因</span><span>金额</span><span>状态</span></div>` +
        requests.map(item => `<div class="table-row">
          <span><strong>${escapeHtml(item.requestNo)}</strong><small>${formatTime(item.createdAt)}</small></span>
          <span><strong>${escapeHtml(item.orderNo)}</strong><small>原订单</small></span>
          <span><strong>${escapeHtml(item.productName)}</strong><small>${escapeHtml(item.reason)}</small></span>
          <span>¥${Number(item.amount).toFixed(2)}</span>
          <span><i class="table-status">${afterSaleStatus(item.status)}</i></span>
        </div>`).join('')
      : '<div class="table-empty">暂无售后工单。可通过 Agent 发起退货申请。</div>';
  } catch (error) {
    $('afterSaleTable').innerHTML = `<div class="table-empty">工单加载失败：${escapeHtml(error.message)}</div>`;
  }
}

async function loadKnowledgeDocuments() {
  try {
    state.knowledgeDocuments = await api('/api/knowledge/documents');
    renderKnowledgeDocuments();
  } catch (error) {
    $('knowledgeDocuments').innerHTML = `<div class="table-empty">知识库加载失败：${escapeHtml(error.message)}</div>`;
  }
}

function renderKnowledgeDocuments() {
  const documents = state.knowledgeCategory === 'ALL'
    ? state.knowledgeDocuments
    : state.knowledgeDocuments.filter(item => item.category === state.knowledgeCategory);
  $('knowledgeDocuments').innerHTML = documents.length
    ? documents.map(item => `<article class="doc-card">
        <span>${escapeHtml(categoryNames[item.category] || item.category)}</span>
        <strong>${escapeHtml(item.title)}</strong>
        <p>${escapeHtml(item.content)}</p>
      </article>`).join('')
    : '<div class="table-empty">该分类暂无文档</div>';
}

async function searchKnowledge(query) {
  const section = $('knowledgeResultsSection');
  const container = $('knowledgeResults');
  section.classList.remove('hidden');
  container.innerHTML = '<div class="skeleton"></div><div class="skeleton"></div>';
  try {
    const results = await api(`/api/knowledge/search?query=${encodeURIComponent(query)}&topK=3`);
    container.innerHTML = results.length
      ? results.map(item => `<article class="result-item">
          <div><strong>${escapeHtml(item.title)}</strong><p>${escapeHtml(item.content)}</p></div>
          <div class="score-group">
            <span>BM25 ${formatScore(item.bm25Score)}</span>
            <span>VECTOR ${formatScore(item.vectorScore)}</span>
            <span>RRF ${formatScore(item.rrfScore)}</span>
          </div>
        </article>`).join('')
      : '<div class="table-empty">没有找到相关文档</div>';
  } catch (error) {
    container.innerHTML = `<div class="table-empty">检索失败：${escapeHtml(error.message)}</div>`;
  }
}

async function loadSummary() {
  try {
    const summary = await api('/api/observability/summary');
    $('requestCount').textContent = summary.requests;
    $('toolCount').textContent = summary.toolCalls;
    $('guardCount').textContent = summary.confirmationsIntercepted;
    $('latencyValue').textContent = summary.averageLatencyMs;
    $('obsRequests').textContent = summary.requests;
    $('obsTools').textContent = summary.toolCalls;
    $('obsP95').textContent = summary.p95LatencyMs;
    $('obsGuards').textContent = summary.confirmationsIntercepted;
    renderDashboardTraces(summary.recent);
    renderObservability(summary.recent);
  } catch (error) {
    console.warn('观测数据加载失败', error);
  }
}

function renderDashboardTraces(traces) {
  $('traceEmpty').classList.toggle('hidden', traces.length > 0);
  $('traceList').classList.toggle('hidden', traces.length === 0);
  if (traces.length) {
    $('traceList').innerHTML = traces.map(trace => `<div class="trace-row">
      <strong>${intentLabel(trace.intent)} · ${statusText(trace.status)}</strong>
      <small>${trace.toolCalls} 次工具调用 · ${trace.latencyMs} ms · ${formatTime(trace.createdAt)}</small>
    </div>`).join('');
  }
}

function renderObservability(traces) {
  $('observabilityTable').innerHTML = traces.length
    ? `<div class="table-header"><span>意图 / 时间</span><span>状态</span><span>工具</span><span>文档</span><span>端到端延迟</span></div>` +
      traces.map(trace => `<div class="table-row">
        <span><strong>${intentLabel(trace.intent)}</strong><small>${formatTime(trace.createdAt)}</small></span>
        <span>${statusText(trace.status)}</span>
        <span>${trace.toolCalls} 次</span>
        <span>${trace.retrievalDocs} 篇</span>
        <span><strong>${trace.latencyMs} ms</strong></span>
      </div>`).join('')
    : '<div class="table-empty">暂无调用记录。完成一次 Agent 会话后，这里会显示完整链路。</div>';
}

function surfaceElements(surface) {
  return surface === 'page'
    ? { messages: $('agentPageMessages'), input: $('agentPageInput'), button: $('agentPageSendButton') }
    : { messages: $('messages'), input: $('messageInput'), button: $('sendButton') };
}

function openChat(prompt) {
  $('chatDrawer').classList.add('open');
  $('chatDrawer').setAttribute('aria-hidden', 'false');
  syncOverlay();
  if (prompt) {
    $('messageInput').value = prompt;
    setTimeout(() => sendMessage(prompt, null, 'drawer'), 160);
  } else {
    setTimeout(() => $('messageInput').focus(), 80);
  }
}

function closeChat() {
  $('chatDrawer').classList.remove('open');
  $('chatDrawer').setAttribute('aria-hidden', 'true');
  syncOverlay();
}

function openUsage() {
  $('usageModal').classList.add('open');
  $('usageModal').setAttribute('aria-hidden', 'false');
  syncOverlay();
}

function closeUsage() {
  $('usageModal').classList.remove('open');
  $('usageModal').setAttribute('aria-hidden', 'true');
  syncOverlay();
}

function syncOverlay() {
  const visible = $('chatDrawer').classList.contains('open') || $('usageModal').classList.contains('open');
  $('overlay').classList.toggle('open', visible);
}

function addMessage(role, content, extra = '', surface = 'drawer') {
  const target = surfaceElements(surface).messages;
  const node = document.createElement('div');
  node.className = `message ${role}`;
  node.innerHTML = role === 'user'
    ? `<div class="bubble">${escapeHtml(content)}</div>`
    : `<div class="mini-avatar">S</div><div class="bubble">${escapeHtml(content)}${extra}</div>`;
  target.appendChild(node);
  target.scrollTop = target.scrollHeight;
  return node;
}

function addStage(label, surface) {
  const target = surfaceElements(surface).messages;
  const node = document.createElement('div');
  node.className = 'message agent stage';
  node.innerHTML = `<div class="mini-avatar">·</div><div class="bubble">${escapeHtml(label)}…</div>`;
  target.appendChild(node);
  target.scrollTop = target.scrollHeight;
  return node;
}

async function sendMessage(message, confirmationToken = null, surface = 'drawer') {
  const text = String(message || '').trim();
  const elements = surfaceElements(surface);
  if (state.busy[surface] || !text) return;
  state.busy[surface] = true;
  elements.button.disabled = true;
  if (!confirmationToken) addMessage('user', text, '', surface);
  elements.input.value = '';
  const stages = [];
  let streamNode = null;
  let streamedText = '';
  let receivedResult = false;
  try {
    const response = await fetch('/api/agent/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${state.token}` },
      body: JSON.stringify({
        message: text,
        requestId: crypto.randomUUID(),
        confirmationToken
      })
    });
    if (!response.ok) throw new Error('Agent 服务暂时不可用');
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const blocks = buffer.split(/\r?\n\r?\n/);
      buffer = blocks.pop();
      for (const block of blocks) {
        const lines = block.split(/\r?\n/);
        const event = lines.find(line => line.startsWith('event:'))?.slice(6).trim();
        const raw = lines.filter(line => line.startsWith('data:')).map(line => line.slice(5).trim()).join('');
        if (!raw) continue;
        const data = JSON.parse(raw);
        if (event === 'stage') stages.push(addStage(data.label, surface));
        if (event === 'tool') stages.push(addStage(`已调用 ${data.name}（${data.latencyMs} ms）`, surface));
        if (event === 'token') {
          streamedText += data.content || '';
          if (!streamNode) streamNode = addMessage('agent', '', '', surface);
          streamNode.querySelector('.bubble').textContent = streamedText;
          surfaceElements(surface).messages.scrollTop = surfaceElements(surface).messages.scrollHeight;
        }
        if (event === 'result') {
          receivedResult = true;
          stages.forEach(node => node.remove());
          streamNode?.remove();
          renderResult(data, text, surface);
        }
      }
    }
  } catch (error) {
    stages.forEach(node => node.remove());
    streamNode?.remove();
    if (!receivedResult) addMessage('agent', '抱歉，' + error.message, '', surface);
  } finally {
    state.busy[surface] = false;
    elements.button.disabled = false;
    await Promise.all([loadSummary(), loadAfterSales()]);
  }
}

function renderResult(result, originalMessage, surface) {
  let extra = '';
  if (result.execution) {
    const model = result.execution.model ? ` · ${result.execution.model}` : '';
    const fallback = result.execution.fallback ? ' · 已自动降级' : '';
    const engine = result.execution.engine === 'SPRING_AI' ? 'Spring AI' : '规则引擎';
    extra += `<span class="source">运行：${escapeHtml(engine + model + fallback)}</span>`;
  }
  if (result.sources?.length) {
    const titles = result.sources.map(source => source.title).slice(0, 2).join('、');
    extra += `<span class="source">参考：${escapeHtml(titles)}</span>`;
  }
  if (result.confirmation) {
    state.pending[surface] = { message: originalMessage, token: result.confirmation.token };
    extra += `<div class="confirm-card"><strong>${escapeHtml(result.confirmation.summary)}</strong><button class="confirm-action">确认并提交申请</button></div>`;
  }
  const node = addMessage('agent', result.message, extra, surface);
  const button = node.querySelector('.confirm-action');
  if (button) {
    button.addEventListener('click', () => {
      const pending = state.pending[surface];
      if (!pending) return;
      button.disabled = true;
      button.textContent = '正在安全提交…';
      sendMessage(pending.message, pending.token, surface);
    });
  }
}

document.querySelectorAll('.nav-item').forEach(button => {
  button.addEventListener('click', () => switchPage(button.dataset.page));
});
document.querySelectorAll('[data-prompt]').forEach(button => {
  button.addEventListener('click', () => openChat(button.dataset.prompt));
});
document.querySelectorAll('[data-agent-prompt]').forEach(button => {
  button.addEventListener('click', () => {
    if (button.dataset.openAgent === 'true') switchPage('agent');
    const prompt = button.dataset.agentPrompt;
    $('agentPageInput').value = prompt;
    setTimeout(() => sendMessage(prompt, null, 'page'), 120);
  });
});

$('newChatButton').addEventListener('click', () => openChat());
$('closeChatButton').addEventListener('click', closeChat);
$('usageButton').addEventListener('click', openUsage);
$('closeUsageButton').addEventListener('click', closeUsage);
$('overlay').addEventListener('click', () => {
  closeChat();
  closeUsage();
});
$('chatForm').addEventListener('submit', event => {
  event.preventDefault();
  sendMessage($('messageInput').value, null, 'drawer');
});
$('agentPageForm').addEventListener('submit', event => {
  event.preventDefault();
  sendMessage($('agentPageInput').value, null, 'page');
});
$('messageInput').addEventListener('keydown', event => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    $('chatForm').requestSubmit();
  }
});
$('agentPageInput').addEventListener('keydown', event => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    $('agentPageForm').requestSubmit();
  }
});
$('showOrdersButton').addEventListener('click', () => {
  if (state.orders.length <= 3) {
    showToast('当前已展示全部订单');
    return;
  }
  state.ordersExpanded = !state.ordersExpanded;
  renderOrders();
});
$('refreshAfterSales').addEventListener('click', async () => {
  await loadAfterSales();
  showToast('售后工单已刷新');
});
$('refreshObservability').addEventListener('click', async () => {
  await loadSummary();
  showToast('观测指标已刷新');
});
$('knowledgeSearchForm').addEventListener('submit', event => {
  event.preventDefault();
  const query = $('knowledgeQuery').value.trim();
  if (query) searchKnowledge(query);
});
$('clearKnowledgeSearch').addEventListener('click', () => {
  $('knowledgeQuery').value = '';
  $('knowledgeResultsSection').classList.add('hidden');
});
$('categoryFilters').addEventListener('click', event => {
  const button = event.target.closest('[data-category]');
  if (!button) return;
  state.knowledgeCategory = button.dataset.category;
  document.querySelectorAll('[data-category]').forEach(node => node.classList.toggle('active', node === button));
  renderKnowledgeDocuments();
});
$('logoutButton').addEventListener('click', () => {
  localStorage.removeItem('smartservice-token');
  location.reload();
});

function showToast(text) {
  $('toast').textContent = text;
  $('toast').classList.add('show');
  setTimeout(() => $('toast').classList.remove('show'), 2800);
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>'"]/g, char => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
  })[char]);
}

function productIcon(name) {
  if (name.includes('耳机')) return '♬';
  if (name.includes('键盘')) return '⌨';
  return '⌁';
}

function statusLabel(status) {
  return ({ DELIVERED: '已签收', SHIPPED: '运输中', COMPLETED: '已完成', PAID: '已付款' })[status] || status;
}

function afterSaleStatus(status) {
  return ({ PENDING_REVIEW: '待审核', APPROVED: '已通过', REFUNDED: '已退款', COMPLETED: '已完成', CLOSED: '已关闭' })[status] || status;
}

function statusText(status) {
  return ({
    ANSWERED: '已回答',
    CONFIRMATION_REQUIRED: '等待确认',
    ACTION_COMPLETED: '执行成功',
    TRANSFERRED: '已转人工'
  })[status] || status;
}

function intentLabel(intent) {
  return ({
    QUERY_LOGISTICS: '物流查询',
    APPLY_REFUND: '退货申请',
    QUERY_POLICY: '政策问答',
    CANCEL_ORDER: '取消订单',
    TRANSFER_HUMAN: '人工服务',
    GENERAL: '通用对话'
  })[intent] || intent;
}

function formatScore(value) {
  return Number(value || 0).toFixed(4);
}

function formatTime(value) {
  if (!value) return '--';
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
  });
}

initialize().catch(error => console.error(error));
