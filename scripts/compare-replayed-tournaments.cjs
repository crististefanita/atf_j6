const fs = require('node:fs');
const assert = require('node:assert/strict');

function read(file) {
  const tournaments = new Map();
  let current;
  for (const [index, line] of fs.readFileSync(file, 'utf8').split(/\r?\n/).entries()) {
    let match = /^TOURNAMENT seed=(-?\d+) seats=(\d+) /.exec(line);
    if (match) {
      assert.equal(current, undefined, `${file}:${index + 1} nested tournament`);
      const startingStack = /startingStack=(\d+)/.exec(line);
      assert.ok(startingStack, `${file}:${index + 1} omitted starting stack`);
      const offset = /\bbuttonOffset=(-?\d+)/.exec(line);
      const anchor = /\bfirstButton=(\d+)/.exec(line);
      const seed = +match[1], seats = +match[2];
      current = { seed, seats, firstButton: anchor ? +anchor[1] : ((seed % seats) + seats) % seats,
        startingStack: +startingStack[1], dealSeeds: [], actions: [], stackHistory: [] };
      if (offset) current.buttonOffset = +offset[1];
      continue;
    }
    match = /^HAND (\d+) .* dealSeed=(-?\d+)$/.exec(line);
    if (match && current) {
      assert.equal(+match[1], current.dealSeeds.length + 1, `${file}:${index + 1} hand number gap`);
      current.dealSeeds.push(match[2]);
      continue;
    }
    match = /^DECISION seat=(\d+) policy=([^ ]+) action=([A-Z_]+) amount=(\d+)/.exec(line);
    if (match && current) current.actions.push({ seat: +match[1], policy: match[2], action: match[3], amount: +match[4] });
    match = /^STACKS \[([^\]]+)\]$/.exec(line);
    if (match && current) {
      const stacks = match[1].split(',').map(value => Number(value.trim()));
      assert.equal(stacks.length, current.seats);
      assert.ok(stacks.every(value => Number.isInteger(value) && value >= 0));
      assert.equal(stacks.reduce((sum, value) => sum + value, 0), current.seats * current.startingStack,
        `${file}:${index + 1} chip conservation failed`);
      current.stackHistory.push(stacks);
      continue;
    }
    match = /^COMPLETE winner=(\d+) hands=(\d+)$/.exec(line);
    if (match) {
      assert.ok(current, `${file}:${index + 1} completion without header`);
      assert.equal(current.dealSeeds.length, +match[2]);
      assert.equal(current.stackHistory.length, +match[2]);
      current.winner = +match[1];
      current.finalStacks = current.stackHistory.at(-1);
      assert.equal(current.finalStacks[current.winner], current.seats * current.startingStack);
      const key = `${current.seats}:${current.seed}`;
      assert.ok(!tournaments.has(key), `${file}:${index + 1} duplicate ${key}`);
      tournaments.set(key, current);
      current = undefined;
    }
  }
  assert.equal(current, undefined, `${file} ended mid-tournament`);
  assert.ok(tournaments.size, `${file} contained no tournaments`);
  return tournaments;
}

function compare(archivedPath, replayPath) {
  const archived = read(archivedPath), replay = read(replayPath);
  assert.deepEqual([...archived.keys()].sort(), [...replay.keys()].sort(), 'Replay must use the exact old seeds and table sizes');
  const bySize = {};
  for (const [key, oldRun] of archived) {
    const newRun = replay.get(key);
    assert.equal(oldRun.firstButton, newRun.firstButton, `${key} initial button differs`);
    const commonDeals = Math.min(oldRun.dealSeeds.length, newRun.dealSeeds.length);
    assert.deepEqual(oldRun.dealSeeds.slice(0, commonDeals), newRun.dealSeeds.slice(0, commonDeals),
      `${key} replay changed the common seeded-deal stream`);
    const actionPrefix = Math.min(oldRun.actions.length, newRun.actions.length);
    let sameActions = 0;
    for (; sameActions < actionPrefix; sameActions++) {
      const a = oldRun.actions[sameActions], b = newRun.actions[sameActions];
      if (a.seat !== b.seat || a.action !== b.action || a.amount !== b.amount) break;
    }
    const bucket = bySize[oldRun.seats] ??= { tournaments: 0, sameWinner: 0, changedWinner: 0,
      sameHandCount: 0, fullActionReplay: 0, commonHandSeeds: 0, comparedActions: 0,
      commonActionPrefix: 0, archivedHands: 0, replayHands: 0,
      archivedWinBySeat: {}, replayWinBySeat: {}, archivedActionCounts: {}, replayActionCounts: {} };
    bucket.tournaments++;
    bucket.sameWinner += oldRun.winner === newRun.winner ? 1 : 0;
    bucket.changedWinner += oldRun.winner === newRun.winner ? 0 : 1;
    bucket.sameHandCount += oldRun.dealSeeds.length === newRun.dealSeeds.length ? 1 : 0;
    bucket.fullActionReplay += sameActions === oldRun.actions.length && sameActions === newRun.actions.length ? 1 : 0;
    bucket.commonHandSeeds += commonDeals;
    bucket.comparedActions += actionPrefix;
    bucket.commonActionPrefix += sameActions;
    bucket.archivedHands += oldRun.dealSeeds.length;
    bucket.replayHands += newRun.dealSeeds.length;
    bucket.archivedWinBySeat[oldRun.winner] = (bucket.archivedWinBySeat[oldRun.winner] ?? 0) + 1;
    bucket.replayWinBySeat[newRun.winner] = (bucket.replayWinBySeat[newRun.winner] ?? 0) + 1;
    for (const action of oldRun.actions) bucket.archivedActionCounts[action.action] = (bucket.archivedActionCounts[action.action] ?? 0) + 1;
    for (const action of newRun.actions) bucket.replayActionCounts[action.action] = (bucket.replayActionCounts[action.action] ?? 0) + 1;
  }
  return { comparison: 'archived tournament trace vs current-code replay with legacy alternating TAG/check-call seats',
    archivedTrace: archivedPath, replayTrace: replayPath, seededDealPrefixVerified: true, resultsBySeatCount: bySize,
    caveat: 'Matching seeds reproduce shuffled card streams, but matching outcomes only tests replay compatibility. It does not show strategic improvement; compare separately controlled candidate cohorts for that.' };
}

try {
  const [archivedPath, replayPath] = process.argv.slice(2);
  if (!archivedPath || !replayPath || process.argv.length !== 4)
    throw new Error('Usage: node scripts/compare-replayed-tournaments.cjs archived.trace replay.trace');
  console.log(JSON.stringify(compare(archivedPath, replayPath), null, 2));
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
