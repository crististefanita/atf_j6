const test = require('node:test');
const assert = require('node:assert/strict');
const { summarize } = require('./summarize-cash-sessions.cjs');

function row(seed, offset, delta, candidateHands = 50, controlHands = 50) {
  return { seed: String(seed), seats: 4, offset, pairedStackShareDelta: delta,
    candidateStackShare: 0.5 + delta / 2, controlSlotStackShare: 0.5 - delta / 2,
    candidateHands, controlHands, path: `s4-seed${seed}-button${offset}/comparison.json` };
}

test('clusters button offsets within each seed before estimating uncertainty', () => {
  const rows = [
    row(10, 0, 0.1, 50, 50), row(10, 1, 0.3, 40, 50),
    row(11, 0, -0.2), row(11, 1, 0),
    row(12, 0, 0.4), row(12, 1, 0.6),
    row(13, 0, 0.2), row(13, 1, 0),
  ];
  const result = summarize(rows, 50).resultsBySeatCount[4];
  assert.equal(result.sessions, 8);
  assert.equal(result.independentSeedClusters, 4);
  assert.equal(result.positiveSeedClusters, 3);
  assert.equal(result.negativeSeedClusters, 1);
  assert.equal(result.candidateHitHandLimit, 7);
  assert.equal(result.controlHitHandLimit, 8);
  assert.ok(Math.abs(result.meanPairedStackShareDelta - 0.175) < 1e-12);
  assert.ok(result.interval95[0] < result.meanPairedStackShareDelta);
  assert.ok(result.interval95[1] > result.meanPairedStackShareDelta);
});

test('rejects empty cohorts and invalid hand limits', () => {
  assert.throws(() => summarize([], 50), /no cash comparison files/);
  assert.throws(() => summarize([row(1, 0, 0)], 0), /positive integer/);
});
