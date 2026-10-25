import { useState } from 'react';
import { H1, H2, H3, Text, Stack, Row, Grid, Divider, Table, Stat, Button } from 'qoder/canvas';

// ============================================================
// 面板App UI 2.0 设计稿
// 诊断: 组件偏大 / 间距不统一 / 卡片墙 / AI 生成感
// 方案: 8dp 栅格 + 紧凑密度 + 统一圆角 + 收敛色彩 + 分组列表
// ============================================================

// ---- App 设计 token（Material 3 冷靛蓝，浅色）----
const C = {
  primary: '#4F5BD5',
  onPrimary: '#FFFFFF',
  primaryContainer: '#E4E2FF',
  onPrimaryContainer: '#141266',
  surface: '#FAF8FF',
  surfaceC: '#F1EEF9',
  surfaceHigh: '#EBE8F4',
  onSurface: '#1A1B21',
  onSurfaceVar: '#45464F',
  outlineVar: '#E1E1EC',
  error: '#BA1A1A',
  errorC: '#FFDAD6',
  onErrorC: '#410002',
  teal: '#00696D',
  tealC: '#CFF1F4',
  green: '#1B6C3A',
  greenC: '#D3F2DD',
  amber: '#7A5900',
  amberC: '#FFDF99',
};

const FONT = `-apple-system, 'Segoe UI', Roboto, 'Noto Sans SC', sans-serif`;
const MONO = `'JetBrains Mono', Consolas, monospace`;

// ---- 手机壳 ----
function Phone({ children, label, dim }: { children: any; label: string; dim?: boolean }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8, opacity: dim ? 0.85 : 1 }}>
      <div
        style={{
          width: 290, height: 600, borderRadius: 26, overflow: 'hidden',
          border: '6px solid #1A1B21', background: C.surface, fontFamily: FONT,
          display: 'flex', flexDirection: 'column', boxSizing: 'border-box',
        }}
      >
        <div style={{ height: 22, background: C.surface, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div style={{ width: 80, height: 4, borderRadius: 2, background: '#C9C7D4' }} />
        </div>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>{children}</div>
      </div>
      <Text size="small" tone="secondary">{label}</Text>
    </div>
  );
}

// ---- 通用小件 ----
function Bar({ title, right }: { title: string; right?: string }) {
  return (
    <div style={{ height: 48, display: 'flex', alignItems: 'center', padding: '0 14px', gap: 8, flexShrink: 0 }}>
      <div style={{ fontSize: 16, fontWeight: 700, color: C.onSurface, flex: 1 }}>{title}</div>
      {right && <div style={{ fontSize: 11, color: C.onSurfaceVar }}>{right}</div>}
    </div>
  );
}

function Chip({ label, active }: { label: string; active?: boolean }) {
  return (
    <span
      style={{
        fontSize: 10.5, padding: '3px 10px', borderRadius: 999, whiteSpace: 'nowrap',
        background: active ? C.primaryContainer : 'transparent',
        color: active ? C.onPrimaryContainer : C.onSurfaceVar,
        border: `1px solid ${active ? C.primaryContainer : C.outlineVar}`,
      }}
    >
      {label}
    </span>
  );
}

function StatusDot({ tone }: { tone: 'run' | 'ok' | 'off' | 'err' }) {
  const color = tone === 'run' ? C.green : tone === 'ok' ? C.primary : tone === 'err' ? C.error : '#9A99A6';
  return <span style={{ width: 7, height: 7, borderRadius: 4, background: color, display: 'inline-block', flexShrink: 0 }} />;
}

function Tag2({ text, bg, fg }: { text: string; bg: string; fg: string }) {
  return <span style={{ fontSize: 9, padding: '1px 6px', borderRadius: 5, background: bg, color: fg, fontWeight: 600 }}>{text}</span>;
}

