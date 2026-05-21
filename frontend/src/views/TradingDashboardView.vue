<script setup lang="ts">
type MarketTick = {
  symbol: string
  name: string
  price: string
  change: string
  direction: 'up' | 'down'
  volume: string
  spread: string
  latency: string
  points: number[]
}

type Strategy = {
  name: string
  mode: string
  status: 'Running' | 'Guarded' | 'Paused'
  pnl: string
  exposure: string
  winRate: string
  linkedMarkets: string[]
}

type LinkSignal = {
  strategy: string
  market: string
  strength: number
  signal: string
  action: string
  tone: 'buy' | 'sell' | 'watch'
}

const marketTicks: MarketTick[] = [
  {
    symbol: 'BTC-USDT',
    name: 'Bitcoin Perpetual',
    price: '68,421.20',
    change: '+1.84%',
    direction: 'up',
    volume: '2.8B',
    spread: '0.8',
    latency: '41ms',
    points: [28, 31, 29, 36, 34, 38, 43, 41, 48, 52, 50, 57],
  },
  {
    symbol: 'ETH-USDT',
    name: 'Ethereum Perpetual',
    price: '3,742.88',
    change: '+0.92%',
    direction: 'up',
    volume: '1.1B',
    spread: '0.5',
    latency: '39ms',
    points: [42, 39, 44, 45, 47, 43, 51, 55, 53, 58, 56, 61],
  },
  {
    symbol: 'SOL-USDT',
    name: 'Solana Perpetual',
    price: '171.46',
    change: '-0.37%',
    direction: 'down',
    volume: '486M',
    spread: '1.2',
    latency: '44ms',
    points: [54, 56, 51, 49, 52, 48, 45, 46, 41, 39, 42, 38],
  },
  {
    symbol: 'NVDA',
    name: 'NVIDIA Equity',
    price: '1,034.18',
    change: '+2.11%',
    direction: 'up',
    volume: '73M',
    spread: '0.3',
    latency: '58ms',
    points: [35, 37, 41, 40, 45, 49, 46, 53, 59, 61, 64, 67],
  },
]

const strategies: Strategy[] = [
  {
    name: 'Momentum Breakout',
    mode: 'Trend',
    status: 'Running',
    pnl: '+$18,420',
    exposure: '$412K',
    winRate: '63%',
    linkedMarkets: ['BTC-USDT', 'ETH-USDT', 'NVDA'],
  },
  {
    name: 'Mean Reversion Grid',
    mode: 'Range',
    status: 'Guarded',
    pnl: '+$6,870',
    exposure: '$185K',
    winRate: '58%',
    linkedMarkets: ['ETH-USDT', 'SOL-USDT'],
  },
  {
    name: 'Volatility Hedge',
    mode: 'Risk',
    status: 'Running',
    pnl: '-$2,140',
    exposure: '$96K',
    winRate: '51%',
    linkedMarkets: ['BTC-USDT', 'SOL-USDT'],
  },
  {
    name: 'News Sentiment Scalper',
    mode: 'Event',
    status: 'Paused',
    pnl: '+$1,220',
    exposure: '$0',
    winRate: '46%',
    linkedMarkets: ['NVDA'],
  },
]

const linkSignals: LinkSignal[] = [
  {
    strategy: 'Momentum Breakout',
    market: 'BTC-USDT',
    strength: 92,
    signal: 'Volume expansion + trend confirmation',
    action: 'Increase long allocation',
    tone: 'buy',
  },
  {
    strategy: 'Mean Reversion Grid',
    market: 'SOL-USDT',
    strength: 74,
    signal: 'Range boundary touched',
    action: 'Tighten grid spacing',
    tone: 'watch',
  },
  {
    strategy: 'Volatility Hedge',
    market: 'ETH-USDT',
    strength: 68,
    signal: 'Correlation drift rising',
    action: 'Add protective hedge',
    tone: 'sell',
  },
  {
    strategy: 'News Sentiment Scalper',
    market: 'NVDA',
    strength: 39,
    signal: 'Event feed cooling',
    action: 'Keep paused',
    tone: 'watch',
  },
]

