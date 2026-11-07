const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

function parse(file, expectedOffset) {
  const tournaments = new Map();
  let current;
  for (const [lineIndex, line] of fs.readFileSync(file, 'utf8').split(/\r?\n/).entries()) {
    let match = /^TOURNAMENT seed=(-?\d+) seats=(\d+) buttonOffset=(-?\d+) firstButton=(\d+) /.exec(line);
    if (match) {
      assert.equal(current, undefined, `${file}:${lineIndex + 1} nested tournament`);
      const seed = +match[1], seats = +match[2], offset = +match[3], firstButton = +match[4];
      assert.equal(offset, expectedOffset, `${file}:${lineIndex + 1} wrong offset`);
      assert.equal(firstButton, ((seed % seats) + seats + (offset % seats) + seats) % seats,
        `${file}:${lineIndex + 1} first button is not the requested rotation`);
      current = { seed, seats, offset, firstButton, dealSeeds: [] };
      continue;
    }
    match = /^HAND (\d+) .* dealSeed=(-?\d+)$/.exec(line);
    if (match && current) {
      assert.equal(+match[1], current.dealSeeds.length + 1, `${file}:${lineIndex + 1} hand numbering gap`);
      current.dealSeeds.push(+match[2]);
      continue;
    }
    match = /^COMPLETE winner=(\d+) hands=(\d+)$/.exec(line);
    if (match) {
      assert.ok(current, `${file}:${lineIndex + 1} completed without a header`);
      assert.equal(current.dealSeeds.length, +match[2], `${file}:${lineIndex + 1} hand count mismatch`);
      const key = `${current.seats}:${current.seed}`;
      assert.ok(!tournaments.has(key), `${file}:${lineIndex + 1} duplicate ${key}`);
      tournaments.set(key, current);
      current = undefined;
    }
  }
  assert.equal(current, undefined, `${file} ended with an incomplete tournament`);
  assert.ok(tournaments.size > 0, `${file} has no completed tournaments`);
  return tournaments;
}

function sharedPrefix(left, right, label) {
  const count = Math.min(left.length, right.length);
  assert.deepEqual(left.slice(0, count), right.slice(0, count), `${label} used different deal seeds`);
}

function audit(root) {
  const records = new Map();
  const rows = [];
  for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const match = /^(standard|stress)-([a-z0-9-]+)-button(\d+)$/.exec(entry.name);
    if (!match) continue;
    const [, profile, style, offsetRaw] = match;
    const offset = +offsetRaw;
    const directory = path.join(root, entry.name);
    const candidate = parse(path.join(directory, 'candidate.trace'), offset);
    const control = parse(path.join(directory, 'control.trace'), offset);
    assert.deepEqual([...candidate.keys()].sort(), [...control.keys()].sort(), `${entry.name} candidate/control seeds differ`);
    for (const key of candidate.keys()) sharedPrefix(candidate.get(key).dealSeeds, control.get(key).dealSeeds,
      `${entry.name}/${key} candidate/control`);

    const group = `${profile}/${style}`;
    let rotations = records.get(group);
    if (!rotations) records.set(group, rotations = new Map());
    assert.ok(!rotations.has(offset), `${group} duplicate button offset ${offset}`);
    rotations.set(offset, { candidate, control });

    const comparison = JSON.parse(fs.readFileSync(path.join(directory, 'comparison.json'), 'utf8'));
    for (const [seatCount, stats] of Object.entries(comparison.resultsBySeatCount)) {
      rows.push({ profile, style, offset, seats: +seatCount, paired: stats.pairedTournaments,
        candidateWins: stats.candidateWins, controlWins: stats.controlWins, changedWinner: stats.changedWinner,
        deltaMeanStackPerSeatAcrossHands: stats.deltaMeanStackPerSeatAcrossHands,
        candidateDecisionMismatches: stats.candidateDecisionMismatches,
        candidateAggressiveHands: stats.candidateAggressiveHands,
        candidateAggressiveActions: stats.candidateAggressiveActions,
        candidateFoldWinsAfterAggression: stats.candidateFoldWinsAfterAggression,
        candidateFoldWinAfterAggressionRate: stats.candidateFoldWinAfterAggressionRate,
        candidateOpponentFoldsAfterAggression: stats.candidateOpponentFoldsAfterAggression });
    }
  }

  assert.ok(records.size > 0, 'No button-rotation policy groups were audited');
  for (const [group, offsets] of records) {
    assert.deepEqual([...offsets.keys()].sort((a, b) => a - b), [0, 1, 2, 3, 4, 5], `${group} misses button rotations`);
    const baseline = offsets.get(0).candidate;
    for (const [offset, pair] of offsets) {
      assert.deepEqual([...pair.candidate.keys()].sort(), [...baseline.keys()].sort(), `${group} changed seed set at offset ${offset}`);
      for (const key of baseline.keys()) sharedPrefix(baseline.get(key).dealSeeds, pair.candidate.get(key).dealSeeds,
        `${group}/${key}/offset${offset}`);
    }
    const seatCounts = [...new Set([...baseline.values()].map(tournament => tournament.seats))];
    for (const seats of seatCounts) {
      const starts = new Set();
      for (const [offset, pair] of offsets) {
        const first = [...pair.candidate.values()].find(tournament => tournament.seats === seats);
        starts.add(first.firstButton);
      }
      assert.equal(starts.size, seats, `${group}/${seats} did not visit every physical first-button seat`);
    }
  }
  return { profileStyleGroups: records.size, offsetsPerGroup: 6,
    seedAndDealStreamMatchedAcrossOffsets: true, candidateControlPairedAtEachOffset: true, comparisons: rows,
    caveat: 'Each offset reuses the same seeded deck sequence. Rotating the button changes which physical seat receives each card; it is a position counterfactual, not a claim that each seat retained the same private cards.' };
}

try {
  const root = process.argv[2];
  if (!root || process.argv.length !== 3) throw new Error('Usage: node scripts/audit-button-rotations.cjs evidence-directory');
  console.log(JSON.stringify(audit(root), null, 2));
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
