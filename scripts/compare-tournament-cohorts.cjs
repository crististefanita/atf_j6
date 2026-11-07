const fs = require('node:fs');
const assert = require('node:assert/strict');

function read(file) {
  const completed = new Map();
  let current;
  for (const [index, line] of fs.readFileSync(file, 'utf8').split(/\r?\n/).entries()) {
    let match = /^TOURNAMENT seed=(-?\d+) seats=(\d+) /.exec(line);
    if (match) {
      assert.equal(current, undefined, `${file}:${index + 1} started before prior tournament completed`);
      const rules = /startingStack=(\d+)/.exec(line);
      assert.ok(rules, `${file}:${index + 1} tournament header omitted starting stack`);
      const offset = /\bbuttonOffset=(-?\d+)/.exec(line);
      const anchor = /\bfirstButton=(\d+)/.exec(line);
      current = { seed: +match[1], seats: +match[2], startingStack: +rules[1],
        buttonOffset: offset ? +offset[1] : 0,
        firstButton: anchor ? +anchor[1] : ((+match[1] % +match[2]) + +match[2]) % +match[2], stackHistory: [],
        actions: {}, versions: {}, versionsBySeat: {}, candidateDecisions: [] };
    }
    match = /^HAND (\d+) /.exec(line);
    if (match && current) {
      current.hand = { number: +match[1], candidateAggressiveSeats: new Set(), candidateAggressiveActionsBySeat: {},
        lastCandidateAggressor: null, opponentFoldsAfterCandidateAggression: 0,
        opponentFoldsAfterCandidateAggressionBySeat: {}, foldWinAwards: [] };
      current.handHistory ??= [];
      current.handHistory.push(current.hand);
    }
    match = /^DECISION seat=(\d+) policy=([^ ]+) action=([A-Z_]+) amount=(\d+)/.exec(line);
    if (match && current) {
      const seat = +match[1];
      current.versionsBySeat[seat] ??= new Set();
      current.versionsBySeat[seat].add(match[2]);
      if ((seat + ((current.seed % 4) + 4) % 4) % 4 === 3) {
        current.actions[match[3]] = (current.actions[match[3]] ?? 0) + 1;
        current.versions[match[2]] = (current.versions[match[2]] ?? 0) + 1;
        current.candidateDecisions.push({ seat, policy: match[2], action: match[3], amount: +match[4] });
        if ((match[3] === 'BET' || match[3] === 'RAISE') && current.hand) {
          current.hand.candidateAggressiveSeats.add(seat);
          current.hand.candidateAggressiveActionsBySeat[seat] = (current.hand.candidateAggressiveActionsBySeat[seat] ?? 0) + 1;
          current.hand.lastCandidateAggressor = seat;
        }
      } else if (match[3] === 'FOLD' && current.hand && current.hand.lastCandidateAggressor !== null) {
        current.hand.opponentFoldsAfterCandidateAggression++;
        const aggressor = current.hand.lastCandidateAggressor;
        current.hand.opponentFoldsAfterCandidateAggressionBySeat[aggressor] =
          (current.hand.opponentFoldsAfterCandidateAggressionBySeat[aggressor] ?? 0) + 1;
      }
      if (match[3] === 'FOLD' && current.hand && current.hand.lastCandidateAggressor === seat)
        current.hand.lastCandidateAggressor = null;
    }
    match = /^FOLD_WIN \[([^\]]+)\]$/.exec(line);
    if (match && current?.hand) current.hand.foldWinAwards = match[1].split(',').map(value => Number(value.trim()));
    match = /^STACKS \[([^\]]+)\]$/.exec(line);
    if (match && current) {
      const stacks = match[1].split(',').map(value => Number(value.trim()));
      assert.equal(stacks.length, current.seats, `${file}:${index + 1} stack count mismatch`);
      assert.ok(stacks.every(value => Number.isInteger(value) && value >= 0));
      assert.equal(stacks.reduce((sum, value) => sum + value, 0), current.startingStack * current.seats,
        `${file}:${index + 1} chip conservation mismatch`);
      current.stackHistory.push(stacks);
    }
    match = /^COMPLETE winner=(\d+) hands=(\d+)$/.exec(line);
    if (match) {
      assert.ok(current, `${file}:${index + 1} completed without a tournament header`);
      assert.equal(current.stackHistory.length, +match[2], `${file}:${index + 1} hand and stack trace counts differ`);
      const key = `${current.seats}:${current.seed}`;
      assert.ok(!completed.has(key), `${file}:${index + 1} duplicated ${key}`);
      const finalStacks = current.stackHistory.at(-1);
      assert.equal(finalStacks.filter(stack => stack > 0).length, 1, `${file}:${index + 1} did not end with one survivor`);
      assert.equal(finalStacks[+match[1]], current.startingStack * current.seats, `${file}:${index + 1} winner stack mismatch`);
      completed.set(key, { ...current, winner: +match[1], hands: +match[2], finalStacks });
      current = undefined;
    }
  }
  assert.equal(current, undefined, `${file} ended with an incomplete tournament`);
  assert.ok(completed.size > 0, `${file} has no completed tournaments`);
  return completed;
}

