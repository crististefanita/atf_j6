const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const T95 = new Map([[1, Infinity], [2, 12.706], [3, 4.303], [4, 3.182], [5, 2.776],
  [6, 2.571], [7, 2.447], [8, 2.365], [9, 2.306], [10, 2.262], [11, 2.228],
  [12, 2.201], [13, 2.179], [14, 2.160], [15, 2.145], [16, 2.131], [17, 2.120],
  [18, 2.110], [19, 2.101], [20, 2.093], [21, 2.086], [22, 2.080], [23, 2.074],
  [24, 2.069], [25, 2.064], [26, 2.060], [27, 2.056], [28, 2.052], [29, 2.048],
  [30, 2.045]]);

function filesUnder(root) {
  return fs.readdirSync(root, { withFileTypes: true }).flatMap(entry => {
    const full = path.join(root, entry.name);
    return entry.isDirectory() ? filesUnder(full) : entry.name === 'comparison.json' ? [full] : [];
  });
}

function mean(values) { return values.reduce((sum, value) => sum + value, 0) / values.length; }

function interval95(values) {
  const n = values.length;
  if (n < 2) return { standardDeviation: null, interval95: null };
  const center = mean(values);
  const variance = values.reduce((sum, value) => sum + (value - center) ** 2, 0) / (n - 1);
  const standardDeviation = Math.sqrt(variance);
  const critical = T95.get(n - 1) ?? 1.96;
  const halfWidth = critical * standardDeviation / Math.sqrt(n);
  return { standardDeviation, interval95: [center - halfWidth, center + halfWidth] };
}

function readRows(root) {
  return filesUnder(root).map(file => {
    const row = JSON.parse(fs.readFileSync(file, 'utf8'));
    const offset = Number(path.basename(path.dirname(file)).match(/button(\d+)$/)?.[1]);
    assert.ok(Number.isInteger(offset), `cannot read button offset from ${file}`);
    assert.ok(Number.isFinite(Number(row.seed)) && Number.isInteger(row.seats), `invalid session key in ${file}`);
    assert.ok(Number.isFinite(row.pairedStackShareDelta), `missing paired delta in ${file}`);
    return { ...row, path: file, offset, seed: String(row.seed) };
  });
}

function summarizeGroup(rows, handLimit) {
  assert.ok(rows.length > 0, 'empty cash group');
  const bySeed = new Map();
  for (const row of rows) {
    const seedRows = bySeed.get(row.seed) ?? [];
    seedRows.push(row);
    bySeed.set(row.seed, seedRows);
  }
  const seedClusters = [...bySeed.entries()].map(([seed, items]) => ({
    seed,
    pairs: items.length,
    meanPairedStackShareDelta: mean(items.map(item => item.pairedStackShareDelta)),
  }));
  const clusterDeltas = seedClusters.map(cluster => cluster.meanPairedStackShareDelta);
  const stats = interval95(clusterDeltas);
  return {
    sessions: rows.length,
    independentSeedClusters: seedClusters.length,
    meanPairedStackShareDelta: mean(clusterDeltas),
    ...stats,
    positiveSeedClusters: clusterDeltas.filter(value => value > 0).length,
    zeroSeedClusters: clusterDeltas.filter(value => value === 0).length,
    negativeSeedClusters: clusterDeltas.filter(value => value < 0).length,
    meanCandidateStackShare: mean(rows.map(row => row.candidateStackShare)),
    meanControlSlotStackShare: mean(rows.map(row => row.controlSlotStackShare)),
    candidateHitHandLimit: rows.filter(row => row.candidateHands >= handLimit).length,
    controlHitHandLimit: rows.filter(row => row.controlHands >= handLimit).length,
    handLimit,
    seedClusters,
  };
}

function summarize(rows, handLimit = 50) {
  assert.ok(rows.length > 0, 'no cash comparison files found');
  assert.ok(Number.isInteger(handLimit) && handLimit > 0, 'hand limit must be a positive integer');
  const seatCounts = [...new Set(rows.map(row => row.seats))].sort((a, b) => a - b);
  const resultsBySeatCount = {};
  const resultsBySeatAndButton = {};
  for (const seats of seatCounts) {
    const seatRows = rows.filter(row => row.seats === seats);
    resultsBySeatCount[seats] = summarizeGroup(seatRows, handLimit);
    resultsBySeatAndButton[seats] = {};
    for (const offset of [...new Set(seatRows.map(row => row.offset))].sort((a, b) => a - b)) {
      resultsBySeatAndButton[seats][offset] = summarizeGroup(
        seatRows.filter(row => row.offset === offset), handLimit);
    }
  }
  return {
    metric: 'paired difference in end-of-session chip share for the candidate seat',
    uncertainty: 'Student-t 95% interval across independent seed clusters; button offsets are averaged within seed',
    sessions: rows.length,
    resultsBySeatCount,
    resultsBySeatAndButton,
    caveat: 'Exploratory only: few seed clusters, no rake, opponents do not adapt, and stack share is not EV or evidence of player strength.',
  };
}

if (require.main === module) {
  try {
    const args = process.argv.slice(2);
    const root = args.find(arg => !arg.startsWith('--'));
    const limitIndex = args.indexOf('--hand-limit');
    const handLimit = limitIndex >= 0 ? Number(args[limitIndex + 1]) : 50;
    assert.ok(root, 'usage: summarize-cash-sessions.cjs <cash-evaluation-root> [--hand-limit N]');
    process.stdout.write(`${JSON.stringify(summarize(readRows(root), handLimit), null, 2)}\n`);
  } catch (error) {
    console.error(error.stack || String(error));
    process.exitCode = 1;
  }
}

module.exports = { readRows, summarize };