const riskMetrics = [
  { label: 'Net PnL', value: '+$24,370', detail: '+4.8% today', tone: 'positive' },
  { label: 'Open Exposure', value: '$693K', detail: '62% limit used', tone: 'neutral' },
  { label: 'Max Drawdown', value: '3.2%', detail: 'below 5% guardrail', tone: 'positive' },
  { label: 'Rejected Orders', value: '7', detail: '2 liquidity, 5 risk', tone: 'warning' },
]

const eventStream = [
  { time: '14:32:08', level: 'Fill', text: 'Momentum Breakout filled BTC-USDT long 2.4 lots at 68,418.5' },
  { time: '14:31:44', level: 'Risk', text: 'Volatility Hedge reduced SOL-USDT exposure after correlation breach' },
  { time: '14:30:19', level: 'Signal', text: 'Mean Reversion Grid detected ETH-USDT upper range compression' },
  { time: '14:28:53', level: 'Guard', text: 'News Sentiment Scalper paused until event confidence recovers' },
]

function sparklinePoints(points: number[]) {
  const width = 180
  const height = 56
  const min = Math.min(...points)
  const max = Math.max(...points)
  const span = max - min || 1

  return points
    .map((point, index) => {
      const x = (index / (points.length - 1)) * width
      const y = height - ((point - min) / span) * height
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
}
</script>

<template>
  <div class="trading-dashboard">
    <section class="trading-header">
      <div>
        <div class="dashboard-kicker">Automated Trading</div>
        <h1>Trading Command Dashboard</h1>
        <p>Live market state, strategy posture, signal linkage, and risk controls in one operating view.</p>
      </div>
      <div class="session-strip" aria-label="Trading session status">
        <div>
          <span>Session</span>
          <strong>US + Crypto</strong>
        </div>
        <div>
          <span>Data Feed</span>
          <strong class="is-online">Live</strong>
        </div>
        <div>
          <span>Risk Gate</span>
          <strong>Armed</strong>
        </div>
      </div>
    </section>

    <section class="risk-grid" aria-label="Risk summary">
      <article
        v-for="metric in riskMetrics"
        :key="metric.label"
        class="metric-tile"
        :class="`tone-${metric.tone}`"
      >
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
        <small>{{ metric.detail }}</small>
      </article>
    </section>

    <section class="dashboard-grid">
      <div class="market-panel">
        <div class="panel-heading">
          <div>
            <span class="panel-kicker">Markets</span>
            <h2>Realtime Quotes</h2>
          </div>
          <span class="feed-pill">42ms avg</span>
        </div>

        <div class="market-list">
          <article
            v-for="tick in marketTicks"
            :key="tick.symbol"
            class="market-row"
            :class="tick.direction"
          >
            <div class="market-identity">
              <strong>{{ tick.symbol }}</strong>
              <span>{{ tick.name }}</span>
            </div>
            <svg class="sparkline" viewBox="0 0 180 56" preserveAspectRatio="none" aria-hidden="true">
              <polyline
                :points="sparklinePoints(tick.points)"
                fill="none"
                stroke="currentColor"
                stroke-width="4"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
            <div class="market-price">
              <strong>{{ tick.price }}</strong>
              <span>{{ tick.change }}</span>
            </div>
            <div class="market-meta">
              <span>Vol {{ tick.volume }}</span>
              <span>Spread {{ tick.spread }}</span>
              <span>{{ tick.latency }}</span>
            </div>
          </article>
        </div>
      </div>

      <div class="strategy-panel">
        <div class="panel-heading">
          <div>
            <span class="panel-kicker">Strategies</span>
            <h2>Active Strategy Book</h2>
          </div>
          <span class="feed-pill">4 loaded</span>
        </div>

        <div class="strategy-list">
          <article
            v-for="strategy in strategies"
            :key="strategy.name"
            class="strategy-row"
          >
            <div class="strategy-title">
              <strong>{{ strategy.name }}</strong>
              <span>{{ strategy.mode }}</span>
            </div>
            <span class="strategy-status" :class="strategy.status.toLowerCase()">
              {{ strategy.status }}
            </span>
            <div class="strategy-stats">
              <span>PnL <strong>{{ strategy.pnl }}</strong></span>
              <span>Exposure <strong>{{ strategy.exposure }}</strong></span>
              <span>Win <strong>{{ strategy.winRate }}</strong></span>
            </div>
            <div class="market-tags">
              <span v-for="market in strategy.linkedMarkets" :key="market">{{ market }}</span>
            </div>
          </article>
        </div>
      </div>

      <div class="linkage-panel">
        <div class="panel-heading">
          <div>
            <span class="panel-kicker">Correlation</span>
            <h2>Market × Strategy Links</h2>
          </div>
          <span class="feed-pill">Signal mesh</span>
        </div>

        <div class="linkage-list">
          <article
            v-for="signal in linkSignals"
            :key="`${signal.strategy}-${signal.market}`"
            class="linkage-row"
          >
            <div class="linkage-node strategy-node">{{ signal.strategy }}</div>
            <div class="linkage-bridge">
              <div class="linkage-strength">
                <span :style="{ width: `${signal.strength}%` }"></span>
              </div>
              <strong>{{ signal.strength }}%</strong>
            </div>
            <div class="linkage-node market-node">{{ signal.market }}</div>
            <div class="linkage-detail">
              <span>{{ signal.signal }}</span>
              <strong :class="signal.tone">{{ signal.action }}</strong>
            </div>
          </article>
        </div>
      </div>

      <div class="execution-panel">
        <div class="panel-heading">
          <div>
            <span class="panel-kicker">Execution</span>
            <h2>Order & Guard Stream</h2>
          </div>
          <span class="feed-pill">17 open</span>
        </div>

        <div class="allocation-map" aria-label="Capital allocation">
          <div class="allocation-track">
            <span class="allocation-fill momentum"></span>
            <span class="allocation-fill grid"></span>
            <span class="allocation-fill hedge"></span>
            <span class="allocation-fill cash"></span>
          </div>
          <div class="allocation-legend">
            <span><i class="momentum"></i>Momentum 42%</span>
            <span><i class="grid"></i>Grid 24%</span>
            <span><i class="hedge"></i>Hedge 14%</span>
            <span><i class="cash"></i>Cash 20%</span>
          </div>
        </div>

        <div class="event-stream">
          <div v-for="event in eventStream" :key="`${event.time}-${event.level}`" class="event-row">
            <span class="event-time">{{ event.time }}</span>
            <span class="event-level">{{ event.level }}</span>
            <p>{{ event.text }}</p>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.trading-dashboard {
  width: min(1440px, 100%);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.trading-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(360px, 520px);
  gap: 24px;
  align-items: stretch;
  padding: 24px;
  border: 1px solid rgba(216, 227, 243, 0.92);
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.92), rgba(244, 248, 255, 0.94)),
    linear-gradient(rgba(95, 117, 158, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(95, 117, 158, 0.08) 1px, transparent 1px);
  background-size: auto, 28px 28px, 28px 28px;
  box-shadow: var(--shadow-card);
}

.dashboard-kicker,
.panel-kicker {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
  color: #4b6ea9;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.trading-header h1 {
  margin-top: 6px;
  font-size: 30px;
  line-height: 1.14;
  color: #172235;
  letter-spacing: 0;
}

.trading-header p {
  max-width: 640px;
  margin-top: 8px;
  color: var(--color-text-secondary);
}

.session-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border: 1px solid rgba(207, 219, 238, 0.9);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  overflow: hidden;
}

