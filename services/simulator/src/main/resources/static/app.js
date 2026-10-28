'use strict';

const API = '/api/v1/simulator';
const MAX_FEED_ENTRIES = 250;

const TOPIC_KINDS = {
    'warehouse.movement-events': 'movement',
    'inventory.stock-level-events': 'stock',
    'reorder.purchase-order-events': 'order',
};

const state = {
    config: null,
    trafficRunning: false,
    feedPaused: false,
    pausedBuffer: [],
    seenFeedIds: new Set(),
    rows: new Map(),
    hiddenKinds: new Set(),
    scenarioRunning: false,
    scenarioLogSignature: '',
};

const $ = id => document.getElementById(id);

// --- Helpers ---

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, ch => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
    })[ch]);
}

function formatTime(iso) {
    if (!iso) return '';
    return new Date(iso).toLocaleTimeString([], {hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false});
}

function signed(n) {
    return n > 0 ? `+${n}` : `${n}`.replace('-', '−');
}

function truncate(text, length) {
    const value = String(text ?? '');
    return value.length > length ? value.slice(0, length - 1) + '…' : value;
}

function kindOf(topic) {
    if (topic.endsWith('.dlt')) return 'dlt';
    return TOPIC_KINDS[topic] ?? 'movement';
}

function toast(message, tone = 'info') {
    const el = document.createElement('div');
    el.className = 'toast';
    el.dataset.tone = tone;
    el.textContent = message;
    $('toasts').append(el);
    setTimeout(() => el.remove(), 5000);
}

async function call(method, path, body) {
    const response = await fetch(API + path, {
        method,
        headers: body === undefined ? {} : {'Content-Type': 'application/json'},
        body: body === undefined ? undefined : JSON.stringify(body),
    });

    if (!response.ok) {
        let detail = response.statusText;
        try {
            const problem = await response.json();
            detail = problem.detail || problem.title || detail;
        } catch {
            // not a problem detail
        }
        toast(detail, 'error');
        throw new Error(detail);
    }

    return response.status === 204 ? null : response.json();
}

// --- Theme ---

function initTheme() {
    let saved = null;
    try {
        saved = localStorage.getItem('theme');
    } catch {
        // storage unavailable
    }
    if (saved) document.documentElement.dataset.theme = saved;

    $('theme-toggle').addEventListener('click', () => {
        const current = document.documentElement.dataset.theme
            || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
        const next = current === 'dark' ? 'light' : 'dark';
        document.documentElement.dataset.theme = next;
        try {
            localStorage.setItem('theme', next);
        } catch {
            // storage unavailable
        }
    });
}

// --- Static config ---

function renderConfig(config) {
    $('links').innerHTML = config.links
        .map(link => `<a href="${escapeHtml(link.url)}" target="_blank" rel="noopener">${escapeHtml(link.name)} ↗</a>`)
        .join('');

    const rate = $('rate');
    rate.min = config.minRate;
    rate.max = config.maxRate;

    $('scenario-list').innerHTML = config.scenarios.map(scenario => `
        <div class="scenario" data-id="${escapeHtml(scenario.id)}">
            <div class="scenario-head">
                <strong>${escapeHtml(scenario.name)}</strong>
                <button class="button small primary" type="button" data-run="${escapeHtml(scenario.id)}">Run</button>
            </div>
            <p>${escapeHtml(scenario.description)}</p>
            ${scenario.id === 'burst' ? `
                <label class="scenario-size">Movements
                    <input type="number" id="burst-size" min="2" max="${config.maxBurstSize}" step="100"
                           value="${config.defaultBurstSize}">
                </label>` : ''}
        </div>`).join('');
}

// --- Snapshot ---

function renderSnapshot(snapshot) {
    renderServices(snapshot.services);
    renderTraffic(snapshot.traffic);
    renderSupplier(snapshot.supplier);
    renderScenario(snapshot.scenario);
    renderTopicCounts(snapshot.topicCounts);
    renderStock(snapshot.stock);
}

