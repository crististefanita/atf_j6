const fs = require('node:fs');
const assert = require('node:assert/strict');

function read(file) {
  const result = new Map();
  let tournament, hand;
  for (const [index, line] of fs.readFileSync(file, 'utf8').split(/\r?\n/).entries()) {
    let match = /^TOURNAMENT seed=(-?\d+) seats=(\d+) .*buttonOffset=(-?\d+) firstButton=(\d+) .*startingStack=(\d+)/.exec(line);
    if (match) {
      assert.equal(tournament, undefined, `${file}:${index + 1} nested tournament`);
      tournament = { seed: +match[1], seats: +match[2], buttonOffset: +match[3], firstButton: +match[4],
        startingStack: +match[5], hands: [] };
      continue;
    }
    match = /^HAND (\d+) .* dealSeed=(-?\d+)$/.exec(line);
    if (match && tournament) {
      hand = { number: +match[1], dealSeed: match[2], decisions: [], foldAwards: [] };
      tournament.hands.push(hand);
      continue;
    }
    match = /^DECISION seat=(\d+) policy=(\S+) action=([A-Z_]+) amount=(\d+) reason=.* positionFromButton=(\d+) street=(PREFLOP|FLOP|TURN|RIVER) pot=(\d+) toCall=(\d+) heroStack=(\d+)$/.exec(line);
    if (line.startsWith('DECISION ')) assert.ok(match, `${file}:${index + 1} missing public sizing context`);
    if (match && hand) {
      hand.decisions.push({ seat: +match[1], policy: match[2], action: match[3], amount: +match[4],
        positionFromButton: +match[5], street: match[6], pot: +match[7], toCall: +match[8], heroStack: +match[9] });
      continue;
    }
    match = /^FOLD_WIN \[([^\]]+)\]$/.exec(line);
    if (match && hand) {
      hand.foldAwards = match[1].split(',').map(value => Number(value.trim()));
      continue;
    }
    match = /^COMPLETE winner=(\d+) hands=(\d+)$/.exec(line);
    if (match) {
      assert.ok(tournament, `${file}:${index + 1} completion without header`);
      assert.equal(tournament.hands.length, +match[2], `${file}:${index + 1} hand count mismatch`);
      tournament.winner = +match[1];
      const key = `${tournament.seats}:${tournament.seed}`;
      assert.ok(!result.has(key), `${file}:${index + 1} duplicate ${key}`);
      result.set(key, tournament);
      tournament = undefined;
      hand = undefined;
    }
  }
  assert.equal(tournament, undefined, `${file} ended mid-tournament`);
  assert.ok(result.size, `${file} has no completed tournaments`);
  return result;
}

function init() {
  return { seatRuns: 0, candidateAggressiveHands: 0, candidateAggressiveActions: 0,
    candidatePressureMultipleTotal: 0, candidatePressureSamples: 0, candidateOverPotActions: 0,
    candidateFoldWinsAfterAggression: 0, candidateOpponentFoldsAfterAggression: 0,
    controlAggressiveHands: 0, controlAggressiveActions: 0,
    controlPressureMultipleTotal: 0, controlPressureSamples: 0, controlOverPotActions: 0,
    controlFoldWinsAfterAggression: 0, controlOpponentFoldsAfterAggression: 0 };
}

function pressureMultiple(decision) {
  if (decision.action === 'BET') return decision.amount / Math.max(1, decision.pot);
  if (decision.action === 'RAISE') return (decision.amount - decision.toCall)
    / Math.max(1, decision.pot + decision.toCall);
  return undefined;
}