function compare(candidatePath, controlPath) {
  const candidate = read(candidatePath), control = read(controlPath);
  assert.deepEqual([...candidate.keys()].sort(), [...control.keys()].sort(), 'Cohorts need identical table sizes and seeds');
  const result = {}, resultByPosition = {};
  for (const key of candidate.keys()) {
    const a = candidate.get(key), b = control.get(key);
    assert.equal(a.buttonOffset, b.buttonOffset, `${key} cohorts need the same button offset`);
    assert.equal(a.firstButton, b.firstButton, `${key} cohorts need the same initial button`);
    // Match Java Math.floorMod for signed seeds.
    const target = new Set(Array.from({ length: a.seats }, (_, seat) => seat)
      .filter(seat => (seat + ((a.seed % 4) + 4) % 4) % 4 === 3));
    assert.ok(target.size > 0, `${key} contains no paired candidate seats`);
    for (let seat = 0; seat < a.seats; seat++) {
      if (target.has(seat)) continue;
      assert.deepEqual([...(a.versionsBySeat[seat] ?? [])].sort(), [...(b.versionsBySeat[seat] ?? [])].sort(),
        `${key} changed a non-candidate policy at seat ${seat}; reject this cohort pair`);
    }
    const bucket = result[a.seats] ??= { pairedTournaments: 0, candidateSeats: 0, candidateWins: 0,
      controlWins: 0, sameWinner: 0, changedWinner: 0, candidateHands: 0, controlHands: 0,
      candidateAverageFinishChipsPerSeat: 0, controlAverageFinishChipsPerSeat: 0,
      candidateMeanStackPerSeatAcrossHands: 0, controlMeanStackPerSeatAcrossHands: 0,
      candidateAggressiveHands: 0, candidateAggressiveActions: 0, candidateFoldWinsAfterAggression: 0,
      candidateOpponentFoldsAfterAggression: 0, controlAggressiveHands: 0, controlAggressiveActions: 0,
      controlFoldWinsAfterAggression: 0, controlOpponentFoldsAfterAggression: 0,
      candidateActions: {}, controlActions: {}, candidatePolicyVersions: {}, controlPolicyVersions: {} };
    const candidateFinish = [...target].reduce((sum, seat) => sum + a.finalStacks[seat], 0) / target.size;
    const controlFinish = [...target].reduce((sum, seat) => sum + b.finalStacks[seat], 0) / target.size;
    const candidateTrajectory = a.stackHistory.reduce((sum, stacks) =>
      sum + [...target].reduce((seatSum, seat) => seatSum + stacks[seat], 0), 0)
      / (a.stackHistory.length * target.size);
    const controlTrajectory = b.stackHistory.reduce((sum, stacks) =>
      sum + [...target].reduce((seatSum, seat) => seatSum + stacks[seat], 0), 0)
      / (b.stackHistory.length * target.size);
    const maxDecisionCount = Math.max(a.candidateDecisions.length, b.candidateDecisions.length);
    let changedDecisionSlots = 0;
    for (let index = 0; index < maxDecisionCount; index++) {
      const candidateAction = a.candidateDecisions[index] && {
        seat: a.candidateDecisions[index].seat, action: a.candidateDecisions[index].action,
        amount: a.candidateDecisions[index].amount
      };
      const controlAction = b.candidateDecisions[index] && {
        seat: b.candidateDecisions[index].seat, action: b.candidateDecisions[index].action,
        amount: b.candidateDecisions[index].amount
      };
      if (JSON.stringify(candidateAction) !== JSON.stringify(controlAction)) changedDecisionSlots++;
    }
    bucket.pairedTournaments++;
    bucket.candidateSeats += target.size;
    if (target.has(a.winner)) bucket.candidateWins++;
    if (target.has(b.winner)) bucket.controlWins++;
    if (a.winner === b.winner) bucket.sameWinner++; else bucket.changedWinner++;
    bucket.candidateHands += a.hands;
    bucket.controlHands += b.hands;
    const foldMetrics = (run, targetSeats) => run.handHistory.reduce((sum, hand) => {
      const aggressors = [...hand.candidateAggressiveSeats].filter(seat => targetSeats.has(seat));
      const winnerAggressed = aggressors.some(seat => hand.foldWinAwards[seat] > 0);
      sum.aggressiveHands += aggressors.length > 0 ? 1 : 0;
      sum.aggressiveActions += aggressors.reduce((count, seat) => count + hand.candidateAggressiveActionsBySeat[seat], 0);
      sum.foldWinsAfterAggression += winnerAggressed ? 1 : 0;
      sum.opponentFoldsAfterAggression += hand.opponentFoldsAfterCandidateAggression;
      return sum;
    }, { aggressiveHands: 0, aggressiveActions: 0, foldWinsAfterAggression: 0, opponentFoldsAfterAggression: 0 });
    const candidateFoldMetrics = foldMetrics(a, target), controlFoldMetrics = foldMetrics(b, target);
    bucket.candidateAggressiveHands += candidateFoldMetrics.aggressiveHands;
    bucket.candidateAggressiveActions += candidateFoldMetrics.aggressiveActions;
    bucket.candidateFoldWinsAfterAggression += candidateFoldMetrics.foldWinsAfterAggression;
    bucket.candidateOpponentFoldsAfterAggression += candidateFoldMetrics.opponentFoldsAfterAggression;
    bucket.controlAggressiveHands += controlFoldMetrics.aggressiveHands;
    bucket.controlAggressiveActions += controlFoldMetrics.aggressiveActions;
    bucket.controlFoldWinsAfterAggression += controlFoldMetrics.foldWinsAfterAggression;
    bucket.controlOpponentFoldsAfterAggression += controlFoldMetrics.opponentFoldsAfterAggression;
    bucket.candidateDecisionMismatches = (bucket.candidateDecisionMismatches ?? 0)
      + (a.candidateDecisions.length === b.candidateDecisions.length
        && a.candidateDecisions.every((decision, index) => decision.seat === b.candidateDecisions[index].seat
          && decision.action === b.candidateDecisions[index].action && decision.amount === b.candidateDecisions[index].amount) ? 0 : 1);
    bucket.changedDecisionSlots = (bucket.changedDecisionSlots ?? 0) + changedDecisionSlots;
    bucket.candidateAverageFinishChipsPerSeat += candidateFinish;
    bucket.controlAverageFinishChipsPerSeat += controlFinish;
    bucket.candidateMeanStackPerSeatAcrossHands += candidateTrajectory;
    bucket.controlMeanStackPerSeatAcrossHands += controlTrajectory;
    for (const [action, count] of Object.entries(a.actions))
      bucket.candidateActions[action] = (bucket.candidateActions[action] ?? 0) + count;
    for (const [action, count] of Object.entries(b.actions))
      bucket.controlActions[action] = (bucket.controlActions[action] ?? 0) + count;
    for (const [version, count] of Object.entries(a.versions))
      bucket.candidatePolicyVersions[version] = (bucket.candidatePolicyVersions[version] ?? 0) + count;
    for (const [version, count] of Object.entries(b.versions))
      bucket.controlPolicyVersions[version] = (bucket.controlPolicyVersions[version] ?? 0) + count;

    for (const seat of target) {
      const position = ((seat - a.firstButton) % a.seats + a.seats) % a.seats;
      const byPosition = resultByPosition[a.seats] ??= {};
      const positionBucket = byPosition[position] ??= { initialPositionFromButton: position,
        pairedSeatRuns: 0, candidateWins: 0, controlWins: 0, sameTournamentWinner: 0,
        changedTournamentWinner: 0, candidateFinishChips: 0, controlFinishChips: 0,
        candidateMeanStackAcrossHands: 0, controlMeanStackAcrossHands: 0,
        candidateAggressiveHands: 0, candidateAggressiveActions: 0, candidateFoldWinsAfterAggression: 0,
        candidateOpponentFoldsAfterAggression: 0, controlAggressiveHands: 0, controlAggressiveActions: 0,
        controlFoldWinsAfterAggression: 0, controlOpponentFoldsAfterAggression: 0 };
      positionBucket.pairedSeatRuns++;
      if (a.winner === seat) positionBucket.candidateWins++;
      if (b.winner === seat) positionBucket.controlWins++;
      if (a.winner === b.winner) positionBucket.sameTournamentWinner++;
      else positionBucket.changedTournamentWinner++;
      positionBucket.candidateFinishChips += a.finalStacks[seat];
      positionBucket.controlFinishChips += b.finalStacks[seat];
      positionBucket.candidateMeanStackAcrossHands += a.stackHistory.reduce((sum, stacks) => sum + stacks[seat], 0)
        / a.stackHistory.length;
      positionBucket.controlMeanStackAcrossHands += b.stackHistory.reduce((sum, stacks) => sum + stacks[seat], 0)
        / b.stackHistory.length;
      for (const [run, side] of [[a, 'candidate'], [b, 'control']]) {
        const handMetrics = run.handHistory.reduce((sum, hand) => {
          const aggressive = hand.candidateAggressiveSeats.has(seat);
          sum.aggressiveHands += aggressive ? 1 : 0;
          sum.aggressiveActions += hand.candidateAggressiveActionsBySeat[seat] ?? 0;
          sum.foldWins += aggressive && hand.foldWinAwards[seat] > 0 ? 1 : 0;
          sum.opponentFolds += hand.opponentFoldsAfterCandidateAggressionBySeat[seat] ?? 0;
          return sum;
        }, { aggressiveHands: 0, aggressiveActions: 0, foldWins: 0, opponentFolds: 0 });
        positionBucket[`${side}AggressiveHands`] += handMetrics.aggressiveHands;
        positionBucket[`${side}AggressiveActions`] += handMetrics.aggressiveActions;
        positionBucket[`${side}FoldWinsAfterAggression`] += handMetrics.foldWins;
        positionBucket[`${side}OpponentFoldsAfterAggression`] += handMetrics.opponentFolds;
      }
    }
  }
  for (const bucket of Object.values(result)) {
    assert.notDeepEqual(bucket.candidatePolicyVersions, bucket.controlPolicyVersions,
      'Candidate and control executed identical policy versions; reject this paired run');
    const n = bucket.pairedTournaments;
    bucket.candidateAverageFinishChipsPerSeat /= n;
    bucket.controlAverageFinishChipsPerSeat /= n;
    bucket.candidateMeanStackPerSeatAcrossHands /= n;
    bucket.controlMeanStackPerSeatAcrossHands /= n;
    bucket.deltaMeanStackPerSeatAcrossHands = bucket.candidateMeanStackPerSeatAcrossHands
      - bucket.controlMeanStackPerSeatAcrossHands;
    bucket.candidateFoldWinAfterAggressionRate = bucket.candidateAggressiveHands
      ? bucket.candidateFoldWinsAfterAggression / bucket.candidateAggressiveHands : 0;
    bucket.controlFoldWinAfterAggressionRate = bucket.controlAggressiveHands
      ? bucket.controlFoldWinsAfterAggression / bucket.controlAggressiveHands : 0;
  }
  for (const positions of Object.values(resultByPosition)) {
    for (const bucket of Object.values(positions)) {
      const n = bucket.pairedSeatRuns;
      bucket.candidateFinishChips /= n;
      bucket.controlFinishChips /= n;
      bucket.meanFinishChipDelta = bucket.candidateFinishChips - bucket.controlFinishChips;
      bucket.candidateMeanStackAcrossHands /= n;
      bucket.controlMeanStackAcrossHands /= n;
      bucket.meanTrajectoryChipDelta = bucket.candidateMeanStackAcrossHands - bucket.controlMeanStackAcrossHands;
      bucket.candidateFoldWinAfterAggressionRate = bucket.candidateAggressiveHands
        ? bucket.candidateFoldWinsAfterAggression / bucket.candidateAggressiveHands : 0;
      bucket.controlFoldWinAfterAggressionRate = bucket.controlAggressiveHands
        ? bucket.controlFoldWinsAfterAggression / bucket.controlAggressiveHands : 0;
    }
  }
  return { comparison: 'same-seed paired candidate policy variants', resultsBySeatCount: result,
    resultsByCandidateInitialPosition: resultByPosition,
    caveat: 'Small deterministic tournament cohorts are noisy. Mean stack over observed hands is a descriptive trajectory, not EV; win share alone is not a strategy-strength claim.' };
}

try {
  const [candidatePath, controlPath] = process.argv.slice(2);
  if (!candidatePath || !controlPath || process.argv.length !== 4)
    throw new Error('Usage: node scripts/compare-tournament-cohorts.cjs candidate.trace control.trace');
  console.log(JSON.stringify(compare(candidatePath, controlPath), null, 2));
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