function renderServices(services) {
    $('services').innerHTML = Object.entries(services).map(([name, status]) => `
        <li title="${escapeHtml(name)}: ${escapeHtml(status)}">
            <span class="dot" data-status="${escapeHtml(status)}"></span>${escapeHtml(name.replace('-service', ''))}
        </li>`).join('');
}

function renderTraffic(traffic) {
    state.trafficRunning = traffic.running;

    const pill = $('traffic-state');
    pill.textContent = traffic.running ? (traffic.seeded ? 'Running' : 'Seeding…') : 'Stopped';
    pill.dataset.tone = traffic.running ? 'ok' : 'muted';

    const toggle = $('traffic-toggle');
    toggle.textContent = traffic.running ? 'Stop traffic' : 'Start traffic';
    toggle.classList.toggle('primary', !traffic.running);

    const rate = $('rate');
    if (document.activeElement !== rate) {
        rate.value = traffic.rate;
        $('rate-value').textContent = formatRate(traffic.rate);
    }

    $('traffic-sent').textContent = traffic.sent.toLocaleString();
    $('traffic-failed').textContent = traffic.failed.toLocaleString();

    const error = $('traffic-error');
    error.hidden = !traffic.lastError;
    error.textContent = traffic.lastError ? `Last error at ${formatTime(traffic.lastErrorAt)}: ${traffic.lastError}` : '';
}

function formatRate(rate) {
    return Number(rate).toFixed(rate < 10 ? 1 : 0);
}

function renderSupplier(supplier) {
    const pill = $('supplier-state');
    pill.textContent = supplier.enabled ? 'Delivering' : 'Paused';
    pill.dataset.tone = supplier.enabled ? 'ok' : 'warn';

    const enabled = $('supplier-enabled');
    if (document.activeElement !== enabled) enabled.checked = supplier.enabled;

    const leadTime = $('lead-time');
    if (document.activeElement !== leadTime) {
        const value = String(supplier.leadTimeSeconds);
        if (![...leadTime.options].some(option => option.value === value)) {
            leadTime.add(new Option(`${value} s`, value));
        }
        leadTime.value = value;
    }

    $('supplier-transit').textContent = supplier.ordersInTransit;
    $('supplier-shipments').textContent = supplier.shipments.toLocaleString();
    $('supplier-units').textContent = supplier.unitsDelivered.toLocaleString();
}

const SCENARIO_TONES = {RUNNING: 'info', SUCCEEDED: 'ok', FAILED: 'danger', CANCELLED: 'warn'};

function renderScenario(scenario) {
    state.scenarioRunning = scenario?.state === 'RUNNING';
    document.querySelectorAll('[data-run]').forEach(button => {
        button.disabled = state.scenarioRunning;
    });
    $('scenario-stop').hidden = !state.scenarioRunning;

    if (!scenario) return;

    $('scenario-title').textContent = scenario.name;
    const pill = $('scenario-state');
    pill.textContent = scenario.state.charAt(0) + scenario.state.slice(1).toLowerCase();
    pill.dataset.tone = SCENARIO_TONES[scenario.state] ?? 'muted';

    const signature = `${scenario.id}|${scenario.startedAt}|${scenario.log.length}|${scenario.state}`;
    if (signature === state.scenarioLogSignature) return;
    state.scenarioLogSignature = signature;

    const log = $('scenario-log');
    log.innerHTML = scenario.log.map(line => `
        <li data-kind="${line.kind}">
            <time>${formatTime(line.at)}</time>
            <span>${escapeHtml(line.text)}</span>
        </li>`).join('');
    log.scrollTop = log.scrollHeight;
}

