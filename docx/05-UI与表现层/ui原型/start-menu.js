const $ = (sel, el = document) => el.querySelector(sel);
const $$ = (sel, el = document) => Array.from(el.querySelectorAll(sel));

const panels = {
  empty: $('#panelEmpty'),
  newgame: $('#panelNewGame'),
  loadgame: $('#panelLoadGame'),
  multiplayer: $('#panelMultiplayer'),
  settings: $('#panelSettings'),
};

const saves = [
  { id: 'save_001', title: '第 3 舰队边境战', sub: '游戏日 12 · 16:00 · 普通 · 单人', detail: { faction: '人类联邦', money: 48200, fleets: 3, seed: 'A1B2C3' } },
  { id: 'save_002', title: '开局试跑', sub: '游戏日 1 · 02:00 · 普通 · 单人', detail: { faction: '新星财团', money: 128400, fleets: 8, seed: '随机' } },
  { id: 'save_003', title: '多人房间测试', sub: '游戏日 4 · 09:00 · 困难 · 合作', detail: { faction: '星门商会', money: 91200, fleets: 5, seed: 'MP-7788' } },
];

let selectedSaveId = null;

function showToast(msg) {
  const toast = $('#toast');
  toast.textContent = msg;
  toast.classList.add('is-show');
  window.clearTimeout(showToast._t);
  showToast._t = window.setTimeout(() => toast.classList.remove('is-show'), 1600);
}

function ripple(e) {
  const btn = e.currentTarget;
  const r = document.createElement('span');
  r.className = 'ripple';

  const rect = btn.getBoundingClientRect();
  const size = Math.max(rect.width, rect.height) * 1.2;
  r.style.width = `${size}px`;
  r.style.height = `${size}px`;
  r.style.left = `${e.clientX - rect.left - size / 2}px`;
  r.style.top = `${e.clientY - rect.top - size / 2}px`;

  btn.appendChild(r);
  window.setTimeout(() => r.remove(), 520);
}

function showPanel(key) {
  for (const [k, el] of Object.entries(panels)) {
    el.classList.toggle('is-hidden', k !== key);
  }
}

function setSegActive(btn) {
  const parent = btn.closest('.seg');
  if (!parent) return;
  for (const b of $$('.seg-btn', parent)) b.classList.remove('is-active');
  btn.classList.add('is-active');
}

function renderSaveList() {
  const list = $('#saveList');
  list.innerHTML = '';
  for (const s of saves) {
    const div = document.createElement('div');
    div.className = 'save-item' + (s.id === selectedSaveId ? ' is-active' : '');
    div.innerHTML = `<div class="save-title">${s.title}</div><div class="save-sub">${s.sub}</div>`;
    div.addEventListener('click', () => {
      selectedSaveId = s.id;
      renderSaveList();
      renderSaveDetail(s);
    });
    list.appendChild(div);
  }
}

function renderSaveDetail(s) {
  const el = $('#saveDetail');
  el.innerHTML = `
    <div style="display:flex; justify-content:space-between; gap:10px; align-items:baseline;">
      <div style="font-weight:900; font-size:16px;">${s.title}</div>
      <div class="chip">${s.id}</div>
    </div>
    <div class="muted" style="margin-top:6px;">${s.sub}</div>
    <div style="margin-top:14px; display:grid; gap:10px;">
      <div class="stat"><div class="k">势力</div><div class="v">${s.detail.faction}</div></div>
      <div class="stat"><div class="k">资金</div><div class="v">${s.detail.money.toLocaleString('zh-CN')}</div></div>
      <div class="stat"><div class="k">舰队</div><div class="v">${s.detail.fleets}</div></div>
      <div class="stat"><div class="k">种子</div><div class="v">${s.detail.seed}</div></div>
    </div>
  `;
}

function wireMenuButtons() {
  for (const btn of $('.menu-item[data-open]')) {
    btn.addEventListener('pointerdown', ripple);
    btn.addEventListener('click', () => {
      const key = btn.dataset.open;
      showPanel(key);
      showToast(`打开：${btn.textContent}`);
      if (key === 'loadgame') {
        renderSaveList();
      }
    });
  }

  for (const btn of $$('[data-back]')) {
    btn.addEventListener('click', () => {
      showPanel('empty');
    });
  }

  $('#btnStart').addEventListener('click', () => {
    const diff = $('#selDifficulty').value;
    const galaxy = $('#selGalaxy').value;
    const seed = $('#inputSeed').value.trim() || '随机';
    showToast(`开始新游戏：难度=${diff}，规模=${galaxy}，种子=${seed}（原型）`);
  });

  $('#btnLoad').addEventListener('click', () => {
    if (!selectedSaveId) return showToast('请先选择一个存档');
    showToast(`加载存档 ${selectedSaveId}（原型）`);
  });

  $('#btnDelete').addEventListener('click', () => {
    if (!selectedSaveId) return showToast('请先选择一个存档');
    showToast(`删除存档 ${selectedSaveId}（原型）`);
  });

  $('#btnMp').addEventListener('click', () => {
    showToast('多人连接（原型）：此处仅展示流程');
  });

  $('#btnSaveSettings').addEventListener('click', () => {
    showToast('设置已保存（原型）');
  });
}

function wireSegButtons() {
  for (const btn of $$('.seg-btn')) {
    btn.addEventListener('click', () => {
      setSegActive(btn);
    });
  }
}

function wireQuit() {
  const modal = $('#modalQuit');
  $('#btnQuit').addEventListener('click', () => {
    modal.classList.remove('is-hidden');
  });
  $('#btnQuitCancel').addEventListener('click', () => {
    modal.classList.add('is-hidden');
  });
  $('#btnQuitConfirm').addEventListener('click', () => {
    modal.classList.add('is-hidden');
    showToast('（原型）已确认退出');
  });

  modal.addEventListener('click', (e) => {
    if (e.target === modal) modal.classList.add('is-hidden');
  });
}

function wireTopButtons() {
  $('#btnLegal').addEventListener('click', () => showToast('许可信息（原型）'));
  $('#btnLang').addEventListener('click', () => showToast('语言切换（原型）'));
}

showPanel('empty');
wireMenuButtons();
wireSegButtons();
wireQuit();
wireTopButtons();