// ---- 屏 A：任务首页（改后）----
function ScreenHome() {
  const tasks = [
    { name: '移动云盘5.08.py', cmd: 'task 移动云盘5.08.py', time: '12:15', tone: 'ok' as const, tag: ['已启用', C.greenC, C.green] },
    { name: 'workbuddy探险', cmd: 'task …/workbuddy_buddy_travel.py', time: '16:04', tone: 'run' as const, tag: ['运行中', C.tealC, C.teal] },
    { name: '花生日记v1.0.py', cmd: 'task 花生日记v1.0.py', time: '10:17', tone: 'ok' as const, tag: ['已启用', C.greenC, C.green] },
    { name: 'IQOO.js', cmd: 'task IQOO.js', time: '18:32', tone: 'ok' as const, tag: ['已启用', C.greenC, C.green] },
    { name: '得无开源修2.6.5.js', cmd: 'task 得无开源修2.6.4.js', time: '03:00', tone: 'off' as const, tag: ['已停用', C.amberC, C.amber] },
    { name: 'WorkBuddy签到2', cmd: 'task …/workbuddy_checkin.py', time: '09:46', tone: 'ok' as const, tag: ['已启用', C.greenC, C.green] },
  ];
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Bar title="青龙面板" right="2.15.20 · 在线" />
      <div style={{ display: 'flex', gap: 6, padding: '0 14px 8px' }}>
        <Chip label="全部 24" active />
        <Chip label="运行 1" />
        <Chip label="停用 2" />
        <Chip label="搜索" />
      </div>
      <div style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column', gap: 2, padding: '0 8px' }}>
        {tasks.map((t, i) => (
          <div
            key={i}
            style={{
              display: 'flex', alignItems: 'center', gap: 9, padding: '7px 8px',
              borderRadius: 10, background: i === 1 ? C.surfaceC : 'transparent',
            }}
          >
            <StatusDot tone={t.tone} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 12.5, fontWeight: 600, color: C.onSurface, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{t.name}</div>
              <div style={{ fontSize: 10, color: C.onSurfaceVar, fontFamily: MONO, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{t.cmd}</div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 2, flexShrink: 0 }}>
              <Tag2 text={t.tag[0]} bg={t.tag[1]} fg={t.tag[2]} />
              <div style={{ fontSize: 9.5, color: C.onSurfaceVar }}>{t.time}</div>
            </div>
          </div>
        ))}
      </div>
      <div style={{ height: 52, borderTop: `1px solid ${C.outlineVar}`, display: 'flex', alignItems: 'center', flexShrink: 0 }}>
        {['任务', '脚本', '依赖', '我的'].map((t, i) => (
          <div key={t} style={{ flex: 1, textAlign: 'center' }}>
            <div style={{ fontSize: 15, color: i === 0 ? C.primary : '#9A99A6', lineHeight: 1 }}>●</div>
            <div style={{ fontSize: 9, marginTop: 2, color: i === 0 ? C.primary : '#9A99A6', fontWeight: i === 0 ? 700 : 400 }}>{t}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ---- 屏 B：依赖页（改后）----
function ScreenDeps() {
  const deps = [
    { n: 'requests', t: 'PYTHON3', s: ['已安装', C.greenC, C.green] },
    { n: 'pycryptodome', t: 'PYTHON3', s: ['已安装', C.greenC, C.green] },
    { n: 'axios', t: 'NODEJS', s: ['安装中', C.tealC, C.teal] },
    { n: 'crypto-js', t: 'NODEJS', s: ['已安装', C.greenC, C.green] },
    { n: 'pngquant', t: 'LINUX', s: ['失败', C.errorC, C.error] },
    { n: 'dayjs', t: 'NODEJS', s: ['已安装', C.greenC, C.green] },
  ];
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Bar title="依赖包管理" right="24 项" />
      <div style={{ padding: '0 14px 8px' }}>
        <div style={{ height: 32, borderRadius: 9, border: `1px solid ${C.outlineVar}`, display: 'flex', alignItems: 'center', padding: '0 10px', gap: 6, background: C.surface }}>
          <span style={{ fontSize: 12, color: '#9A99A6' }}>⌕</span>
          <span style={{ fontSize: 11, color: '#9A99A6' }}>搜索依赖名称或备注</span>
        </div>
      </div>
      <div style={{ display: 'flex', gap: 5, padding: '0 14px 6px', overflow: 'hidden' }}>
        <Chip label="全部" active /><Chip label="Node" /><Chip label="Python" /><Chip label="Linux" /><Chip label="批量" />
      </div>
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 2, padding: '0 8px', overflow: 'hidden' }}>
        {deps.map((d, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 8px', borderRadius: 10, background: i === 2 ? C.surfaceC : 'transparent' }}>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 12.5, fontWeight: 600, color: C.onSurface, fontFamily: MONO }}>{d.n}</div>
              <div style={{ fontSize: 9.5, color: C.onSurfaceVar, marginTop: 1 }}>上次操作 10-24 21:07</div>
            </div>
            <Tag2 text={d.t} bg={C.surfaceHigh} fg={C.onSurfaceVar} />
            <Tag2 text={d.s[0]} bg={d.s[1]} fg={d.s[2]} />
          </div>
        ))}
      </div>
      <div style={{ margin: '0 14px 10px', padding: '6px 10px', borderRadius: 10, background: C.primaryContainer, display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
        <span style={{ fontSize: 11, fontWeight: 700, color: C.onPrimaryContainer, flex: 1 }}>已选 2 项</span>
        <span style={{ fontSize: 10.5, fontWeight: 700, color: C.error }}>卸载</span>
        <span style={{ fontSize: 10.5, fontWeight: 700, color: C.onPrimaryContainer }}>重装</span>
      </div>
    </div>
  );
}

// ---- 屏 C：仪表盘（改后）----
function ScreenDash() {
  const stats = [
    { k: '总任务', v: '24' }, { k: '运行中', v: '1' },
    { k: '今日成功', v: '19' }, { k: '今日失败', v: '2' },
  ];
  const trend = [3, 6, 4, 8, 5, 9, 7, 11, 6, 9, 12, 8];
  const ranks = [
    { n: 'IQOO.js', v: '43 次' }, { n: '移动云盘5.08.py', v: '31 次' }, { n: '花生日记v1.0.py', v: '28 次' },
  ];
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Bar title="仪表盘" right="聚合自任务列表" />
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6, padding: '0 14px 8px' }}>
        {stats.map(s => (
          <div key={s.k} style={{ background: C.surfaceC, borderRadius: 12, padding: '9px 11px' }}>
            <div style={{ fontSize: 18, fontWeight: 800, color: C.onSurface }}>{s.v}</div>
            <div style={{ fontSize: 10, color: C.onSurfaceVar }}>{s.k}</div>
          </div>
        ))}
      </div>
      <div style={{ margin: '0 14px 8px', padding: '9px 11px', background: C.surfaceC, borderRadius: 12 }}>
        <div style={{ fontSize: 10.5, fontWeight: 700, color: C.onSurfaceVar, marginBottom: 6 }}>近 12 次执行</div>
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 4, height: 52 }}>
          {trend.map((h, i) => (
            <div key={i} style={{ flex: 1, height: `${h * 8}%`, minHeight: 4, borderRadius: 3, background: i === trend.length - 1 ? C.primary : C.primaryContainer }} />
          ))}
        </div>
      </div>
      <div style={{ flex: 1, padding: '0 8px', overflow: 'hidden' }}>
        <div style={{ fontSize: 10.5, fontWeight: 700, color: C.onSurfaceVar, padding: '4px 8px 6px' }}>运行次数排行</div>
        {ranks.map((r, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '7px 8px', borderRadius: 10 }}>
            <span style={{ fontSize: 11, fontWeight: 800, color: C.primary, width: 16 }}>{i + 1}</span>
            <span style={{ flex: 1, fontSize: 12, fontWeight: 600, color: C.onSurface, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{r.n}</span>
            <span style={{ fontSize: 10.5, color: C.onSurfaceVar }}>{r.v}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ---- 屏 D：开发者日志（改后）----
function ScreenLog() {
  const logs = [
    { t: '20:54:19', l: 'GET 200 37ms', p: '/api/scripts?path=我的本子', err: false },
    { t: '20:54:19', l: 'GET 500 132ms', p: '/api/dependencies', err: true },
    { t: '20:54:19', l: 'GET 200 162ms', p: '/api/configs/files', err: false },
    { t: '20:54:19', l: 'GET 200 90ms', p: '/api/scripts', err: false },
    { t: '20:54:21', l: 'GET 404 83ms', p: '/api/dashboard/system', err: true },
    { t: '20:54:21', l: 'GET 200 309ms', p: '/api/subscriptions', err: false },
    { t: '20:54:44', l: 'GET 200 30ms', p: '/api/scripts/移动云盘5.08.py', err: false },
    { t: '20:55:13', l: 'PUT 200 21ms', p: '/api/scripts', err: false },
  ];
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Bar title="开发者日志" right="实时记录中" />
      <div style={{ display: 'flex', gap: 5, padding: '0 14px 8px' }}>
        <Chip label="全部 128" active /><Chip label="HTTP 96" /><Chip label="错误 9" />
      </div>
      <div style={{ flex: 1, padding: '0 8px', overflow: 'hidden' }}>
        {logs.map((l, i) => (
          <div key={i} style={{ padding: '5px 8px', borderBottom: `1px solid ${C.outlineVar}` }}>
            <div style={{ display: 'flex', gap: 6, alignItems: 'baseline' }}>
              <span style={{ fontSize: 9, color: '#9A99A6', fontFamily: MONO }}>{l.t}</span>
              <span style={{ fontSize: 9.5, fontFamily: MONO, fontWeight: 700, color: l.err ? C.error : C.primary }}>{l.l}</span>
            </div>
            <div style={{ fontSize: 10.5, fontFamily: MONO, color: l.err ? C.error : C.onSurface, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', marginTop: 1 }}>{l.p}</div>
          </div>
        ))}
      </div>
      <div style={{ height: 36, borderTop: `1px solid ${C.outlineVar}`, display: 'flex', alignItems: 'center', padding: '0 12px', gap: 12, flexShrink: 0 }}>
        <span style={{ fontSize: 10.5, color: C.onSurfaceVar }}>保留 7 天</span>
        <span style={{ fontSize: 10.5, color: C.primary, fontWeight: 700 }}>打开目录</span>
        <span style={{ fontSize: 10.5, color: C.error, fontWeight: 700 }}>清除</span>
      </div>
    </div>
  );
}

// ---- 屏 A0：任务首页（现状，对照用）----
function ScreenHomeBefore() {
  const rows = [
    { n: '移动云盘5.08.py', s: '已启用' }, { n: 'workbuddy探险', s: '运行中' }, { n: '花生日记v1.0.py', s: '已启用' },
  ];
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', background: '#F7F5FB' }}>
      <div style={{ height: 64, background: C.primary, display: 'flex', alignItems: 'flex-end', padding: '0 16px 10px' }}>
        <span style={{ fontSize: 19, fontWeight: 800, color: '#fff' }}>任务列表 (24)</span>
      </div>
      <div style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div style={{ background: '#fff', borderRadius: 16, padding: 14, boxShadow: '0 2px 8px rgba(0,0,0,0.08)' }}>
          <div style={{ fontSize: 13, fontWeight: 800, color: C.onSurface }}>开发者抓取状态卡片</div>
          <div style={{ fontSize: 10.5, color: C.onSurfaceVar, marginTop: 4 }}>完整记录 HTTP 请求、入参、响应体与系统报错…</div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 8 }}>
            <div style={{ width: 52, height: 28, borderRadius: 16, background: C.primary, position: 'relative' }}>
              <div style={{ width: 24, height: 24, borderRadius: 14, background: '#fff', position: 'absolute', right: 2, top: 2 }} />
            </div>
          </div>
        </div>
        {rows.map(r => (
          <div key={r.n} style={{ background: '#fff', borderRadius: 16, padding: 16, boxShadow: '0 2px 8px rgba(0,0,0,0.08)' }}>
            <div style={{ fontSize: 15, fontWeight: 800, color: C.onSurface }}>{r.n}</div>
            <div style={{ fontSize: 11, color: C.onSurfaceVar, marginTop: 5, fontFamily: MONO }}>task {r.n}</div>
            <div style={{ display: 'flex', gap: 6, marginTop: 12, alignItems: 'center' }}>
              <span style={{ fontSize: 10, padding: '4px 12px', borderRadius: 999, background: C.greenC, color: C.green, fontWeight: 700 }}>{r.s}</span>
              <span style={{ flex: 1 }} />
              <span style={{ fontSize: 11, padding: '8px 18px', borderRadius: 999, background: C.primary, color: '#fff', fontWeight: 700 }}>运行</span>
              <span style={{ fontSize: 11, padding: '8px 18px', borderRadius: 999, background: C.surfaceHigh, color: C.onSurfaceVar, fontWeight: 700 }}>日志</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ---- 主组件 ----
export default function PanelUIRedesign() {
  const [tab, setTab] = useState<'home' | 'deps' | 'dash' | 'log'>('home');
  const screens = {
    home: { el: <ScreenHome />, label: '任务首页（改后）', notes: ['列表行 72dp → 44dp，去卡片改分组行', '状态改 7dp 色点，右对齐标签+时间', '操作收进点按行后的底部动作条，不再每行双大按钮'] },
    deps: { el: <ScreenDeps />, label: '依赖页（改后）', notes: ['搜索框 56dp → 32dp，与 chips 同一栅格', '类型/状态双行 chips 合并为可滚动单行', '批量栏改为固定底部操作条，选中时浮现'] },
    dash: { el: <ScreenDash />, label: '仪表盘（改后）', notes: ['统计 4 卡 2×2，数值 18sp 为主视觉', '趋势用 12 根迷你柱，不引第三方图表', '排行改紧凑行， rank 数字做视觉锚点'] },
    log: { el: <ScreenLog />, label: '开发者日志（改后）', notes: ['单行一条：时间 + 动作/码/耗时 + 路径', '错误行红字，点击弹底部详情层', '底部固定 保留/打开目录/清除 三个动作'] },
  } as const;
  const cur = screens[tab];

  return (
    <Stack gap={20}>
      <Stack gap={6}>
        <H1>面板 App UI 2.0 设计稿</H1>
        <Text tone="secondary">现状：组件偏大、间距不统一、满屏 ElevatedCard 叠加，观感"AI 生成"。方案：Material 3 冷靛蓝 + 8dp 栅格 + 紧凑密度，下方四屏可切换预览，首页附改造前后对照。</Text>
      </Stack>

      <Grid columns={4} gap={12}>
        <Stat value="72→44dp" label="列表行高" />
        <Stat value="4 档" label="间距只留 4/8/12/16" />
        <Stat value="12/8dp" label="统一圆角(卡/控件)" />
        <Stat value="1 套" label="色板(收敛彩色容器)"></Stat>
      </Grid>

      <Divider />

      <Stack gap={10}>
        <H2>屏幕预览</H2>
        <Row gap={8}>
          {(['home', 'deps', 'dash', 'log'] as const).map(k => (
            <Button
              key={k}
              size="sm"
              rounded="pill"
              variant={tab === k ? 'primary' : 'secondary'}
              onClick={() => setTab(k)}
            >
              {k === 'home' ? '任务首页' : k === 'deps' ? '依赖页' : k === 'dash' ? '仪表盘' : '开发者日志'}
            </Button>
          ))}
        </Row>
        <Row gap={24} align="start">
          <Phone label={cur.label}>{cur.el}</Phone>
          <Stack gap={8} style={{ maxWidth: 380 }}>
            <H3>本屏改动点</H3>
            {cur.notes.map(n => (
              <Row key={n} gap={8} align="start">
                <span style={{ width: 6, height: 6, borderRadius: 3, background: C.primary, marginTop: 6, flexShrink: 0 }} />
                <Text size="small">{n}</Text>
              </Row>
            ))}
          </Stack>
        </Row>
      </Stack>

      <Divider />

      <Stack gap={10}>
        <H2>任务首页：改造前后对照</H2>
        <Row gap={24} align="start">
          <Phone label="现状：大卡片 + 每行双大按钮 + 卡片墙"><ScreenHomeBefore /></Phone>
          <Phone label="改后：紧凑分组行 + 底部导航"><ScreenHome /></Phone>
          <Stack gap={8} style={{ maxWidth: 360 }}>
            <H3>为什么现状显得"AI 生成"</H3>
            {[
              '每一块都用 ElevatedCard + 大圆角 + 阴影，层级全靠投影堆',
              '行高 72dp+、按钮 44dp 圆角胶囊，一屏只放得下 3 条数据',
              '同屏出现 3 种圆角、4 种内边距，没有栅格',
              '彩色容器标签滥用（绿/蓝/黄同屏），信息密度反而低',
            ].map(n => (
              <Row key={n} gap={8} align="start">
                <span style={{ width: 6, height: 6, borderRadius: 3, background: C.error, marginTop: 6, flexShrink: 0 }} />
                <Text size="small">{n}</Text>
              </Row>
            ))}
          </Stack>
        </Row>
      </Stack>

      <Divider />

      <Stack gap={10}>
        <H2>设计 Token（落地到 Compose Theme）</H2>
        <Table
          density="compact"
          headers={['Token', '现状', '改为', '落点']}
          rows={[
            ['列表行高', '72–88dp（卡片+大内边距）', '44–48dp 紧凑行', '自绘 TaskRow/DepRow 替代 ElevatedCard 列表'],
            ['间距', '10/12/14/16 混用', '只用 4/8/12/16', 'Spacing object 全局收敛'],
            ['圆角', '10/12/16/999 混用', '卡 12dp / 控件 8dp / Chip 全圆', 'MaterialTheme.shapes'],
            ['按钮高度', '32–44dp 不等', '36dp，文字 12sp', 'ButtonDefaults 统一 contentPadding'],
            ['Chip 高度', '26–30dp 不等', '26dp，文字 10.5sp', 'FilterChip defaults'],
            ['色板', '每页自配容器色', 'primary/surface/3 个语义色', 'MaterialTheme.colorScheme 单一来源'],
            ['顶栏', '64dp 双行标题', '48–56dp 单行 + 右侧元信息', 'TopAppBar titleMedium'],
          ]}
        />
      </Stack>

      <Text size="small" tone="secondary">确认风格后按 token 表逐屏落地：先首页+依赖页（数据密度最高），再仪表盘、设置、开发者日志。</Text>
    </Stack>
  );
}