function renderTopicCounts(counts) {
    const entries = Object.entries(counts);
    const deadLetters = entries.filter(([topic]) => topic.endsWith('.dlt')).reduce((sum, [, n]) => sum + n, 0);
    const main = Object.keys(TOPIC_KINDS).map(topic => [topic, counts[topic] ?? 0]);

    $('topic-counts').innerHTML = main
        .map(([topic, n]) => `<li data-kind="${TOPIC_KINDS[topic]}">${escapeHtml(topic)} <b>${n.toLocaleString()}</b></li>`)
        .concat(`<li data-kind="dlt">dead letter topics <b>${deadLetters.toLocaleString()}</b></li>`)
        .join('');
}

// --- Stock table ---

function stockStatus(row) {
    if (row.level == null) return 'unknown';
    if (row.level < 0) return 'negative';
    if (row.reorderPoint != null && row.level <= row.reorderPoint) return 'low';
    return 'ok';
}

function renderStock(rows) {
    const body = $('stock-body');
    if (rows.length === 0) {
        if (!body.querySelector('.empty-row')) {
            body.innerHTML = '<tr class="empty-row"><td colspan="4">No stock yet. Start traffic or run a scenario.</td></tr>';
        }
        state.rows.clear();
        return;
    }

    body.querySelector('.empty-row')?.remove();

    const ordered = [];
    let currentGroup = null;
    for (const row of rows) {
        const group = row.scenarioSku ? 'Scenario SKUs' : `Warehouse ${row.warehouseId}`;
        if (group !== currentGroup) {
            ordered.push(groupRow(group));
            currentGroup = group;
        }
        ordered.push(stockRow(row));
    }

    const live = new Set(ordered);
    for (const [key, tr] of state.rows) {
        if (!live.has(tr)) {
            tr.remove();
            state.rows.delete(key);
        }
    }

    // Re-append in order; moving an element that's already in place is cheap and keeps animations
    ordered.forEach((tr, index) => {
        if (body.children[index] !== tr) body.insertBefore(tr, body.children[index] ?? null);
    });
}

function groupRow(label) {
    const key = `group:${label}`;
    let tr = state.rows.get(key);
    if (!tr) {
        tr = document.createElement('tr');
        tr.className = 'group-row';
        tr.innerHTML = `<td colspan="4">${escapeHtml(label)}</td>`;
        state.rows.set(key, tr);
    }
    return tr;
}

function stockRow(row) {
    const key = `${row.warehouseId}|${row.sku}`;
    let tr = state.rows.get(key);
    if (!tr) {
        tr = document.createElement('tr');
        state.rows.set(key, tr);
    }

    const signature = JSON.stringify(row);
    if (tr.dataset.signature === signature) return tr;

    const previousLevel = tr.dataset.level ? Number(tr.dataset.level) : null;
    tr.dataset.signature = signature;
    tr.dataset.level = row.level ?? '';

    const status = stockStatus(row);
    tr.innerHTML = `
        <td>
            <div><span class="item-sku">${escapeHtml(row.sku)}</span>${row.scenarioSku ? '<span class="tag">DEMO</span>' : ''}</div>
            <div class="item-name">${escapeHtml(row.name ?? row.warehouseId)}</div>
        </td>
        <td class="num"><span class="level" data-status="${status}">${row.level ?? '–'}</span></td>
        <td>${levelBar(row, status)}</td>
        <td>${orderCell(row.order)}</td>`;

    if (previousLevel !== null && row.level != null && previousLevel !== row.level) {
        const cls = row.level > previousLevel ? 'flash-up' : 'flash-down';
        tr.classList.remove('flash-up', 'flash-down');
        void tr.offsetWidth;
        tr.classList.add(cls);
    }

    return tr;
}