.session-strip div {
  min-width: 0;
  padding: 16px;
  border-right: 1px solid rgba(207, 219, 238, 0.82);
}

.session-strip div:last-child {
  border-right: none;
}

.session-strip span,
.metric-tile span,
.metric-tile small {
  display: block;
  color: var(--color-text-muted);
  font-size: 12px;
}

.session-strip strong {
  display: block;
  margin-top: 4px;
  font-size: 15px;
  color: #172235;
}

.session-strip .is-online {
  color: #0f766e;
}

.risk-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-tile,
.market-panel,
.strategy-panel,
.linkage-panel,
.execution-panel {
  border: 1px solid var(--color-border-subtle);
  border-radius: 8px;
  background: var(--color-surface-strong);
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(18px);
}

.metric-tile {
  padding: 16px;
  border-left-width: 4px;
}

.metric-tile strong {
  display: block;
  margin: 6px 0 2px;
  font-size: 22px;
  line-height: 1.1;
}

.tone-positive {
  border-left-color: #10b981;
}

.tone-neutral {
  border-left-color: #3b82f6;
}

.tone-warning {
  border-left-color: #f59e0b;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(560px, 1.24fr) minmax(360px, 0.76fr);
  grid-template-areas:
    'markets strategies'
    'links execution';
  gap: 16px;
}