function compare(candidateFile, controlFile) {
  const candidate = read(candidateFile), control = read(controlFile);
  assert.deepEqual([...candidate.keys()].sort(), [...control.keys()].sort(), 'Candidate/control must use identical seeds and table sizes');
  const groups = new Map();
  for (const [key, run] of candidate) {
    const baseline = control.get(key);
    assert.equal(run.buttonOffset, baseline.buttonOffset, `${key} button offset differs`);
    assert.equal(run.firstButton, baseline.firstButton, `${key} first button differs`);
    const common = Math.min(run.hands.length, baseline.hands.length);
    for (let index = 0; index < common; index++)
      assert.equal(run.hands[index].dealSeed, baseline.hands[index].dealSeed, `${key} hand ${index + 1} deal seed differs`);
    const targetSeats = Array.from({ length: run.seats }, (_, seat) => seat)
      .filter(seat => (seat + ((run.seed % 4) + 4) % 4) % 4 === 3);
    for (const seat of targetSeats) {
      const position = (seat - run.firstButton + run.seats) % run.seats;
      const groupKey = `${run.seats}:${position}`;
      const aggregate = groups.get(groupKey) ?? { seats: run.seats, initialPositionFromButton: position, ...init() };
      aggregate.seatRuns++;
      for (const [source, side] of [[run, 'candidate'], [baseline, 'control']]) {
        const prefix = side === 'candidate' ? 'candidate' : 'control';
        const aggressiveHands = new Set();
        let foldsAfterAggression = 0;
        for (const currentHand of source.hands) {
          let lastAggressor = null;
          for (const decision of currentHand.decisions) {
            if (decision.seat === seat && (decision.action === 'BET' || decision.action === 'RAISE')) {
              aggressiveHands.add(currentHand.number);
              aggregate[`${prefix}AggressiveActions`]++;
              const multiple = pressureMultiple(decision);
              if (multiple !== undefined) {
                aggregate[`${prefix}PressureMultipleTotal`] += multiple;
                aggregate[`${prefix}PressureSamples`]++;
                if (multiple > 1) aggregate[`${prefix}OverPotActions`]++;
              }
              lastAggressor = seat;
            } else if (decision.action === 'FOLD' && lastAggressor === seat) {
              foldsAfterAggression++;
            }
            if (decision.seat === seat && decision.action === 'FOLD') lastAggressor = null;
          }
          if (aggressiveHands.has(currentHand.number) && currentHand.foldAwards.includes(seat))
            aggregate[`${prefix}FoldWinsAfterAggression`]++;
        }
        aggregate[`${prefix}AggressiveHands`] += aggressiveHands.size;
        aggregate[`${prefix}OpponentFoldsAfterAggression`] += foldsAfterAggression;
      }
      groups.set(groupKey, aggregate);
    }
  }
  const rows = [...groups.values()].map(row => {
    for (const side of ['candidate', 'control']) {
      row[`${side}MeanPressurePotMultiple`] = row[`${side}PressureSamples`]
        ? row[`${side}PressureMultipleTotal`] / row[`${side}PressureSamples`] : null;
      row[`${side}OverPotActionRate`] = row[`${side}PressureSamples`]
        ? row[`${side}OverPotActions`] / row[`${side}PressureSamples`] : null;
      row[`${side}FoldWinAfterAggressionRate`] = row[`${side}AggressiveHands`]
        ? row[`${side}FoldWinsAfterAggression`] / row[`${side}AggressiveHands`] : null;
    }
    for (const field of ['candidatePressureMultipleTotal', 'controlPressureMultipleTotal']) delete row[field];
    return row;
  }).sort((a, b) => a.seats - b.seats || a.initialPositionFromButton - b.initialPositionFromButton);
  return { comparison: 'same-seed pressure sizing and fold-response description', rows,
    caveat: 'Pot multiples use only the public pot, call price, stack, street and position recorded before each decision. Same-hand opponent folds and fold-win awards are descriptive temporal associations, not proof that the candidate bet caused those folds or had positive expected value.' };
}

try {
  const [candidate, control] = process.argv.slice(2);
  if (!candidate || !control || process.argv.length !== 4)
    throw new Error('Usage: node scripts/summarize-pressure-sizing.cjs candidate.trace control.trace');
  console.log(JSON.stringify(compare(candidate, control), null, 2));
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