function levelBar(row, status) {
    if (row.reorderUpTo == null) {
        return '<span class="muted">No reorder rule</span>';
    }

    const scale = Math.max(row.reorderUpTo, row.level ?? 0, 1);
    const fill = Math.max(0, Math.min(100, ((row.level ?? 0) / scale) * 100));
    const marker = (row.reorderPoint / scale) * 100;

    return `
        <div class="bar" title="Reorder at ${row.reorderPoint}, order up to ${row.reorderUpTo}">
            <div class="bar-fill" data-status="${status}" style="width:${status === 'negative' ? 100 : fill}%"></div>
            <div class="bar-marker" style="left:${marker}%"></div>
        </div>
        <div class="bar-caption">reorder at ${row.reorderPoint} · up to ${row.reorderUpTo}</div>`;
}

function orderCell(order) {
    if (!order) return '<span class="muted">–</span>';

    if (order.status === 'OPEN') {
        const progress = order.quantity > 0 ? (order.received / order.quantity) * 100 : 0;
        return `
            <div class="order">
                <span><span class="pill" data-tone="info">Open</span> ${order.received} / ${order.quantity} received</span>
                <div class="order-progress"><span style="width:${progress}%"></span></div>
            </div>`;
    }

    if (order.status === 'COMPLETED') {
        return `<div class="order"><span><span class="pill" data-tone="ok">Completed</span> ${order.quantity} units</span>
                <span class="muted">${formatTime(order.closedAt)}</span></div>`;
    }

    return `<div class="order"><span><span class="pill" data-tone="warn">Expired</span> ${order.received} / ${order.quantity}</span>
            <span class="muted">${formatTime(order.closedAt)}</span></div>`;
}

// --- Feed ---

function summarize(entry) {
    if (entry.deadLetter) {
        const who = entry.deadLetter.group ?? 'a consumer';
        const why = entry.deadLetter.exception ? `: ${truncate(entry.deadLetter.exception, 140)}` : '';
        return `Dead-lettered by ${who}${why}`;
    }

    const p = entry.payload;
    if (!p) return `Unreadable payload: ${truncate(entry.raw, 100)}`;

    switch (entry.type) {
        case 'warehouse.movement.recorded':
            return `${p.reason} ${signed(p.quantityDelta)} · ${p.sku} @ ${p.warehouseId}`;
        case 'inventory.stock-level.changed':
            return `${p.sku} @ ${p.warehouseId}: ${p.previousLevel} → ${p.newLevel} (${p.reason})`;
        case 'reorder.purchase-order.placed':
            return `Purchase order placed: ${p.quantity} × ${p.sku} @ ${p.warehouseId}`;
        case 'reorder.purchase-order.completed':
            return `Purchase order completed: ${p.orderedQuantity} × ${p.sku} @ ${p.warehouseId}`;
        case 'reorder.purchase-order.expired':
            return `Purchase order expired: ${p.receivedQuantity} of ${p.orderedQuantity} received · ${p.sku} @ ${p.warehouseId}`;
        default:
            return entry.type ?? 'Event without a type header';
    }
}

function feedElement(entry) {
    const kind = kindOf(entry.topic);
    const li = document.createElement('li');
    li.className = 'feed-entry';
    li.dataset.kind = kind;
    if (state.hiddenKinds.has(kind)) li.classList.add('hidden');

    const body = entry.payload ? JSON.stringify(entry.payload, null, 2) : entry.raw;
    li.innerHTML = `
        <div class="feed-meta">
            <span class="feed-topic" title="${escapeHtml(entry.topic)}">${escapeHtml(entry.topic)}</span>
            <span>p${entry.partition}@${entry.offset}</span>
            <time>${formatTime(entry.timestamp)}</time>
        </div>
        <div class="feed-summary">${escapeHtml(summarize(entry))}</div>
        <details>
            <summary>key ${escapeHtml(entry.key ?? '–')} · ${escapeHtml(entry.type ?? 'no type header')}</summary>
            <pre>${escapeHtml(body)}</pre>
        </details>`;
    return li;
}