.market-panel {
  grid-area: markets;
}

.strategy-panel {
  grid-area: strategies;
}

.linkage-panel {
  grid-area: links;
}

.execution-panel {
  grid-area: execution;
}

.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-bottom: 1px solid rgba(227, 234, 247, 0.86);
}

.panel-heading h2 {
  margin-top: 2px;
  font-size: 17px;
  color: #172235;
  letter-spacing: 0;
}

.feed-pill {
  flex-shrink: 0;
  padding: 5px 9px;
  border-radius: 999px;
  border: 1px solid #cfe0f7;
  background: #f4f8ff;
  color: #34507a;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 700;
}

.market-list,
.strategy-list,
.linkage-list,
.event-stream {
  display: flex;
  flex-direction: column;
}

.market-row {
  display: grid;
  grid-template-columns: minmax(150px, 0.85fr) minmax(160px, 1fr) minmax(120px, 0.7fr) minmax(150px, 0.7fr);
  gap: 16px;
  align-items: center;
  min-height: 92px;
  padding: 14px 18px;
  border-bottom: 1px solid rgba(227, 234, 247, 0.72);
}

.market-row:last-child,
.strategy-row:last-child,
.linkage-row:last-child,
.event-row:last-child {
  border-bottom: none;
}

.market-identity,
.market-price,
.strategy-title {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.market-identity strong,
.market-price strong,
.strategy-title strong {
  color: #172235;
  font-size: 15px;
}

.market-identity span,
.strategy-title span,
.market-meta span {
  color: var(--color-text-muted);
  font-size: 12px;
}

.sparkline {
  width: 100%;
  height: 56px;
  color: #0f766e;
}

.market-row.down .sparkline,
.market-row.down .market-price span {
  color: #dc2626;
}

.market-row.up .market-price span {
  color: #0f766e;
}

.market-meta {
  display: grid;
  gap: 2px;
  justify-items: end;
}

.strategy-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px 14px;
  padding: 15px 18px;
  border-bottom: 1px solid rgba(227, 234, 247, 0.72);
}