function addFeedEntries(entries) {
    const fresh = entries.filter(entry => !state.seenFeedIds.has(entry.id));
    fresh.forEach(entry => state.seenFeedIds.add(entry.id));
    if (fresh.length === 0) return;

    if (state.feedPaused) {
        state.pausedBuffer.push(...fresh);
        state.pausedBuffer = state.pausedBuffer.slice(-MAX_FEED_ENTRIES);
        $('feed-paused').hidden = false;
        $('feed-paused').textContent = `Paused · ${state.pausedBuffer.length} new records waiting`;
        return;
    }

    prependToFeed(fresh);
}

function prependToFeed(entries) {
    const feed = $('feed');
    const fragment = document.createDocumentFragment();
    entries.slice(-MAX_FEED_ENTRIES).reverse().forEach(entry => fragment.append(feedElement(entry)));
    feed.prepend(fragment);

    while (feed.children.length > MAX_FEED_ENTRIES) {
        feed.lastElementChild.remove();
    }
    if (state.seenFeedIds.size > MAX_FEED_ENTRIES * 20) {
        state.seenFeedIds = new Set([...state.seenFeedIds].slice(-MAX_FEED_ENTRIES * 4));
    }
}

// --- Live connection ---

function connect() {
    const source = new EventSource(`${API}/stream`);
    const connection = $('connection');

    source.onopen = () => {
        connection.dataset.state = 'live';
        connection.textContent = 'Live';
    };
    source.onerror = () => {
        connection.dataset.state = 'connecting';
        connection.textContent = 'Reconnecting…';
    };
    source.addEventListener('snapshot', event => renderSnapshot(JSON.parse(event.data)));
    source.addEventListener('feed', event => addFeedEntries(JSON.parse(event.data)));
}

// --- Controls ---

function bindControls() {
    $('traffic-toggle').addEventListener('click', async () => {
        const status = await call('POST', state.trafficRunning ? '/traffic/stop' : '/traffic/start');
        renderTraffic(status);
    });

    const rate = $('rate');
    rate.addEventListener('input', () => {
        $('rate-value').textContent = formatRate(rate.value);
    });
    rate.addEventListener('change', async () => {
        renderTraffic(await call('PUT', '/traffic', {rate: Number(rate.value)}));
        rate.blur();
    });

    const updateSupplier = async () => {
        renderSupplier(await call('PUT', '/supplier', {
            enabled: $('supplier-enabled').checked,
            leadTimeSeconds: Number($('lead-time').value),
        }));
    };
    $('supplier-enabled').addEventListener('change', updateSupplier);
    $('lead-time').addEventListener('change', updateSupplier);

    $('scenario-list').addEventListener('click', async event => {
        const button = event.target.closest('[data-run]');
        if (!button) return;

        const id = button.dataset.run;
        const body = id === 'burst' ? {size: Number($('burst-size').value)} : {};
        renderScenario(await call('POST', `/scenarios/${encodeURIComponent(id)}`, body));
    });

    $('scenario-stop').addEventListener('click', () => call('DELETE', '/scenarios/current'));

    $('feed-pause').addEventListener('click', () => {
        state.feedPaused = !state.feedPaused;
        $('feed-pause').textContent = state.feedPaused ? 'Resume' : 'Pause';
        if (!state.feedPaused) {
            prependToFeed(state.pausedBuffer);
            state.pausedBuffer = [];
            $('feed-paused').hidden = true;
        } else {
            $('feed-paused').hidden = false;
            $('feed-paused').textContent = 'Paused';
        }
    });

    $('feed-filters').addEventListener('change', event => {
        const kind = event.target.value;
        if (event.target.checked) state.hiddenKinds.delete(kind);
        else state.hiddenKinds.add(kind);

        document.querySelectorAll(`.feed-entry[data-kind="${kind}"]`)
            .forEach(li => li.classList.toggle('hidden', !event.target.checked));
    });
}

async function init() {
    initTheme();
    bindControls();
    try {
        state.config = await (await fetch(`${API}/config`)).json();
        renderConfig(state.config);
    } catch (e) {
        toast('Could not load the simulator configuration', 'error');
    }
    connect();
}

init();