.strategy-status {
  align-self: start;
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.strategy-status.running {
  background: #dcfce7;
  color: #15803d;
}

.strategy-status.guarded {
  background: #fef3c7;
  color: #b45309;
}

.strategy-status.paused {
  background: #e2e8f0;
  color: #475569;
}

.strategy-stats {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.strategy-stats span {
  min-width: 0;
  padding: 8px;
  border-radius: 6px;
  background: #f8fafc;
  color: var(--color-text-muted);
  font-size: 12px;
}

.strategy-stats strong {
  display: block;
  margin-top: 2px;
  color: #172235;
  font-size: 13px;
}

.market-tags {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.market-tags span {
  padding: 4px 7px;
  border: 1px solid #dbe6f5;
  border-radius: 6px;
  background: #f7faff;
  color: #4b5b7c;
  font-family: var(--font-mono);
  font-size: 11px;
}

.linkage-row {
  display: grid;
  grid-template-columns: minmax(160px, 0.95fr) minmax(140px, 0.8fr) minmax(100px, 0.55fr) minmax(180px, 1fr);
  gap: 14px;
  align-items: center;
  min-height: 82px;
  padding: 14px 18px;
  border-bottom: 1px solid rgba(227, 234, 247, 0.72);
}

.linkage-node {
  min-width: 0;
  padding: 10px;
  border-radius: 6px;
  border: 1px solid #d9e5f4;
  background: #f8fbff;
  color: #172235;
  font-weight: 700;
}

.market-node {
  border-color: #cce7df;
  background: #f4fbf8;
}

.linkage-bridge {
  display: flex;
  align-items: center;
  gap: 8px;
}

.linkage-strength {
  flex: 1;
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #e2e8f0;
}

.linkage-strength span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #3b82f6, #10b981);
}

.linkage-bridge strong {
  width: 42px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: #34507a;
}

.linkage-detail {
  min-width: 0;
}

.linkage-detail span,
.event-row p {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.linkage-detail strong {
  display: block;
  margin-top: 3px;
  font-size: 13px;
}

.linkage-detail .buy {
  color: #0f766e;
}

.linkage-detail .sell {
  color: #dc2626;
}

.linkage-detail .watch {
  color: #b45309;
}

.allocation-map {
  padding: 16px 18px 10px;
}

.allocation-track {
  display: flex;
  height: 18px;
  overflow: hidden;
  border-radius: 999px;
  background: #e2e8f0;
}

.allocation-fill.momentum {
  width: 42%;
  background: #2563eb;
}

.allocation-fill.grid {
  width: 24%;
  background: #10b981;
}

.allocation-fill.hedge {
  width: 14%;
  background: #f59e0b;
}

.allocation-fill.cash {
  width: 20%;
  background: #94a3b8;
}

.allocation-legend {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
  margin-top: 12px;
}

.allocation-legend span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: var(--color-text-muted);
  font-size: 12px;
}

.allocation-legend i {
  width: 8px;
  height: 8px;
  border-radius: 999px;
}

.allocation-legend .momentum {
  background: #2563eb;
}

.allocation-legend .grid {
  background: #10b981;
}

.allocation-legend .hedge {
  background: #f59e0b;
}

.allocation-legend .cash {
  background: #94a3b8;
}

.event-row {
  display: grid;
  grid-template-columns: 72px 52px minmax(0, 1fr);
  gap: 10px;
  padding: 12px 18px;
  border-bottom: 1px solid rgba(227, 234, 247, 0.72);
}

.event-time {
  color: #64748b;
  font-family: var(--font-mono);
  font-size: 12px;
}

.event-level {
  color: #34507a;
  font-size: 12px;
  font-weight: 700;
}

@media (max-width: 1180px) {
  .trading-header,
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-grid {
    grid-template-areas:
      'markets'
      'strategies'
      'links'
      'execution';
  }
}

@media (max-width: 900px) {
  .risk-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .market-row,
  .linkage-row {
    grid-template-columns: 1fr;
  }

  .market-meta {
    justify-items: start;
  }
}

@media (max-width: 640px) {
  .trading-header {
    padding: 18px;
  }

  .trading-header h1 {
    font-size: 24px;
  }

  .session-strip,
  .risk-grid,
  .strategy-stats,
  .allocation-legend,
  .event-row {
    grid-template-columns: 1fr;
  }

  .session-strip div {
    border-right: none;
    border-bottom: 1px solid rgba(207, 219, 238, 0.82);
  }

  .session-strip div:last-child {
    border-bottom: none;
  }
}
</style>
